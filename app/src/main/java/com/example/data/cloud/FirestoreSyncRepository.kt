package com.example.data.cloud

import android.util.Log
import androidx.annotation.Keep
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.MasakenDatabase
import com.example.data.db.OpsReminderEntity
import com.example.data.db.ReminderType
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.data.sync.TestRecordFilter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Collections

sealed interface CloudSyncState {
    data object SignedOut : CloudSyncState
    data object Syncing : CloudSyncState
    data class Synced(val uid: String, val lastSyncedAtMillis: Long) : CloudSyncState
    data class Error(val message: String) : CloudSyncState
}

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "operator"
)

class FirestoreSyncRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val db: MasakenDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = Collections.synchronizedList(mutableListOf<ListenerRegistration>())

    private val knownKeyIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val knownVisitorIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val knownMaintenanceIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val knownSportsIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val knownReminderIds = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var activeUid: String? = null

    private val _syncState = MutableStateFlow<CloudSyncState>(CloudSyncState.SignedOut)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Authentication required before accessing Firestore")
    }

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun keysCol(uid: String) = userDoc(uid).collection("keys")
    private fun visitorsCol(uid: String) = userDoc(uid).collection("visitorDays")
    private fun maintenanceCol(uid: String) = userDoc(uid).collection("maintenanceRequests")
    private fun sportsCol(uid: String) = userDoc(uid).collection("sportsBookings")
    private fun remindersCol(uid: String) = userDoc(uid).collection("reminders")

    suspend fun startSessionSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            if (activeUid == uid && listeners.isNotEmpty()) {
                return@withContext Result.success(Unit)
            }
            detachListenersInternal()
            activeUid = uid
            _syncState.value = CloudSyncState.Syncing

            // Ensure local database has baseline backlog seeded first
            MasakenDatabase.seedIfEmpty(db)

            // Sync operator profile doc (/users/{uid})
            upsertUserProfile(uid)

            // Bootstrap empty user workspace from Room baseline, or mirror cloud records down
            bootstrapCollectionIfEmpty(uid)

            // Attach live owner-scoped snapshot listeners
            attachRealtimeListeners(uid)

            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}", auth)
            _syncState.value = CloudSyncState.Error(e.localizedMessage ?: "Cloud sync unavailable")
            Result.failure(e)
        }
    }

    fun detachListeners() {
        detachListenersInternal()
        activeUid = null
        _syncState.value = CloudSyncState.SignedOut
    }

    private fun detachListenersInternal() {
        synchronized(listeners) {
            listeners.forEach { reg ->
                try {
                    reg.remove()
                } catch (_: Exception) {
                }
            }
            listeners.clear()
        }
        knownKeyIds.clear()
        knownVisitorIds.clear()
        knownMaintenanceIds.clear()
        knownSportsIds.clear()
        knownReminderIds.clear()
    }

    private suspend fun upsertUserProfile(uid: String) {
        val path = "users/$uid"
        val user = auth.currentUser
        val email = user?.email.orEmpty().take(254)
        val displayName = (user?.displayName ?: email.substringBefore('@')).ifBlank { "Operator" }.take(120)
        val ref = userDoc(uid)
        val existing = ref.get().await()
        if (!existing.exists()) {
            ref.set(
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "role" to "operator",
                    "lastSignInAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            ref.update(
                mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "lastSignInAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Log.d("FirestoreSync", "Synced profile at $path")
    }

    private suspend fun bootstrapCollectionIfEmpty(uid: String) {
        // Keys
        if (keysCol(uid).limit(1).get().await().isEmpty) {
            val allKeys = db.keyRegisterDao().getAllOnce()
                .filterNot { TestRecordFilter.isDeletedTestKeyRecord(it) }
                .filter { it.si >= 1 }
            batchCreate(allKeys) { key ->
                val docId = key.si.toString()
                knownKeyIds.add(docId)
                keysCol(uid).document(docId) to keyToCreateMap(key)
            }
        }

        // Visitor Days
        if (visitorsCol(uid).limit(1).get().await().isEmpty) {
            val allVisitors = db.visitorDayDao().getAllOnce().filter { it.dateIso.isNotBlank() }
            batchCreate(allVisitors) { day ->
                knownVisitorIds.add(day.dateIso)
                visitorsCol(uid).document(day.dateIso) to visitorToCreateMap(day)
            }
        }

        // Maintenance Requests
        if (maintenanceCol(uid).limit(1).get().await().isEmpty) {
            val allMaint = db.maintenanceRequestDao().getAllOnce().filter { it.sn >= 1 }
            batchCreate(allMaint) { req ->
                val docId = req.sn.toString()
                knownMaintenanceIds.add(docId)
                maintenanceCol(uid).document(docId) to maintenanceToCreateMap(req)
            }
        }

        // Sports Bookings
        if (sportsCol(uid).limit(1).get().await().isEmpty) {
            val allSports = db.sportsBookingDao().getAllAscendingOnce().filter { it.sn >= 1 }
            batchCreate(allSports) { booking ->
                val docId = booking.sn.toString()
                knownSportsIds.add(docId)
                sportsCol(uid).document(docId) to sportsToCreateMap(booking)
            }
        }

        // Reminders
        if (remindersCol(uid).limit(1).get().await().isEmpty) {
            val allReminders = db.opsReminderDao().getAllOnce().filter { it.id.isNotBlank() }
            batchCreate(allReminders) { rem ->
                knownReminderIds.add(rem.id)
                remindersCol(uid).document(rem.id) to reminderToCreateMap(rem)
            }
        }
    }

    private suspend fun <T> batchCreate(
        items: List<T>,
        mapper: (T) -> Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>
    ) {
        if (items.isEmpty()) return
        for (chunk in items.chunked(400)) {
            val batch = firestore.batch()
            chunk.forEach { item ->
                val (ref, data) = mapper(item)
                batch.set(ref, data)
            }
            batch.commit().await()
        }
    }

    private fun attachRealtimeListeners(uid: String) {
        val keysReg = keysCol(uid)
            .orderBy("si", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    logFirestoreError(err, OperationType.LIST, "users/$uid/keys", auth)
                    _syncState.value = CloudSyncState.Error(err.localizedMessage ?: "Key sync error")
                    return@addSnapshotListener
                }
                if (snap == null || snap.isEmpty) return@addSnapshotListener
                val entities = snap.documents.mapNotNull { doc ->
                    knownKeyIds.add(doc.id)
                    docToKeyEntity(doc)
                }
                if (entities.isNotEmpty()) {
                    scope.launch {
                        db.keyRegisterDao().upsertAll(entities)
                        _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
                    }
                }
            }

        val visitorsReg = visitorsCol(uid)
            .orderBy("dateIso", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    logFirestoreError(err, OperationType.LIST, "users/$uid/visitorDays", auth)
                    return@addSnapshotListener
                }
                if (snap == null || snap.isEmpty) return@addSnapshotListener
                val entities = snap.documents.mapNotNull { doc ->
                    knownVisitorIds.add(doc.id)
                    docToVisitorEntity(doc)
                }
                if (entities.isNotEmpty()) {
                    scope.launch {
                        db.visitorDayDao().upsertAll(entities)
                    }
                }
            }

        val maintReg = maintenanceCol(uid)
            .orderBy("sn", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    logFirestoreError(err, OperationType.LIST, "users/$uid/maintenanceRequests", auth)
                    return@addSnapshotListener
                }
                if (snap == null || snap.isEmpty) return@addSnapshotListener
                val entities = snap.documents.mapNotNull { doc ->
                    knownMaintenanceIds.add(doc.id)
                    docToMaintenanceEntity(doc)
                }
                if (entities.isNotEmpty()) {
                    scope.launch {
                        db.maintenanceRequestDao().upsertAll(entities)
                    }
                }
            }

        val sportsReg = sportsCol(uid)
            .orderBy("sn", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    logFirestoreError(err, OperationType.LIST, "users/$uid/sportsBookings", auth)
                    return@addSnapshotListener
                }
                if (snap == null || snap.isEmpty) return@addSnapshotListener
                val entities = snap.documents.mapNotNull { doc ->
                    knownSportsIds.add(doc.id)
                    docToSportsEntity(doc)
                }
                if (entities.isNotEmpty()) {
                    scope.launch {
                        db.sportsBookingDao().upsertAll(entities)
                    }
                }
            }

        val remReg = remindersCol(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    logFirestoreError(err, OperationType.LIST, "users/$uid/reminders", auth)
                    return@addSnapshotListener
                }
                if (snap == null || snap.isEmpty) return@addSnapshotListener
                val entities = snap.documents.mapNotNull { doc ->
                    knownReminderIds.add(doc.id)
                    docToReminderEntity(doc)
                }
                if (entities.isNotEmpty()) {
                    scope.launch {
                        db.opsReminderDao().upsertAll(entities)
                    }
                }
            }

        listeners.addAll(listOf(keysReg, visitorsReg, maintReg, sportsReg, remReg))
    }

    // -------------------------------------------------------------------------
    // Individual Cloud Upsert & Delete Operations (Crash-safe Result<Unit>)
    // -------------------------------------------------------------------------
    suspend fun syncKey(entry: KeyRegisterEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val docId = entry.si.toString()
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val path = "users/$uid/keys/$docId"
        try {
            if (entry.si < 1 || TestRecordFilter.isDeletedTestKeyRecord(entry)) {
                return@withContext Result.success(Unit)
            }
            val ref = keysCol(uid).document(docId)
            val existsRemotely = knownKeyIds.contains(docId) || ref.get().await().exists()
            if (existsRemotely) {
                knownKeyIds.add(docId)
                ref.update(keyToUpdateMap(entry)).await()
            } else {
                knownKeyIds.add(docId)
                ref.set(keyToCreateMap(entry)).await()
            }
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncKeysBatch(entries: List<KeyRegisterEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        try {
            entries.filter { it.si >= 1 && !TestRecordFilter.isDeletedTestKeyRecord(it) }.forEach { entry ->
                syncKey(entry)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, "users/$uid/keys", auth)
            Result.failure(e)
        }
    }

    suspend fun syncVisitorDay(day: VisitorDayEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val path = "users/$uid/visitorDays/${day.dateIso}"
        try {
            val ref = visitorsCol(uid).document(day.dateIso)
            val existsRemotely = knownVisitorIds.contains(day.dateIso) || ref.get().await().exists()
            if (existsRemotely) {
                knownVisitorIds.add(day.dateIso)
                ref.update(visitorToUpdateMap(day)).await()
            } else {
                knownVisitorIds.add(day.dateIso)
                ref.set(visitorToCreateMap(day)).await()
            }
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncMaintenance(item: MaintenanceRequestEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val docId = item.sn.toString()
        val path = "users/$uid/maintenanceRequests/$docId"
        try {
            if (item.sn < 1) return@withContext Result.success(Unit)
            val ref = maintenanceCol(uid).document(docId)
            val existsRemotely = knownMaintenanceIds.contains(docId) || ref.get().await().exists()
            if (existsRemotely) {
                knownMaintenanceIds.add(docId)
                ref.update(maintenanceToUpdateMap(item)).await()
            } else {
                knownMaintenanceIds.add(docId)
                ref.set(maintenanceToCreateMap(item)).await()
            }
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMaintenance(sn: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val docId = sn.toString()
        val path = "users/$uid/maintenanceRequests/$docId"
        try {
            maintenanceCol(uid).document(docId).delete().await()
            knownMaintenanceIds.remove(docId)
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncSportsBooking(booking: SportsBookingEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val docId = booking.sn.toString()
        val path = "users/$uid/sportsBookings/$docId"
        try {
            if (booking.sn < 1) return@withContext Result.success(Unit)
            val ref = sportsCol(uid).document(docId)
            val existsRemotely = knownSportsIds.contains(docId) || ref.get().await().exists()
            if (existsRemotely) {
                knownSportsIds.add(docId)
                ref.update(sportsToUpdateMap(booking)).await()
            } else {
                knownSportsIds.add(docId)
                ref.set(sportsToCreateMap(booking)).await()
            }
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteSportsBooking(sn: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val docId = sn.toString()
        val path = "users/$uid/sportsBookings/$docId"
        try {
            sportsCol(uid).document(docId).delete().await()
            knownSportsIds.remove(docId)
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncReminder(reminder: OpsReminderEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val path = "users/$uid/reminders/${reminder.id}"
        try {
            val ref = remindersCol(uid).document(reminder.id)
            val existsRemotely = knownReminderIds.contains(reminder.id) || ref.get().await().exists()
            if (existsRemotely) {
                knownReminderIds.add(reminder.id)
                ref.update(reminderToUpdateMap(reminder)).await()
            } else {
                knownReminderIds.add(reminder.id)
                ref.set(reminderToCreateMap(reminder)).await()
            }
            _syncState.value = CloudSyncState.Synced(uid, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Payload Mappers (Strictly aligned with firestore.rules keysets & bounds)
    // -------------------------------------------------------------------------
    private fun normalizeStatus(raw: String): String {
        val allowed = listOf(
            KeyStatus.RETURNED,
            KeyStatus.ISSUED,
            KeyStatus.ISSUED_TO_TENANT,
            KeyStatus.NOT_YET_RETURNED
        )
        return allowed.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
            ?: KeyStatus.ISSUED
    }

    private fun keyToCreateMap(e: KeyRegisterEntity): Map<String, Any> = mapOf(
        "si" to e.si,
        "dateOut" to e.dateOut.take(40),
        "timeOut" to e.timeOut.take(40),
        "flatUnitLocation" to e.flatUnitLocation.take(120),
        "keyCardType" to e.keyCardType.take(120),
        "purpose" to e.purpose.take(500),
        "issuedTo" to e.issuedTo.take(160),
        "qidOrNote" to e.qidOrNote.take(200),
        "dateIn" to e.dateIn.take(40),
        "timeIn" to e.timeIn.take(40),
        "returnedBy" to e.returnedBy.take(160),
        "remarks" to e.remarks.take(500),
        "status" to normalizeStatus(e.status),
        "updatedAtMillis" to e.updatedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun keyToUpdateMap(e: KeyRegisterEntity): Map<String, Any> = mapOf(
        "dateOut" to e.dateOut.take(40),
        "timeOut" to e.timeOut.take(40),
        "flatUnitLocation" to e.flatUnitLocation.take(120),
        "keyCardType" to e.keyCardType.take(120),
        "purpose" to e.purpose.take(500),
        "issuedTo" to e.issuedTo.take(160),
        "qidOrNote" to e.qidOrNote.take(200),
        "dateIn" to e.dateIn.take(40),
        "timeIn" to e.timeIn.take(40),
        "returnedBy" to e.returnedBy.take(160),
        "remarks" to e.remarks.take(500),
        "status" to normalizeStatus(e.status),
        "updatedAtMillis" to e.updatedAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun visitorToCreateMap(e: VisitorDayEntity): Map<String, Any> = mapOf(
        "dateIso" to e.dateIso.take(20),
        "displayDate" to e.displayDate.take(40),
        "monthKey" to e.monthKey.take(20),
        "visitorCount" to e.visitorCount.coerceAtLeast(0),
        "viewingCount" to e.viewingCount.coerceAtLeast(0),
        "flatDetails" to e.flatDetails.take(500),
        "notes" to e.notes.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun visitorToUpdateMap(e: VisitorDayEntity): Map<String, Any> = mapOf(
        "displayDate" to e.displayDate.take(40),
        "monthKey" to e.monthKey.take(20),
        "visitorCount" to e.visitorCount.coerceAtLeast(0),
        "viewingCount" to e.viewingCount.coerceAtLeast(0),
        "flatDetails" to e.flatDetails.take(500),
        "notes" to e.notes.take(500),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun maintenanceToCreateMap(e: MaintenanceRequestEntity): Map<String, Any> = mapOf(
        "sn" to e.sn.coerceAtLeast(1),
        "date" to e.date.take(40),
        "names" to e.names.take(160),
        "phoneNumber" to e.phoneNumber.take(60),
        "workOrderNumber" to e.workOrderNumber.take(80),
        "qidNumber" to e.qidNumber.take(60),
        "flatDetails" to e.flatDetails.take(120),
        "requestDetails" to e.requestDetails.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun maintenanceToUpdateMap(e: MaintenanceRequestEntity): Map<String, Any> = mapOf(
        "date" to e.date.take(40),
        "names" to e.names.take(160),
        "phoneNumber" to e.phoneNumber.take(60),
        "workOrderNumber" to e.workOrderNumber.take(80),
        "qidNumber" to e.qidNumber.take(60),
        "flatDetails" to e.flatDetails.take(120),
        "requestDetails" to e.requestDetails.take(1000),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun normalizeSport(raw: String): String =
        if (raw.trim().equals("FOOTBALL", ignoreCase = true)) "FOOTBALL" else "PADEL"

    private fun sportsToCreateMap(e: SportsBookingEntity): Map<String, Any> = buildMap {
        put("sn", e.sn.coerceAtLeast(1))
        put("sport", normalizeSport(e.sport))
        put("date", e.date.take(40))
        put("time", e.time.take(40))
        put("name", e.name.take(160))
        put("unitDetails", e.unitDetails.take(120))
        put("discount", e.discount.take(60))
        put("referenceNo", e.referenceNo.take(80))
        put("durationPlayed", e.durationPlayed.coerceIn(0.0, 24.0))
        put("remarks", e.remarks.take(500))
        e.attachmentPath?.takeIf { it.isNotBlank() }?.let { put("attachmentPath", it.take(500)) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    private fun sportsToUpdateMap(e: SportsBookingEntity): Map<String, Any> = buildMap {
        put("sport", normalizeSport(e.sport))
        put("date", e.date.take(40))
        put("time", e.time.take(40))
        put("name", e.name.take(160))
        put("unitDetails", e.unitDetails.take(120))
        put("discount", e.discount.take(60))
        put("referenceNo", e.referenceNo.take(80))
        put("durationPlayed", e.durationPlayed.coerceIn(0.0, 24.0))
        put("remarks", e.remarks.take(500))
        val pathVal = e.attachmentPath?.takeIf { it.isNotBlank() }?.take(500)
        put("attachmentPath", pathVal ?: FieldValue.delete())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    private fun normalizeReminderType(raw: String): String = when (raw) {
        ReminderType.DAILY_0740, ReminderType.SAT_AUDIT_0900, ReminderType.KEY_NEXT_DAY -> raw
        else -> ReminderType.KEY_NEXT_DAY
    }

    private fun reminderToCreateMap(e: OpsReminderEntity): Map<String, Any> = buildMap {
        put("id", e.id.take(100))
        put("type", normalizeReminderType(e.type))
        put("title", e.title.ifBlank { "Reminder" }.take(200))
        put("message", e.message.take(500))
        e.relatedKeySi?.takeIf { it >= 1 }?.let { put("relatedKeySi", it) }
        put("scheduledAtMillis", e.scheduledAtMillis.coerceAtLeast(0L))
        e.snoozedUntilMillis?.takeIf { it >= 0L }?.let { put("snoozedUntilMillis", it) }
        put("done", e.isDone)
        put("checklistMask", e.checklistMask.coerceAtLeast(0))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    private fun reminderToUpdateMap(e: OpsReminderEntity): Map<String, Any> = buildMap {
        put("title", e.title.ifBlank { "Reminder" }.take(200))
        put("message", e.message.take(500))
        val keySi = e.relatedKeySi?.takeIf { it >= 1 }
        put("relatedKeySi", keySi ?: FieldValue.delete())
        put("scheduledAtMillis", e.scheduledAtMillis.coerceAtLeast(0L))
        val snoozed = e.snoozedUntilMillis?.takeIf { it >= 0L }
        put("snoozedUntilMillis", snoozed ?: FieldValue.delete())
        put("done", e.isDone)
        put("checklistMask", e.checklistMask.coerceAtLeast(0))
        put("updatedAt", FieldValue.serverTimestamp())
    }

    // -------------------------------------------------------------------------
    // Safe Deserializers (ESTIMATE ServerTimestampBehavior + safe Number casts)
    // -------------------------------------------------------------------------
    private fun docToKeyEntity(doc: DocumentSnapshot): KeyRegisterEntity? {
        val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val si = (data["si"] as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: return null
        return KeyRegisterEntity(
            si = si,
            dateOut = (data["dateOut"] as? String).orEmpty(),
            timeOut = (data["timeOut"] as? String).orEmpty(),
            flatUnitLocation = (data["flatUnitLocation"] as? String).orEmpty(),
            keyCardType = (data["keyCardType"] as? String).orEmpty(),
            purpose = (data["purpose"] as? String).orEmpty(),
            issuedTo = (data["issuedTo"] as? String).orEmpty(),
            qidOrNote = (data["qidOrNote"] as? String).orEmpty(),
            dateIn = (data["dateIn"] as? String).orEmpty(),
            timeIn = (data["timeIn"] as? String).orEmpty(),
            returnedBy = (data["returnedBy"] as? String).orEmpty(),
            remarks = (data["remarks"] as? String).orEmpty(),
            status = (data["status"] as? String).orEmpty().ifBlank { KeyStatus.ISSUED },
            updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun docToVisitorEntity(doc: DocumentSnapshot): VisitorDayEntity? {
        val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val dateIso = (data["dateIso"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id
        return VisitorDayEntity(
            dateIso = dateIso,
            displayDate = (data["displayDate"] as? String).orEmpty().ifBlank { dateIso },
            monthKey = (data["monthKey"] as? String).orEmpty().ifBlank { dateIso.take(7) },
            visitorCount = (data["visitorCount"] as? Number)?.toInt() ?: 0,
            viewingCount = (data["viewingCount"] as? Number)?.toInt() ?: 0,
            flatDetails = (data["flatDetails"] as? String).orEmpty().ifBlank { "-" },
            notes = (data["notes"] as? String).orEmpty()
        )
    }

    private fun docToMaintenanceEntity(doc: DocumentSnapshot): MaintenanceRequestEntity? {
        val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val sn = (data["sn"] as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: return null
        return MaintenanceRequestEntity(
            sn = sn,
            date = (data["date"] as? String).orEmpty(),
            names = (data["names"] as? String).orEmpty(),
            phoneNumber = (data["phoneNumber"] as? String).orEmpty(),
            workOrderNumber = (data["workOrderNumber"] as? String).orEmpty(),
            qidNumber = (data["qidNumber"] as? String).orEmpty(),
            flatDetails = (data["flatDetails"] as? String).orEmpty(),
            requestDetails = (data["requestDetails"] as? String).orEmpty()
        )
    }

    private fun docToSportsEntity(doc: DocumentSnapshot): SportsBookingEntity? {
        val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val sn = (data["sn"] as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: return null
        return SportsBookingEntity(
            sn = sn,
            sport = (data["sport"] as? String).orEmpty().ifBlank { "PADEL" },
            date = (data["date"] as? String).orEmpty(),
            time = (data["time"] as? String).orEmpty(),
            name = (data["name"] as? String).orEmpty(),
            unitDetails = (data["unitDetails"] as? String).orEmpty(),
            discount = (data["discount"] as? String).orEmpty().ifBlank { "FREE" },
            referenceNo = (data["referenceNo"] as? String).orEmpty(),
            durationPlayed = (data["durationPlayed"] as? Number)?.toDouble() ?: 1.0,
            remarks = (data["remarks"] as? String).orEmpty(),
            attachmentPath = data["attachmentPath"] as? String
        )
    }

    private fun docToReminderEntity(doc: DocumentSnapshot): OpsReminderEntity? {
        val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id
        return OpsReminderEntity(
            id = id,
            type = (data["type"] as? String).orEmpty().ifBlank { ReminderType.KEY_NEXT_DAY },
            title = (data["title"] as? String).orEmpty(),
            message = (data["message"] as? String).orEmpty(),
            relatedKeySi = (data["relatedKeySi"] as? Number)?.toInt(),
            scheduledAtMillis = (data["scheduledAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            snoozedUntilMillis = (data["snoozedUntilMillis"] as? Number)?.toLong(),
            isDone = (data["done"] as? Boolean) ?: false,
            checklistMask = (data["checklistMask"] as? Number)?.toInt() ?: 0
        )
    }
}
