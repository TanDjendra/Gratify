package com.tan.gratify.expect

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.koin.mp.KoinPlatform.getKoin
import androidx.core.app.NotificationManagerCompat

actual fun openNotificationSettings(): Boolean = runCatching {
    val context: Context = getKoin().get()
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

actual fun notificationsEnabled(): Boolean? = runCatching {
    val context: Context = getKoin().get()
    NotificationManagerCompat.from(context).areNotificationsEnabled()
}.getOrNull()
