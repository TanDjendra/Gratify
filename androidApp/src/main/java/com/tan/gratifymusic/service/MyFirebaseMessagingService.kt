package com.tan.gratifymusic.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.extension.now
import com.tan.domain.repository.CommonRepository
import com.tan.gratifymusic.MainActivity
import com.tan.gratifymusic.R
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MyFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {

    private val commonRepository: CommonRepository by inject()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if message contains notification payload
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        val url = remoteMessage.data["url"] ?: remoteMessage.data["browseId"]

        if (title != null || body != null) {
            showNotification(title ?: "GratifyMusic", body ?: "", url)
            saveDeveloperNotification(title ?: "Pesan Pengembang", body ?: "", url)
        }
    }

    private fun saveDeveloperNotification(title: String, body: String, url: String?) {
        try {
            val entity = NotificationEntity(
                channelId = "developer",
                thumbnail = null,
                name = title,
                single = listOf(
                    mapOf(
                        "title" to body,
                        "browseId" to (url ?: "")
                    )
                ),
                album = emptyList(),
                time = now()
            )
            runBlocking {
                commonRepository.insertNotification(entity)
            }
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Failed to save notification: ${e.message}")
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Log new token. In a production app, you would send this token to your server.
        android.util.Log.d("FCM", "Refreshed FCM token: $token")
    }

    private fun showNotification(title: String, message: String, url: String?) {
        val channelId = "fcm_default_channel"
        val channelName = "Push Notifications"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the NotificationChannel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Default channel for FCM notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Open MainActivity when notification is clicked
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("title", title)
            putExtra("body", message)
            putExtra("url", url)
            putExtra("is_developer_notification", true)
            putExtra("already_saved", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.monochrome) // Use clean silhouette icon
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Show notification (random ID to prevent overwriting unless desired)
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
