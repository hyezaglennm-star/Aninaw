package com.aninaw.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ReminderScheduler {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }
    }

    fun schedule(context: Context, blockId: String, atMillis: Long, title: String, note: String?) {
        ensureChannel(context)

        // Don’t schedule past times (can cause instant fire or weird OEM behavior)
        val now = System.currentTimeMillis()
        if (atMillis <= now + 2_000L) return

        val req = blockId.hashCode()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("TITLE", title)
            putExtra("NOTE", note)
            putExtra("REQ", req)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            // Android 12+ exact alarm gatekeeping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                } else {
                    // fallback: doesn’t crash, but may not be exact
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                }
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (se: SecurityException) {
            // last-resort fallback so your app doesn’t die
            alarm.set(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }
}