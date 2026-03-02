package com.aninaw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent

class CheckInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {

        val prefs = context.getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("checkin_reminders_enabled", false)) return

        val mask = prefs.getInt("checkin_reminders_days_mask", 0b1111111)

        val cal = java.util.Calendar.getInstance()
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)

        val bitIndex = when (dow) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }

        if ((mask and (1 shl bitIndex)) == 0) return
        // Open the Check-in entry point when notification is tapped
        val openIntent = Intent(context, CheckInAnchorActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pending = PendingIntent.getActivity(
            context,
            2001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationHelper.notify(
            context = context,
            notificationId = 2002,
            title = "Check in with yourself",
            body = "A gentle moment. No pressure.",
            pendingIntent = pending
        )

        // Reschedule next day (so it stays daily even if device is picky)
        ReminderScheduler.scheduleDaily(context)
    }
}