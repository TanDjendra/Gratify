package com.tan.media3.service.download

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.tan.media3.R
import com.tan.domain.mediaservice.handler.DownloadHandler
import org.koin.android.ext.android.inject

@UnstableApi
internal class MusicDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        1000L,
        CHANNEL_ID,
        R.string.download,
        0,
    ) {
    private val downloadUtil: DownloadHandler by inject<DownloadHandler>()

    override fun getDownloadManager() = (downloadUtil as DownloadUtils).downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        (downloadUtil as DownloadUtils).downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.monochrome,
            contentIntent(this),
            if (downloads.size == 1) {
                Util.fromUtf8Bytes(downloads[0].request.data)
            } else {
                resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size)
            },
            downloads,
            notMetRequirements,
        )

    class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
    ) : DownloadManager.Listener {
        private val terminalStates = TerminalDownloadStates()

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            terminalStates.removed(download.request.id)
        }
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            if (download.state != Download.STATE_FAILED && download.state != Download.STATE_COMPLETED) {
                terminalStates.removed(download.request.id)
                return
            }
            if (!terminalStates.changed(download.request.id, download.state)) return
            val manager = androidx.core.app.NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            if (download.state == Download.STATE_FAILED) {
                val notification =
                    notificationHelper.buildDownloadFailedNotification(
                        context,
                        R.drawable.baseline_error_outline_24,
                        contentIntent(context),
                        Util.fromUtf8Bytes(download.request.data),
                    )
                try { manager.notify("download:${download.request.id}", download.state, notification) }
                catch (_: SecurityException) { }
            } else if (download.state == Download.STATE_COMPLETED) {
                val notification =
                    notificationHelper.buildDownloadCompletedNotification(
                        context,
                        R.drawable.baseline_downloaded,
                        contentIntent(context),
                        Util.fromUtf8Bytes(download.request.data),
                    )
                try { manager.notify("download:${download.request.id}", download.state, notification) }
                catch (_: SecurityException) { }
            }
        }


    }

    companion object {
        private fun contentIntent(context: Context): android.app.PendingIntent? {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            intent.data = android.net.Uri.parse("gratify://downloads")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            return android.app.PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1000
        const val JOB_ID = 1000
    }
}