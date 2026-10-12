package com.tan.gratify.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.*
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.data.entities.eventKey
import com.tan.domain.repository.CommonRepository
import com.tan.gratify.MainActivity
import com.tan.gratify.R
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DeveloperNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repository: CommonRepository by inject()
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.failure()
        val body = inputData.getString("body").orEmpty()
        val url = inputData.getString("url").orEmpty()
        return try {
            val card = entity(title, body, url)
            repository.insertNotificationOnce(card)
            if (inputData.getBoolean("show", false)) {
                createChannel(applicationContext)
                if (canPostNotification(applicationContext, CHANNEL_ID) && repository.claimDeveloperNotificationDelivery(card)) {
                    post(applicationContext, title, body, url)
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }

    companion object {
        const val CHANNEL_ID = "fcm_default_channel"
        private fun entity(title: String, body: String, url: String) = NotificationEntity(
            channelId = "developer", name = title,
            single = listOf(mapOf("title" to body, "browseId" to url)),
        )
        fun enqueue(context: Context, title: String, body: String, url: String?, show: Boolean) {
            // WorkManager Data is limited to 10 KiB, including UTF-8 encoding.
            val clippedTitle = title.take(240)
            val clippedBody = body.take(1200)
            val clippedUrl = url.orEmpty().take(800)
            val key = entity(clippedTitle, clippedBody, clippedUrl).eventKey()
            val request = OneTimeWorkRequestBuilder<DeveloperNotificationWorker>().setInputData(workDataOf(
                "title" to clippedTitle, "body" to clippedBody, "url" to clippedUrl, "show" to show,
            )).build()
            WorkManager.getInstance(context).enqueueUniqueWork("developer-notification-${key.hashCode()}", ExistingWorkPolicy.KEEP, request)
        }
        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_gratify), NotificationManager.IMPORTANCE_DEFAULT))
        }
        private fun post(context: Context, title: String, body: String, url: String) {
            createChannel(context)
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val key = entity(title, body, url).eventKey()
            val notificationId = key.hashCode() xor 0x20000000
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = Intent.ACTION_VIEW
                // The inbox retains the original link for a deliberate user action.
                data = "gratify://notification".toUri()
                putExtra("notification_event", key)
            }
            val pendingIntent = PendingIntent.getActivity(context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.monochrome).setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true)
                .setContentIntent(pendingIntent).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            try { manager.notify(notificationId, notification) } catch (_: SecurityException) { }
        }
    }
}
