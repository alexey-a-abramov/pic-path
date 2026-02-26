package com.imageviewer.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.imageviewer.R

object NotificationHelper {
    private const val CHANNEL_ID = "pic_selector_copy"
    private const val CHANNEL_NAME = "Copy Notifications"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifications for copied image paths"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showCopyNotification(context: Context, path: String) {
        createNotificationChannel(context)

        val fileName = path.substringAfterLast("/")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_copy)
            .setContentTitle("Path Copied")
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(path))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission not granted, silently fail
        }
    }
}
