package com.tan.gratify.service

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.*
import com.tan.data.sync.AccountAccess
import com.tan.data.sync.AccountSessionCoordinator
import com.tan.domain.repository.CommonRepository
import com.tan.gratify.MainActivity
import com.tan.gratify.R
import com.tan.gratify.viewModel.handler.fetchSocialNotifications
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class SocialNotificationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params),KoinComponent {
    private val client:SupabaseClient by inject()
    private val sessions:AccountSessionCoordinator by inject()
    private val repository:CommonRepository by inject()
    override suspend fun doWork():Result {
        val owner=inputData.getString("owner") ?: return Result.failure()
        val event=inputData.getString("event") ?: return Result.failure()
        return try {
            val access=withTimeout(20_000) { sessions.access.first { it != AccountAccess.Loading } }
            if(access !is AccountAccess.Ready || access.owner!=owner || repository.notificationAccountOwner()!=owner) return Result.success()
            if(access.offline) return retry()
            val notification=fetchSocialNotifications(client,owner,event).singleOrNull() ?: return Result.success()
            // Inbox polling may already have saved it; the delivery receipt is a separate claim.
            repository.insertNotificationOnce(notification)
            val manager=NotificationManagerCompat.from(applicationContext)
            val channel=if(notification.channelId=="social_story")"gratify_stories" else "gratify_messages"
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,
                applicationContext.getString(if(channel=="gratify_stories") R.string.notif_channel_stories else R.string.notif_channel_messages),NotificationManager.IMPORTANCE_DEFAULT))
            if(!canPostNotification(applicationContext,channel)) return Result.success()
            val shown=notification.copy(channelId="push_social_receipt")
            if (!repository.claimNotificationDelivery(shown) || repository.notificationAccountOwner()!=owner) return Result.success()
            val details=notification.single.single()
            val target=if(channel=="gratify_stories")"story=${details["sourceId"]}" else "peer=${details["actorId"]}"
            val intent=Intent(applicationContext,MainActivity::class.java).apply {
                action=Intent.ACTION_VIEW;flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data="gratify://friends?$target&owner=$owner".toUri()
            }
            val pending=PendingIntent.getActivity(applicationContext,event.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val built=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(R.drawable.monochrome)
                .setContentTitle(notification.name).setContentText(details["title"])
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build()
            try { manager.notify("social:$event",event.hashCode(),built) } catch (_:SecurityException) { }
            Result.success()
        } catch (_:TimeoutCancellationException) { retry() }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) { retry() }
    }
    private fun retry()=if(runAttemptCount<4)Result.retry() else Result.failure()
    companion object {
        fun enqueue(context:Context,event:String,owner:String) {
            if(runCatching { UUID.fromString(event);UUID.fromString(owner) }.isFailure)return
            val request=OneTimeWorkRequestBuilder<SocialNotificationWorker>().setInputData(workDataOf("event" to event,"owner" to owner))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("social-push:$owner:$event",ExistingWorkPolicy.KEEP,request)
        }
    }
}
