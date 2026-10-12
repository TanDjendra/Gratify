package com.tan.gratify.service.test.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.tan.gratify.MainActivity
import com.tan.gratify.R
import com.tan.gratify.utils.ComposeResUtils

object NotificationHandler {
    private const val CHANNEL_ID = "transactions_reminder_channel"

    suspend fun createReminderNotification(
        context: Context,
        noti: NotificationModel,
    ) {
        //  No back-stack when launched
        val action = Intent(context, MainActivity::class.java)
        action.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        action.data = "gratify://notification".toUri()
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                action,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = listOfNotNull(
            noti.single.takeIf { it.isNotEmpty() }?.let { "${ComposeResUtils.getResString(ComposeResUtils.StringType.NEW_SINGLES)}: ${it.joinToString { item -> item.title }}" },
            noti.album.takeIf { it.isNotEmpty() }?.let { "${ComposeResUtils.getResString(ComposeResUtils.StringType.NEW_ALBUMS)}: ${it.joinToString { item -> item.title }}" },
        ).joinToString("\n")
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.monochrome)
                .setContentTitle(noti.name)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent) // For launching the MainActivity
                .setAutoCancel(true) // Remove notification when tapped
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        with(NotificationManagerCompat.from(context)) {
            if (!areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            try { notify(noti.channelId.hashCode() xor 0x41000000, builder.build()) }
            catch (_: SecurityException) { /* Permission can change between checking and posting. */ }
        }
    }

    /**
     * Required on Android O+
     */
    fun createNotificationChannel(context: Context) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val name = context.getString(R.string.notif_channel_artists)
            val descriptionText = context.getString(R.string.notif_channel_artists_hint)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            // Register the channel with the system

            notificationManager.createNotificationChannel(channel)
        }
    }
}
