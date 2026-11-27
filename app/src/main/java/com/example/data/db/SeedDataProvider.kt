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
     * Nov 1–26, 2026 verified baseline (140 entries: SN 1..144, skipping 9, 17, 42, 48):
     * - 82 Football + 58 Padel, all marked FREE
     * - Includes confirmed Nov 26, 2026 entries (SN 140..144)
     */
    fun buildSportsBookingsSeed(): List<SportsBookingEntity> =
        SportsBacklogSeed.buildNovember2026Backlog()

    fun buildInitialRemindersSeed(keys: List<KeyRegisterEntity>): List<OpsReminderEntity> {
        val tz = TimeZone.getTimeZone("Asia/Qatar")
        val now = System.currentTimeMillis()

        fun nextDailyAt(hour: Int, minute: Int): Long = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val daily0740 = nextDailyAt(7, 40)
        val daily1530 = nextDailyAt(15, 30)

        val satAudit = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 28, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val weeklySat = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY || timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val monthlyFirstDay = Calendar.getInstance(tz).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
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
                message = "Log prior-day Padel & Football bookings, reconcile Key Register returns, and verify PM Visitor counts.",
                scheduledAtMillis = daily0740,
                isDone = false,
                checklistMask = 0
            ),
            OpsReminderEntity(
                id = "daily_1530",
                type = ReminderType.DAILY_1530,
                title = "03:30 PM Daily Shift Briefing",
                message = "Review 2 Nov unreturned keys (M-A04-16, M-F30-18) + SI 1283 backlog, follow up 4 Move-In T-codes with Mr. Shajith, and summarize PM Visitors & Sports.",
                scheduledAtMillis = daily1530,
                isDone = false
            ),
            OpsReminderEntity(
                id = "sat_audit_2026_11_28",
                type = ReminderType.SAT_AUDIT_0900,
                title = "Sat Nov 28, 09:00 AM — T-Code & Unreturned Key Audit",
                message = "Verify 4 Move-In T-codes with Mr. Shajith (SI 1638, 1639, 1644, 1645) and recover Nov unreturned keys (M-A04-16, M-F30-18) + SI 1283 backlog.",
                scheduledAtMillis = satAudit,
                isDone = false
            ),
            OpsReminderEntity(
                id = "weekly_report_fri",
                type = ReminderType.WEEKLY_REPORT_FRI,
                title = "Saturday Weekly Keys & Visitors Report Due",
                message = "Every Saturday: generate WSF-PMD-F-09 Weekly Key & Visitors XLSX report and prompt To/CC recipients for dispatch.",
                scheduledAtMillis = weeklySat,
                isDone = false
            ),
            OpsReminderEntity(
                id = "monthly_report_eom",
                type = ReminderType.MONTHLY_REPORT_EOM,
                title = "1st-of-Month Keys, Visitors & Sports Reports",
                message = "1st day of the month: compile monthly Key Register, PM Visitors Jan–Dec, and Padel/Football XLSX bundle and send via Gmail/Mail.",
                scheduledAtMillis = monthlyFirstDay,
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
