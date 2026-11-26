package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.db.OpsReminderEntity

class OpsReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(reminder: OpsReminderEntity) {
        if (reminder.isDone) {
            cancelReminder(reminder.id)
            return
        }
        val triggerAt = maxOf(
            reminder.snoozedUntilMillis ?: reminder.scheduledAtMillis,
            System.currentTimeMillis() + 5_000L
        )
        val intent = Intent(context, OpsReminderReceiver::class.java).apply {
            action = OpsReminderReceiver.ACTION_TRIGGER
            putExtra(OpsReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Use Play-compliant setAndAllowWhileIdle without requiring SCHEDULE_EXACT_ALARM
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pending
        )
    }

    fun cancelReminder(reminderId: String) {
        val intent = Intent(context, OpsReminderReceiver::class.java).apply {
            action = OpsReminderReceiver.ACTION_TRIGGER
            putExtra(OpsReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    fun syncAll(reminders: List<OpsReminderEntity>) {
        reminders.forEach { scheduleReminder(it) }
    }
}
