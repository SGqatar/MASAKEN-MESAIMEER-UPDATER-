package com.example.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Context.rtdbDataStore by preferencesDataStore(name = "masaken_rtdb_mirror")

sealed interface RtdbSyncState {
    data object Unconfigured : RtdbSyncState
    data object Syncing : RtdbSyncState
    data class Synced(val lastSyncedLabel: String, val countsLabel: String) : RtdbSyncState
    data class Error(val message: String) : RtdbSyncState
}

/**
 * Mirrors Masaken Mesaimeer's three operational datasets (`sportsBookings`, `keyRegister`, `pmVisitors`)
 * to a user-configured Firebase Realtime Database URL via authenticated REST PUT alongside primary Cloud Firestore.
 */
class RealtimeDbMirrorService(
    private val context: Context,
    private val auth: FirebaseAuth
) {
    companion object {
        private val KEY_RTDB_URL = stringPreferencesKey("rtdb_database_url")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val rtdbUrlFlow: Flow<String> = context.rtdbDataStore.data.map { prefs ->
        prefs[KEY_RTDB_URL].orEmpty()
    }

    private val _syncState = MutableStateFlow<RtdbSyncState>(RtdbSyncState.Unconfigured)
    val syncState: StateFlow<RtdbSyncState> = _syncState.asStateFlow()

    suspend fun saveRtdbUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        context.rtdbDataStore.edit { prefs ->
            prefs[KEY_RTDB_URL] = clean
        }
        if (clean.isBlank()) {
            _syncState.value = RtdbSyncState.Unconfigured
        }
    }

    suspend fun pushAllDatasets(
        keys: List<KeyRegisterEntity>,
        visitors: List<VisitorDayEntity>,
        sports: List<SportsBookingEntity>
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = rtdbUrlFlow.first().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            _syncState.value = RtdbSyncState.Unconfigured
            return@withContext Result.failure(
                IllegalStateException("Realtime Database URL not configured yet. Tap RTDB Settings on Home to link your .firebaseio.com URL.")
            )
        }
        if (!baseUrl.startsWith("https://")) {
            val err = "Realtime Database URL must start with https://"
            _syncState.value = RtdbSyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _syncState.value = RtdbSyncState.Syncing
        try {
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            val nowMillis = System.currentTimeMillis()
            val timeLabel = SimpleDateFormat("dd-MMM HH:mm", Locale.US).format(Date(nowMillis))

            val payload = buildMirrorPayload(
                nowMillis = nowMillis,
                timeLabel = timeLabel,
                keys = keys,
                visitors = visitors,
                sports = sports
            )

            val endpoint = buildString {
                append(baseUrl).append("/masakenMesaimeerOps.json")
                if (!idToken.isNullOrBlank()) {
                    append("?auth=").append(idToken)
                }
            }

            val req = Request.Builder()
                .url(endpoint)
                .put(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = "RTDB HTTP ${resp.code}: ${body.take(180)}"
                    _syncState.value = RtdbSyncState.Error(msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            }

            val summary = "${sports.size} Sports • ${keys.size} Keys • ${visitors.size} Visitor Days"
            _syncState.value = RtdbSyncState.Synced(lastSyncedLabel = timeLabel, countsLabel = summary)
            Result.success("Synced to Realtime DB ($summary)")
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Realtime DB sync failed (offline)."
            _syncState.value = RtdbSyncState.Error(msg)
            Result.failure(e)
        }
    }

    private fun buildMirrorPayload(
        nowMillis: Long,
        timeLabel: String,
        keys: List<KeyRegisterEntity>,
        visitors: List<VisitorDayEntity>,
        sports: List<SportsBookingEntity>
    ): JSONObject {
        val football = sports.filter { it.sport.equals("FOOTBALL", ignoreCase = true) }
        val padel = sports.filter { it.sport.equals("PADEL", ignoreCase = true) }

        val sportsItems = JSONObject().apply {
            sports.sortedBy { it.sn }.forEach { b ->
                put(
                    b.sn.toString(),
                    JSONObject().apply {
                        put("sn", b.sn)
                        put("sport", b.sport)
                        put("date", b.date)
                        put("time", b.time)
                        put("name", b.name)
                        put("unitDetails", b.unitDetails)
                        put("discount", b.discount)
                        put("referenceNo", b.referenceNo)
                        put("durationPlayed", b.durationPlayed)
                        put("remarks", b.remarks)
                    }
                )
            }
        }

        val keyItems = JSONObject().apply {
            keys.sortedBy { it.si }.forEach { k ->
                put(
                    k.si.toString(),
                    JSONObject().apply {
                        put("si", k.si)
                        put("dateOut", k.dateOut)
                        put("timeOut", k.timeOut)
                        put("flatUnitLocation", k.flatUnitLocation)
                        put("keyCardType", k.keyCardType)
                        put("purpose", k.purpose)
                        put("issuedBy", k.issuedBy)
                        put("issuedTo", k.issuedTo)
                        put("qidOrNote", k.qidOrNote)
                        put("dateIn", k.dateIn)
                        put("timeIn", k.timeIn)
                        put("returnedBy", k.returnedBy)
                        put("remarks", k.remarks)
                        put("status", k.status)
                        put("isUnreturnedAlert", k.isUnreturnedAlert)
                        put("isCurrentMonthUnreturnedAlert", k.isCurrentMonthUnreturnedAlert)
                        put("isMoveInPendingTCode", k.isMoveInPendingTCode)
                        put("updatedAtMillis", k.updatedAtMillis)
                    }
                )
            }
        }

        val visitorItems = JSONObject().apply {
            visitors.sortedBy { it.dateIso }.forEach { v ->
                put(
                    v.dateIso,
                    JSONObject().apply {
                        put("dateIso", v.dateIso)
                        put("displayDate", v.displayDate)
                        put("monthKey", v.monthKey)
                        put("visitorCount", v.visitorCount)
                        put("viewingCount", v.viewingCount)
                        put("flatDetails", v.flatDetails)
                        put("notes", v.notes)
                    }
                )
            }
        }

        return JSONObject().apply {
            put("syncedAtMillis", nowMillis)
            put("syncedAtDisplay", timeLabel)
            put(
                "sportsBookings",
                JSONObject().apply {
                    put(
                        "summary",
                        JSONObject().apply {
                            put("totalBookings", sports.size)
                            put("footballCount", football.size)
                            put("footballHours", football.sumOf { it.durationPlayed })
                            put("padelCount", padel.size)
                            put("padelHours", padel.sumOf { it.durationPlayed })
                            put("totalHours", sports.sumOf { it.durationPlayed })
                            put("updatedThrough", "26/11/2026")
                        }
                    )
                    put("items", sportsItems)
                }
            )
            put(
                "keyRegister",
                JSONObject().apply {
                    put(
                        "summary",
                        JSONObject().apply {
                            put("totalRows", keys.size)
                            put("currentMonthRedAlertCount", keys.count { it.isCurrentMonthUnreturnedAlert })
                            put("backlogRedAlertCount", keys.count { it.isUnreturnedAlert && !it.isCurrentMonthUnreturnedAlert })
                            put("redAlertCount", keys.count { it.isUnreturnedAlert })
                            put("moveInPendingTCodeCount", keys.count { it.isMoveInPendingTCode })
                            put("updatedThrough", "26/11/2026")
                        }
                    )
                    put("items", keyItems)
                }
            )
            put(
                "pmVisitors",
                JSONObject().apply {
                    put(
                        "summary",
                        JSONObject().apply {
                            put("daysLogged", visitors.size)
                            put("totalVisitors", visitors.sumOf { it.visitorCount })
                            put("totalViewings", visitors.sumOf { it.viewingCount })
                            put("updatedThrough", "26-Nov-2026")
                        }
                    )
                    put("items", visitorItems)
                }
            )
        }
    }
}
