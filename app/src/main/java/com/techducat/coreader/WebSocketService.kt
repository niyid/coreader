package com.techducat.coreader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class WebSocketService : Service() {
    companion object {
        private const val TAG = "WebSocketService"
        private const val SERVICE_CHANNEL_ID = "SocketIOServiceChannelID"
    }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(
            this,
            SERVICE_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.service_channel_name))
            .setContentText(getString(R.string.service_channel_content))
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CoreaderApp:CoreaderWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

        Log.d(TAG, "Foreground service '$SERVICE_CHANNEL_ID' running")

        startForeground(1, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}