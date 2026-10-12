package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tan.domain.data.entities.NotificationEntity
import com.tan.gratify.expect.notificationsEnabled
import com.tan.gratify.expect.openNotificationSettings
import com.tan.gratify.service.DeveloperNotificationWorker
import com.tan.gratify.ui.screen.home.NotificationInboxContent
import com.tan.gratify.ui.theme.AppTheme

/** Debug-only fixture. Sample cards stay in memory; optional worker check writes only to this device. */
class NotificationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val mode = intent.getStringExtra("local_worker_check")
        if (mode == "cache" || mode == "push") {
            DeveloperNotificationWorker.enqueue(this, "Notification audit ${intent.getStringExtra("sample_id").orEmpty().take(30)}",
                "Local Android check: the inbox and device alert have separate delivery records.", null, mode == "push")
        }
        setContent { AppTheme {
            val peer = "00000000-0000-4000-8000-000000000002"
            var rows by remember { mutableStateOf(listOf(
                NotificationEntity(id = 1, channelId = "developer", name = "Welcome to Gratify",
                    single = listOf(mapOf("title" to "Your music, your space. Find the latest updates here.", "browseId" to "https://example.invalid/updates"))),
                NotificationEntity(id = 2, channelId = "follower_activity", name = "Nara",
                    single = listOf(mapOf("browseId" to peer))),
                NotificationEntity(id = 3, channelId = "social_message", name = "Ari",
                    single = listOf(mapOf("title" to "Sent you a message. Open Friends to read it.", "actorId" to peer,
                        "ownerId" to "00000000-0000-4000-8000-000000000001"))),
            )) }
            var error by remember { mutableStateOf<String?>(null) }
            var deletionFailed by remember { mutableStateOf(false) }
            var following by remember { mutableStateOf(emptySet<String>()) }
            var enabled by remember { mutableStateOf(notificationsEnabled()) }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = notificationsEnabled() }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            NotificationInboxContent(rows, following, notificationsEnabled = enabled, error = error,
                onBack = { finish() }, onRefresh = { error = null }, onDismissError = { error = null },
                onSettings = { if (!openNotificationSettings()) error = "Settings could not be opened." },
                onDelete = { id ->
                    if (!deletionFailed) { deletionFailed = true; error = "The notification could not be deleted. Try again." }
                    else { rows = rows.filterNot { it.id == id }; error = null }
                }, onFollowBack = { following = following + it },
                onOpenLink = { error("Fixture browser failure") },
                onOpenProfile = { _, _ -> error = "Profile target verified." },
                onOpenSocial = { error = "Friends target verified." })
        } }
    }
}
