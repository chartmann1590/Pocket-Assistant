package com.charles.pocketassistant.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule(reminderId: String, title: String, remindAt: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", reminderId)
            putExtra("title", title)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, "reminders")
            .setContentTitle("Pocket Assistant Reminder")
            .setContentText(intent.getStringExtra("title") ?: "Task reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        manager.notify((intent.getStringExtra("id") ?: "0").hashCode(), notification)
    }
}
