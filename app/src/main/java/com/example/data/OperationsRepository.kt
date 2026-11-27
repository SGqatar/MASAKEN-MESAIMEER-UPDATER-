package com.example.data

import com.example.data.cloud.FirestoreSyncRepository
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.MasakenDatabase
import com.example.data.db.OpsReminderEntity
import com.example.data.db.ReminderType
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.data.sync.TestRecordFilter
import com.example.notifications.OpsReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

class OperationsRepository(
    private val db: MasakenDatabase,
    private val scheduler: OpsReminderScheduler,
    private val cloudSync: FirestoreSyncRepository? = null
) {
    val keysFlow: Flow<List<KeyRegisterEntity>> = db.keyRegisterDao().observeAll()
    val visitorsFlow: Flow<List<VisitorDayEntity>> = db.visitorDayDao().observeAll()
    val maintenanceFlow: Flow<List<MaintenanceRequestEntity>> = db.maintenanceRequestDao().observeAll()
    val sportsFlow: Flow<List<SportsBookingEntity>> = db.sportsBookingDao().observeAll()
    val remindersFlow: Flow<List<OpsReminderEntity>> = db.opsReminderDao().observeAll()

    suspend fun ensureSeeded() {
        MasakenDatabase.seedIfEmpty(db)
        scheduler.syncAll(db.opsReminderDao().getAllOnce())
    }

    suspend fun getNextKeySi(): Int = db.keyRegisterDao().getMaxSi() + 1

    suspend fun saveKeyEntry(entry: KeyRegisterEntity): Boolean {
        if (TestRecordFilter.isDeletedTestKeyRecord(entry)) {
            return false
        }
        val clean = entry.copy(updatedAtMillis = System.currentTimeMillis())
        db.keyRegisterDao().upsert(clean)
        cloudSync?.syncKey(clean)

        val reminderId = "key_next_day_${clean.si}"
        if (clean.isUnreturnedAlert) {
            val nextDayMillis = Calendar.getInstance(TimeZone.getTimeZone("Asia/Qatar")).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val reminder = OpsReminderEntity(
                id = reminderId,
                type = ReminderType.KEY_NEXT_DAY,
                title = "Verify Key Return — SI ${clean.si} (${clean.flatUnitLocation})",
                message = "${clean.status} • Issued to ${clean.issuedTo} on ${clean.dateOut} (${clean.timeOut})",
                relatedKeySi = clean.si,
                scheduledAtMillis = nextDayMillis,
                snoozedUntilMillis = null,
                isDone = false
            )
            db.opsReminderDao().upsert(reminder)
            scheduler.scheduleReminder(reminder)
            cloudSync?.syncReminder(reminder)
        } else {
            val existing = db.opsReminderDao().getById(reminderId)
            if (existing != null) {
                val completed = existing.copy(isDone = true, snoozedUntilMillis = null)
                db.opsReminderDao().upsert(completed)
                scheduler.cancelReminder(reminderId)
                cloudSync?.syncReminder(completed)
            }
        }
        return true
    }

    suspend fun markKeyReturned(entry: KeyRegisterEntity, returnedBy: String, dateIn: String, timeIn: String) {
        val updated = entry.copy(
            status = KeyStatus.RETURNED,
            returnedBy = returnedBy.ifBlank { entry.issuedTo },
            dateIn = dateIn.ifBlank { "11/26/2026" },
            timeIn = timeIn.ifBlank { "16:36" },
            remarks = if (entry.remarks.startsWith("OVERDUE", ignoreCase = true)) {
                "Verified & returned"
            } else {
                entry.remarks
            }
        )
        saveKeyEntry(updated)
    }

    /**
     * November Return Reconciliation Helper:
     * Completes missing return timestamps on November returned rows using 11/26/2026 at 16:36.
     */
    suspend fun reconcileNovemberReturnedRows(): Int {
        val all = db.keyRegisterDao().getAllOnce()
        val toFix = all.filter { it.needsNovemberReconciliation }
        if (toFix.isEmpty()) return 0

        val fixed = toFix.map { row ->
            row.copy(
                dateIn = row.dateIn.ifBlank { "11/26/2026" },
                timeIn = row.timeIn.ifBlank { "16:36" },
                returnedBy = row.returnedBy.ifBlank { row.issuedTo },
                remarks = if (row.remarks.contains("pending", ignoreCase = true)) {
                    "Nov return reconciled (11/26/2026 16:36)"
                } else {
                    row.remarks
                },
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        db.keyRegisterDao().upsertAll(fixed)
        cloudSync?.syncKeysBatch(fixed)
        return fixed.size
    }

    suspend fun incrementTodayVisitor(dateIso: String = "2026-11-26", displayDate: String = "26-Nov-2026") {
        val existing = db.visitorDayDao().getByDateIso(dateIso)
        val updated = if (existing != null) {
            existing.copy(visitorCount = existing.visitorCount + 1)
        } else {
            VisitorDayEntity(
                dateIso = dateIso,
                displayDate = displayDate,
                monthKey = dateIso.take(7),
                visitorCount = 1,
                viewingCount = 0,
                flatDetails = "-"
            )
        }
        db.visitorDayDao().upsert(updated)
        cloudSync?.syncVisitorDay(updated)
    }

    suspend fun incrementTodayViewing(
        flatDetail: String,
        dateIso: String = "2026-11-26",
        displayDate: String = "26-Nov-2026"
    ) {
        val existing = db.visitorDayDao().getByDateIso(dateIso)
        val cleanFlat = flatDetail.trim()
        val updated = if (existing != null) {
            val combinedFlats = when {
                cleanFlat.isBlank() -> existing.flatDetails
                existing.flatDetails.isBlank() || existing.flatDetails == "-" -> cleanFlat
                else -> "${existing.flatDetails}, $cleanFlat"
            }
            existing.copy(
                viewingCount = existing.viewingCount + 1,
                visitorCount = maxOf(existing.visitorCount, existing.viewingCount + 1),
                flatDetails = combinedFlats
            )
        } else {
            VisitorDayEntity(
                dateIso = dateIso,
                displayDate = displayDate,
                monthKey = dateIso.take(7),
                visitorCount = 1,
                viewingCount = 1,
                flatDetails = cleanFlat.ifBlank { "-" }
            )
        }
        db.visitorDayDao().upsert(updated)
        cloudSync?.syncVisitorDay(updated)
    }

    suspend fun saveVisitorDay(day: VisitorDayEntity) {
        db.visitorDayDao().upsert(day)
        cloudSync?.syncVisitorDay(day)
    }

    suspend fun saveMaintenanceRequest(item: MaintenanceRequestEntity) {
        val finalSn = if (item.sn == 0) {
            (db.maintenanceRequestDao().getAllOnce().maxOfOrNull { it.sn } ?: 0) + 1
        } else {
            item.sn
        }
        val clean = item.copy(sn = finalSn)
        db.maintenanceRequestDao().upsert(clean)
        cloudSync?.syncMaintenance(clean)
    }

    suspend fun deleteMaintenanceRequest(sn: Int) {
        db.maintenanceRequestDao().deleteBySn(sn)
        cloudSync?.deleteMaintenance(sn)
    }

    suspend fun saveSportsBooking(booking: SportsBookingEntity) {
        val finalSn = if (booking.sn == 0) db.sportsBookingDao().getMaxSn() + 1 else booking.sn
        val clean = booking.copy(sn = finalSn)
        db.sportsBookingDao().upsert(clean)
        cloudSync?.syncSportsBooking(clean)
    }

    suspend fun deleteSportsBooking(sn: Int) {
        db.sportsBookingDao().deleteBySn(sn)
        cloudSync?.deleteSportsBooking(sn)
    }

    suspend fun toggleReminderChecklist(reminder: OpsReminderEntity, bitIndex: Int) {
        val newMask = reminder.checklistMask xor (1 shl bitIndex)
        val allChecked = (newMask and 0b1111) == 0b1111
        val updated = reminder.copy(
            checklistMask = newMask,
            isDone = allChecked
        )
        db.opsReminderDao().upsert(updated)
        if (allChecked) scheduler.cancelReminder(updated.id) else scheduler.scheduleReminder(updated)
        cloudSync?.syncReminder(updated)
    }

    suspend fun snoozeReminder30Min(reminder: OpsReminderEntity) {
        val snoozed = reminder.copy(
            snoozedUntilMillis = System.currentTimeMillis() + 30 * 60 * 1000L,
            isDone = false
        )
        db.opsReminderDao().upsert(snoozed)
        scheduler.scheduleReminder(snoozed)
        cloudSync?.syncReminder(snoozed)
    }

    suspend fun setReminderDone(reminder: OpsReminderEntity, done: Boolean) {
        val updated = reminder.copy(
            isDone = done,
            snoozedUntilMillis = null
        )
        db.opsReminderDao().upsert(updated)
        if (done) scheduler.cancelReminder(updated.id) else scheduler.scheduleReminder(updated)
        cloudSync?.syncReminder(updated)
    }
}
