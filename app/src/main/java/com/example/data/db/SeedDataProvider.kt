package com.example.data.db

import com.example.data.sync.TestRecordFilter
import java.util.Calendar
import java.util.TimeZone

object SeedDataProvider {

    fun buildKeyRegisterSeed(): List<KeyRegisterEntity> =
        TestRecordFilter.filterKeyRecords(KeyRegisterBacklogSeed.buildKeyRegisterBacklog())

    fun buildVisitorDaysSeed(): List<VisitorDayEntity> =
        VisitorsMaintenanceBacklogSeed.buildVisitorDays2026Backlog()

    fun buildMaintenanceSeed(): List<MaintenanceRequestEntity> =
        VisitorsMaintenanceBacklogSeed.buildMaintenanceBacklog()

    /**
     * Exact Nov 1–25, 2026 baseline extracted from the official November 2026 PDF report:
     * - 135 bookings total (SN 1..139, skipping 9, 17, 42, 48)
     * - 79 Football = 106.5 hrs
     * - 56 Padel = 59.5 hrs
     * - 166.0 total hours
     * - All marked FREE
     * - Nov 26, 2026 pending next-day entry
     */
    fun buildSportsBookingsSeed(): List<SportsBookingEntity> =
        SportsBacklogSeed.buildNovember2026Backlog()

    fun buildInitialRemindersSeed(keys: List<KeyRegisterEntity>): List<OpsReminderEntity> {
        val tz = TimeZone.getTimeZone("Asia/Qatar")
        val daily0740 = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val satAudit = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 28, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextDayKeyTime = Calendar.getInstance(tz).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val reminders = mutableListOf(
            OpsReminderEntity(
                id = "daily_0740",
                type = ReminderType.DAILY_0740,
                title = "07:40 AM Daily Sports & Log Check",
                message = "Log Nov 26 Padel & Football bookings, reconcile Key Register returns, and verify PM Visitor counts.",
                scheduledAtMillis = daily0740,
                isDone = false,
                checklistMask = 0
            ),
            OpsReminderEntity(
                id = "sat_audit_2026_11_28",
                type = ReminderType.SAT_AUDIT_0900,
                title = "Sat Nov 28, 09:00 AM — T-Code & Unreturned Key Audit",
                message = "Verify outstanding T-codes and unreturned key statuses (SI 1283, 1634, 1638, 1639, 1643–1645).",
                scheduledAtMillis = satAudit,
                isDone = false
            )
        )

        keys.filter { it.isUnreturnedAlert }.forEach { key ->
            reminders.add(
                OpsReminderEntity(
                    id = "key_next_day_${key.si}",
                    type = ReminderType.KEY_NEXT_DAY,
                    title = "Verify Key Return — SI ${key.si} (${key.flatUnitLocation})",
                    message = "${key.status} • Issued to ${key.issuedTo} on ${key.dateOut} (${key.timeOut})",
                    relatedKeySi = key.si,
                    scheduledAtMillis = nextDayKeyTime,
                    isDone = false
                )
            )
        }
        return reminders
    }
}
