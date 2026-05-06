package com.carorur.tracker

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

class PlaybackKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
                val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty()
                startAsForeground(buildContentText(title, artist))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(content: String) {
        val notification = buildNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildContentText(title: String, artist: String): String {
        if (title.isBlank() && artist.isBlank()) {
            return "La musica sigue sonando en segundo plano"
        }
        if (artist.isBlank()) {
            return title
        }
        if (title.isBlank()) {
            return artist
        }
        return "$title - $artist"
    }

    private fun buildNotification(content: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            2201,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlagImmutable()
        )

        val stopIntent = PendingIntent.getService(
            this,
            2202,
            Intent(this, PlaybackKeepAliveService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlagImmutable()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CARORUR en segundo plano")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reproduccion en segundo plano",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Canal para mantener activa la reproduccion de audio"
        manager.createNotificationChannel(channel)
    }

    private fun pendingFlagImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    companion object {
        const val ACTION_START = "com.carorur.tracker.playback.START"
        const val ACTION_STOP = "com.carorur.tracker.playback.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        private const val CHANNEL_ID = "carorur_playback_channel"
        private const val NOTIFICATION_ID = 3201
    }
}
