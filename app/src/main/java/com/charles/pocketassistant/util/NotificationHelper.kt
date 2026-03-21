package com.charles.pocketassistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central notification helper with multiple channels and rich notifications.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_BILLS = "bills"
        const val CHANNEL_TASKS = "tasks"
        const val CHANNEL_AI = "ai_processing"
        const val CHANNEL_IMPORT = "import"
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Create all notification channels. Call once at app startup.
     */
    fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Appointment and event reminders"
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_BILLS, "Bill Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Bill due date alerts"
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_TASKS, "Tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Task deadlines and updates"
            },
            NotificationChannel(CHANNEL_AI, "AI Processing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "AI analysis completion updates"
            },
            NotificationChannel(CHANNEL_IMPORT, "Import", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Document import status"
            }
        )
        manager.createNotificationChannels(channels)
    }

    /**
     * Show a reminder notification that opens the app when tapped.
     */
    fun showReminder(id: String, title: String, body: String, itemId: String? = null) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (itemId != null) {
                putExtra("navigate_to", "detail/$itemId")
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    /**
     * Show a bill due notification.
     */
    fun showBillDue(id: String, vendor: String, amount: String, dueDate: String, itemId: String? = null) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (itemId != null) {
                putExtra("navigate_to", "detail/$itemId")
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (amount.isNotBlank()) "$vendor — $amount due" else "$vendor bill due"
        val notification = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setContentTitle(title)
            .setContentText("Due $dueDate. Tap to view details.")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    /**
     * Show AI processing complete notification.
     */
    fun showAiComplete(itemId: String, summary: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "detail/$itemId")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, itemId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AI)
            .setContentTitle("AI analysis complete")
            .setContentText(summary.take(100))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(itemId.hashCode(), notification)
    }
}
