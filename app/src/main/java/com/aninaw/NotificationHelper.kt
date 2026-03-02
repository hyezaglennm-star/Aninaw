package com.aninaw

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_ID_CHECKIN = "aninaw_checkin_reminders"

    fun ensureCheckInChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val name = context.getString(R.string.notif_channel_checkin)
        val desc = context.getString(R.string.notif_channel_checkin_desc)

        val channel = NotificationChannel(
            CHANNEL_ID_CHECKIN,
            name,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = desc
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notify(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        pendingIntent: android.app.PendingIntent?
    ) {
        ensureCheckInChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CHECKIN)
            .setSmallIcon(R.mipmap.ic_launcher) // keep simple (you can replace later)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (pendingIntent != null) builder.setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
