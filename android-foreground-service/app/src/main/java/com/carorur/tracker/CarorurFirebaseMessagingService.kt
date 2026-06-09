package com.carorur.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CarorurFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CarorurPushRegistrar.syncToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        ensureChannel()

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "CARORUR"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Hay una nueva actualizacion en la app"

        val openIntent = PendingIntent.getActivity(
            this,
            9001,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlagImmutable()
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cambios de CARORUR",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Notificaciones cuando hay cambios en contenido compartido"
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
        const val CHANNEL_ID = "carorur_updates_channel"
    }
}

object CarorurPushRegistrar {
    private const val REGISTER_URL = "https://script.google.com/macros/s/AKfycbylzFYAHLS28fs99udXE_PRan2hxPRHRN14-5n0shkHqXiawXJInk_F8JqEKvwZiZwF/exec"

    fun syncToken(context: Context, token: String) {
        if (token.isBlank()) return

        Thread {
            try {
                val payload = org.json.JSONObject().apply {
                    put("action", "registerDevice")
                    put("platform", "android")
                    put("device_id", android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "")
                    put("token", token)
                    put("app_version", BuildConfig.VERSION_NAME)
                }

                val connection = java.net.URL(REGISTER_URL).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.outputStream.use { out ->
                    out.write(payload.toString().toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                connection.inputStream.close()
                connection.disconnect()
            } catch (_: Throwable) {
            }
        }.start()
    }
}
