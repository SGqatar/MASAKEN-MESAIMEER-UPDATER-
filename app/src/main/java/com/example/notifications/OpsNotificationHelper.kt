package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.OpsReminderEntity
import com.example.data.db.ReminderType

object OpsNotificationHelper {
    const val CHANNEL_ROUTINES = "ops_routines"
    const val CHANNEL_KEY_ALERTS = "ops_key_alerts"

    const val EXTRA_NAV_TARGET = "nav_target"
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val routinesChannel = NotificationChannel(
                CHANNEL_ROUTINES,
                "Daily & Scheduled Ops Routines",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily 07:40 AM sports & log check and Saturday 09:00 AM T-code audit."
            }
            val keyAlertsChannel = NotificationChannel(
                CHANNEL_KEY_ALERTS,
                "Unreturned Key Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Next-day verification reminders for unreturned or active issued keys."
            }
            nm.createNotificationChannel(routinesChannel)
            nm.createNotificationChannel(keyAlertsChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showReminderNotification(context: Context, reminder: OpsReminderEntity) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val navTarget = when (reminder.type) {
            ReminderType.KEY_NEXT_DAY -> "keys"
            ReminderType.DAILY_0740 -> "sports"
            else -> "dashboard"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TARGET, navTarget)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, OpsReminderReceiver::class.java).apply {
            action = OpsReminderReceiver.ACTION_SNOOZE
            putExtra(OpsReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(OpsReminderReceiver.EXTRA_NOTIF_ID, reminder.id.hashCode())
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            (reminder.id + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, OpsReminderReceiver::class.java).apply {
            action = OpsReminderReceiver.ACTION_MARK_DONE
            putExtra(OpsReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(OpsReminderReceiver.EXTRA_NOTIF_ID, reminder.id.hashCode())
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            (reminder.id + "_done").hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (reminder.type == ReminderType.KEY_NEXT_DAY) {
            CHANNEL_KEY_ALERTS
        } else {
            CHANNEL_ROUTINES
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.title)
            .setContentText(reminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "Snooze 30m", snoozePending)
            .addAction(0, "Mark Done", donePending)
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
    }
}
