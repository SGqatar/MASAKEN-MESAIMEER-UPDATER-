package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.data.sync.ImportedWorkbookBundle
import com.example.data.sync.IngestTarget
import com.example.data.sync.TestRecordFilter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "PhotoIngestService"

class UniversalPhotoIngestService(private val context: Context) {

    private val keySchema = Schema.obj(
        properties = mapOf(
            "rows" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "si" to Schema.integer(description = "Serial SI number if visible, or 0 if not shown"),
                        "dateOut" to Schema.string(description = "Date out in dd/MM/yyyy"),
                        "timeOut" to Schema.string(description = "Time out in HH:mm"),
                        "flatUnitLocation" to Schema.string(description = "Flat or unit code e.g. M-A04-16"),
                        "keyCardType" to Schema.string(description = "Key or access card type"),
                        "purpose" to Schema.string(description = "Purpose of key issuance"),
                        "issuedTo" to Schema.string(description = "Person or vendor issued to"),
                        "qidOrNote" to Schema.string(description = "QID number or note"),
                        "dateIn" to Schema.string(description = "Return date if returned, else empty"),
                        "timeIn" to Schema.string(description = "Return time if returned, else empty"),
                        "returnedBy" to Schema.string(description = "Returned by name if returned, else empty"),
                        "remarks" to Schema.string(description = "Remarks"),
                        "status" to Schema.enumeration(
                            values = listOf(KeyStatus.RETURNED, KeyStatus.ISSUED, KeyStatus.NOT_YET_RETURNED),
                            description = "Key status"
                        )
                    ),
                    optionalProperties = listOf("dateIn", "timeIn", "returnedBy", "remarks", "qidOrNote")
                )
            )
        )
    )

    private val visitorSchema = Schema.obj(
        properties = mapOf(
            "rows" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "displayDate" to Schema.string(description = "Date formatted as dd-MMM-yyyy e.g. 26-Nov-2026"),
                        "visitorCount" to Schema.integer(description = "Number of PM visitors"),
                        "viewingCount" to Schema.integer(description = "Number of apartment viewings"),
                        "flatDetails" to Schema.string(description = "Comma-separated unit codes viewed e.g. M-C13-16"),
                        "notes" to Schema.string(description = "Notes")
                    ),
                    optionalProperties = listOf("flatDetails", "notes")
                )
            )
        )
    )

    private val maintenanceSchema = Schema.obj(
        properties = mapOf(
            "rows" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string(description = "Date dd/MM/yyyy"),
                        "names" to Schema.string(description = "Resident or requester name"),
                        "phoneNumber" to Schema.string(description = "Phone number"),
                        "workOrderNumber" to Schema.string(description = "Work order number"),
                        "qidNumber" to Schema.string(description = "QID number"),
                        "flatDetails" to Schema.string(description = "Flat code e.g. M-B07-12"),
                        "requestDetails" to Schema.string(description = "Maintenance request description")
                    ),
                    optionalProperties = listOf("phoneNumber", "workOrderNumber", "qidNumber")
                )
            )
        )
    )

    private val sportsSchema = Schema.obj(
        properties = mapOf(
            "rows" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "sport" to Schema.enumeration(values = listOf("FOOTBALL", "PADEL"), description = "Sport"),
                        "date" to Schema.string(description = "Date dd/MM/yyyy"),
                        "time" to Schema.string(description = "Time range"),
                        "name" to Schema.string(description = "Player or resident name"),
                        "unitDetails" to Schema.string(description = "Unit code e.g. M-A04-16"),
                        "discount" to Schema.string(description = "FREE or discount label"),
                        "referenceNo" to Schema.string(description = "Reference or T-code"),
                        "durationPlayed" to Schema.double(description = "Hours played"),
                        "remarks" to Schema.string(description = "Remarks")
                    ),
                    optionalProperties = listOf("discount", "referenceNo", "remarks")
                )
            )
        )
    )

    private val keyModel by lazy {
        Firebase.ai.generativeModel(
            "gemini-3.8-flash",
            generationConfig { responseMimeType = "application/json"; responseSchema = keySchema }
        )
    }

    private val visitorModel by lazy {
        Firebase.ai.generativeModel(
            "gemini-3.8-flash",
            generationConfig { responseMimeType = "application/json"; responseSchema = visitorSchema }
        )
    }

    private val maintenanceModel by lazy {
        Firebase.ai.generativeModel(
            "gemini-3.8-flash",
            generationConfig { responseMimeType = "application/json"; responseSchema = maintenanceSchema }
        )
    }

    private val sportsModel by lazy {
        Firebase.ai.generativeModel(
            "gemini-3.8-flash",
            generationConfig { responseMimeType = "application/json"; responseSchema = sportsSchema }
        )
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun extractRowsFromPhoto(
        imagePath: String,
        target: IngestTarget,
        nextKeySi: Int,
        nextMaintenanceSn: Int,
        nextSportsSn: Int,
        hint: String
    ): Result<ImportedWorkbookBundle> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(
                IllegalStateException("Device is offline. Connect to the internet to parse photos with Gemini Vision, or use the offline note draft.")
            )
        }
        val effectiveTarget = if (target == IngestTarget.AUTO) IngestTarget.KEYS else target
        val file = File(imagePath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalStateException("Selected image file was not found."))
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return@withContext Result.failure(IllegalStateException("Could not decode attached photo."))

        val scaled = scaleDownIfNeeded(bitmap, 1280)
        try {
            val (model, promptText) = when (effectiveTarget) {
                IngestTarget.KEYS -> keyModel to buildPrompt("Key & Access Card Register (Form WSF-PMD-F-07)", hint)
                IngestTarget.VISITORS -> visitorModel to buildPrompt("PM Visitors Log (2026)", hint)
                IngestTarget.MAINTENANCE -> maintenanceModel to buildPrompt("Maintenance Request Log", hint)
                IngestTarget.SPORTS, IngestTarget.AUTO -> sportsModel to buildPrompt("Padel & Football November 2026 Log", hint)
            }

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaled)
                        text(promptText)
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("Gemini Vision returned empty text.")
            val root = JSONObject(jsonText)
            val rows = root.optJSONArray("rows") ?: return@withContext Result.success(ImportedWorkbookBundle())

            val bundle = when (effectiveTarget) {
                IngestTarget.KEYS -> {
                    var runningSi = nextKeySi
                    val rawKeys = (0 until rows.length()).map { i ->
                        val o = rows.getJSONObject(i)
                        val parsedSi = o.optInt("si", 0)
                        val finalSi = if (parsedSi > 0) parsedSi else runningSi++
                        KeyRegisterEntity(
                            si = finalSi,
                            dateOut = o.optString("dateOut", "26/11/2026").ifBlank { "26/11/2026" },
                            timeOut = o.optString("timeOut", "16:36").ifBlank { "16:36" },
                            flatUnitLocation = o.optString("flatUnitLocation"),
                            keyCardType = o.optString("keyCardType", "Apartment Key Set"),
                            purpose = o.optString("purpose"),
                            issuedTo = o.optString("issuedTo"),
                            qidOrNote = o.optString("qidOrNote"),
                            dateIn = o.optString("dateIn"),
                            timeIn = o.optString("timeIn"),
                            returnedBy = o.optString("returnedBy"),
                            remarks = o.optString("remarks"),
                            status = o.optString("status", KeyStatus.ISSUED)
                        )
                    }
                    val filtered = TestRecordFilter.filterKeyRecords(rawKeys)
                    ImportedWorkbookBundle(
                        keys = filtered,
                        skippedTestRecords = rawKeys.size - filtered.size
                    )
                }
                IngestTarget.VISITORS -> {
                    val visitors = (0 until rows.length()).map { i ->
                        val o = rows.getJSONObject(i)
                        val disp = o.optString("displayDate", "26-Nov-2026").ifBlank { "26-Nov-2026" }
                        val (iso, month) = parseDisplayDateToIso(disp)
                        VisitorDayEntity(
                            dateIso = iso,
                            displayDate = disp,
                            monthKey = month,
                            visitorCount = o.optInt("visitorCount", 0),
                            viewingCount = o.optInt("viewingCount", 0),
                            flatDetails = o.optString("flatDetails", "-").ifBlank { "-" },
                            notes = o.optString("notes")
                        )
                    }
                    ImportedWorkbookBundle(visitors = visitors)
                }
                IngestTarget.MAINTENANCE -> {
                    val items = (0 until rows.length()).mapIndexed { idx, _ ->
                        val o = rows.getJSONObject(idx)
                        MaintenanceRequestEntity(
                            sn = nextMaintenanceSn + idx,
                            date = o.optString("date", "26/11/2026").ifBlank { "26/11/2026" },
                            names = o.optString("names"),
                            phoneNumber = o.optString("phoneNumber"),
                            workOrderNumber = o.optString("workOrderNumber"),
                            qidNumber = o.optString("qidNumber"),
                            flatDetails = o.optString("flatDetails"),
                            requestDetails = o.optString("requestDetails")
                        )
                    }
                    ImportedWorkbookBundle(maintenance = items)
                }
                IngestTarget.SPORTS, IngestTarget.AUTO -> {
                    val bookings = (0 until rows.length()).mapIndexed { idx, _ ->
                        val o = rows.getJSONObject(idx)
                        SportsBookingEntity(
                            sn = nextSportsSn + idx,
                            sport = o.optString("sport", "PADEL").uppercase(Locale.ROOT),
                            date = o.optString("date", "26/11/2026").ifBlank { "26/11/2026" },
                            time = o.optString("time", "18:00 - 19:00"),
                            name = o.optString("name"),
                            unitDetails = o.optString("unitDetails"),
                            discount = o.optString("discount", "FREE").ifBlank { "FREE" },
                            referenceNo = o.optString("referenceNo"),
                            durationPlayed = o.optDouble("durationPlayed", 1.0).let { if (it.isNaN() || it <= 0) 1.0 else it },
                            remarks = o.optString("remarks"),
                            attachmentPath = imagePath
                        )
                    }
                    ImportedWorkbookBundle(sports = bookings)
                }
            }
            Result.success(bundle)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo extraction timed out", e)
            Result.failure(IllegalStateException("Gemini Vision timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo extraction failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to extract rows from photo."))
        } finally {
            if (scaled !== bitmap) bitmap.recycle()
            scaled.recycle()
        }
    }

    private fun buildPrompt(sheetName: String, hint: String): String = buildString {
        appendLine("Extract all structured rows visible in this photo for the Masaken Mesaimeer (Doha, Qatar) $sheetName.")
        appendLine("Use unit codes like M-A04-16 where applicable and preserve exact serial/reference numbers.")
        if (hint.isNotBlank()) {
            appendLine("Operator note: $hint")
        }
    }

    private fun parseDisplayDateToIso(display: String): Pair<String, String> {
        val formats = listOf("dd-MMM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd")
        for (fmt in formats) {
            try {
                val d = SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }.parse(display.trim()) ?: continue
                val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                return iso to iso.take(7)
            } catch (_: Exception) {
            }
        }
        return "2026-11-26" to "2026-11"
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }
}
