package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.db.MasakenDatabase
import com.example.data.db.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class OpsReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, reminderId?.hashCode() ?: 0)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = MasakenDatabase.getInstance(context)
                val dao = db.opsReminderDao()
                val scheduler = OpsReminderScheduler(context)

                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        scheduler.syncAll(dao.getAllOnce())
                    }
                    ACTION_TRIGGER -> {
                        if (reminderId != null) {
                            val reminder = dao.getById(reminderId)
                            if (reminder != null && !reminder.isDone) {
                                OpsNotificationHelper.showReminderNotification(context, reminder)
                                val qatarTz = TimeZone.getTimeZone("Asia/Qatar")
                                val nextTriggerMillis: Long? = when (reminder.type) {
                                    ReminderType.DAILY_0740 -> Calendar.getInstance(qatarTz).apply {
                                        add(Calendar.DAY_OF_YEAR, 1)
                                        set(Calendar.HOUR_OF_DAY, 7)
                                        set(Calendar.MINUTE, 40)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    ReminderType.DAILY_1530 -> Calendar.getInstance(qatarTz).apply {
                                        add(Calendar.DAY_OF_YEAR, 1)
                                        set(Calendar.HOUR_OF_DAY, 15)
                                        set(Calendar.MINUTE, 30)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    ReminderType.WEEKLY_REPORT_FRI -> Calendar.getInstance(qatarTz).apply {
                                        add(Calendar.DAY_OF_YEAR, 1)
                                        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                                            add(Calendar.DAY_OF_YEAR, 1)
                                        }
                                        set(Calendar.HOUR_OF_DAY, 16)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    ReminderType.MONTHLY_REPORT_EOM -> Calendar.getInstance(qatarTz).apply {
                                        add(Calendar.MONTH, 1)
                                        set(Calendar.DAY_OF_MONTH, 1)
                                        set(Calendar.HOUR_OF_DAY, 9)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    else -> null
                                }
                                if (nextTriggerMillis != null) {
                                    val updated = reminder.copy(
                                        scheduledAtMillis = nextTriggerMillis,
                                        snoozedUntilMillis = null
                                    )
                                    dao.upsert(updated)
                                    scheduler.scheduleReminder(updated)
                                }
                            }
                        }
                    }
                    ACTION_SNOOZE -> {
                        if (reminderId != null) {
                            NotificationManagerCompat.from(context).cancel(notifId)
                            val reminder = dao.getById(reminderId)
                            if (reminder != null) {
                                val snoozedUntil = System.currentTimeMillis() + 30 * 60 * 1000L
                                val updated = reminder.copy(snoozedUntilMillis = snoozedUntil, isDone = false)
                                dao.upsert(updated)
                                scheduler.scheduleReminder(updated)
                            }
                        }
                    }
                    ACTION_MARK_DONE -> {
                        if (reminderId != null) {
                            NotificationManagerCompat.from(context).cancel(notifId)
                            val reminder = dao.getById(reminderId)
                            if (reminder != null) {
                                val updated = reminder.copy(isDone = true, snoozedUntilMillis = null)
                                dao.upsert(updated)
                                scheduler.cancelReminder(reminder.id)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER = "com.example.masaken.ACTION_TRIGGER_REMINDER"
        const val ACTION_SNOOZE = "com.example.masaken.ACTION_SNOOZE_REMINDER"
        const val ACTION_MARK_DONE = "com.example.masaken.ACTION_DONE_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
