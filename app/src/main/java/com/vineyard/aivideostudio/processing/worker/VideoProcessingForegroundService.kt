package com.vineyard.aivideostudio.processing.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.vineyard.aivideostudio.MainActivity
import com.vineyard.aivideostudio.core.common.AppConstants

class VideoProcessingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("AI Video Studio is processing video stages...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    AppConstants.NOTIFICATION_ID_PROCESSING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )
            } else {
                startForeground(AppConstants.NOTIFICATION_ID_PROCESSING, notification)
            }
        } else {
            startForeground(AppConstants.NOTIFICATION_ID_PROCESSING, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_PROCESSING)
            .setContentTitle("Editora Video Studio")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_PROCESSING,
                "Video Processing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of ongoing AI video transformations"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
