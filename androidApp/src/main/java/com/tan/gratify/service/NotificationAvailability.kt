package com.tan.gratify.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Called before a delivery claim, so blocked alerts are never marked as displayed. */
internal fun canPostNotification(context: Context, channelId: String): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
    val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
    return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
}
