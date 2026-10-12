package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.domain.data.entities.NotificationEntity
import com.tan.gratify.ui.screen.home.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.NotificationSyncProblem
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class NotificationInboxRenderTest {
    private val peer = "00000000-0000-4000-8000-000000000002"
    private val announcement = NotificationEntity(id = 1, channelId = "developer", name = "Welcome to Gratify",
        single = listOf(mapOf("title" to "Your music, your space. Find the latest updates here.", "browseId" to "https://example.invalid/updates")))
    private val follower = NotificationEntity(id = 2, channelId = "follower_activity", name = "Nara",
        single = listOf(mapOf("browseId" to peer)))
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun button(scene: ImageComposeScene, label: String) = nodes(scene).first {
        (label in texts(it) || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true) && it.config.contains(SemanticsActions.OnClick)
    }
    private fun click(scene: ImageComposeScene, label: String) = button(scene, label).config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(scene: ImageComposeScene) { repeat(16) { scene.render(it * 16_000_000L).close(); delay(16) } }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val file = root.resolve("artifacts/notifications-2026-10-08/$name.png"); Files.createDirectories(file.parent)
        scene.render(3_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
    }

    @Test fun inboxFitsSmallLargeTextAndWideScreensWithAccessibleDeleteTargets(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            val deleted = mutableListOf<Long>()
            val scene = ImageComposeScene(width, 1200, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { NotificationInboxContent(listOf(announcement, follower), onBack = {}, onRefresh = {}, onDelete = { deleted += it }) }
            }
            try {
                settle(scene)
                assertTrue(nodes(scene).filter { texts(it).isNotEmpty() }.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width }, "text bounds at $width")
                assertTrue(nodes(scene).first { "Notification" in texts(it) }.boundsInRoot.height < 60, "single line toolbar at $width")
                assertTrue(button(scene, "Delete notification: Welcome to Gratify").boundsInRoot.height >= 48)
                click(scene, "Delete notification: Welcome to Gratify"); assertEquals(listOf(1L), deleted)
                // A request alone must not remove the card: the observed database decides removal.
                settle(scene); assertTrue(nodes(scene).any { "Welcome to Gratify" in texts(it) })
                snapshot(scene, "inbox-$width")
            } finally { scene.close() }
        }
    }

    @Test fun filtersShowTheirOwnEmptyStateAndReturnToAll(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(390, 1000, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { NotificationInboxContent(listOf(announcement, follower), onBack = {}, onRefresh = {}, onDelete = {}) }
        }
        try {
            settle(scene); click(scene, "Friends"); settle(scene)
            assertTrue(nodes(scene).any { "Nara" in texts(it) }); assertFalse(nodes(scene).any { "Welcome to Gratify" in texts(it) })
            click(scene, "Music"); settle(scene); assertTrue(nodes(scene).any { "No notifications in this category" in texts(it) })
            click(scene, "Show all notifications"); settle(scene); assertTrue(nodes(scene).any { "Welcome to Gratify" in texts(it) })
        } finally { scene.close() }
    }

    @Test fun failedAnnouncementLinkShowsAddressAndCanBeRetried(): Unit = runBlocking(Dispatchers.Swing) {
        var failures = true; var opened = 0
        val scene = ImageComposeScene(390, 1000, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { NotificationInboxContent(listOf(announcement), onBack = {}, onRefresh = {}, onDelete = {},
                onOpenLink = { assertEquals("https://example.invalid/updates", it); if (failures) error("No browser") else opened++ }) }
        }
        try {
            settle(scene); click(scene, "Open announcement link"); settle(scene)
            assertTrue(nodes(scene).any { "https://example.invalid/updates" in texts(it) }); snapshot(scene, "inbox-link-failure")
            failures = false; click(scene, "Open announcement link"); settle(scene); assertEquals(1, opened)
            assertFalse(nodes(scene).any { "https://example.invalid/updates" in texts(it) })
        } finally { scene.close() }
    }

    @Test fun followBackWaitsForKnownStateAndConfirmation(): Unit = runBlocking(Dispatchers.Swing) {
        var ready by mutableStateOf(false); var following by mutableStateOf(emptySet<String>())
        var busy by mutableStateOf(emptySet<String>()); val requests = mutableListOf<String>()
        val scene = ImageComposeScene(390, 1000, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { NotificationInboxContent(listOf(follower), following, busy, ready, onBack = {}, onRefresh = {}, onDelete = {}, onFollowBack = { requests += it }) }
        }
        try {
            settle(scene); assertTrue(button(scene, "Following status unavailable").config.contains(SemanticsProperties.Disabled))
            ready = true; settle(scene); click(scene, "Follow back"); assertEquals(listOf(peer), requests)
            busy = setOf(peer); settle(scene); assertTrue(button(scene, "Following…").config.contains(SemanticsProperties.Disabled))
            busy = emptySet(); following = setOf(peer); settle(scene)
            assertTrue(nodes(scene).any { "Following" in texts(it) }); assertFalse(nodes(scene).any { "Follow back" in texts(it) })
        } finally { scene.close() }
    }

    @Test fun readFailureRetainsCardsAndDeviceSettingsAndRetryRemainReachable(): Unit = runBlocking(Dispatchers.Swing) {
        var retries = 0; var settings = 0
        val scene = ImageComposeScene(390, 1600, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { NotificationInboxContent(listOf(announcement), readFailed = true, notificationsEnabled = false,
                syncProblems = setOf(NotificationSyncProblem.SOCIAL), onBack = {}, onRefresh = { retries++ },
                onSettings = { settings++ }, onDelete = {}) }
        }
        try {
            settle(scene); assertTrue(nodes(scene).any { "Welcome to Gratify" in texts(it) })
            assertTrue(nodes(scene).any { "Friends messages and stories could not be checked." in texts(it) })
            click(scene, "Try again"); click(scene, "Android notification settings"); assertEquals(1, retries); assertEquals(1, settings)
            snapshot(scene, "inbox-recovery")
        } finally { scene.close() }
    }

    @Test fun loadingAndDeletionPendingDisableDuplicateRequests(): Unit = runBlocking(Dispatchers.Swing) {
        var rows by mutableStateOf<List<NotificationEntity>?>(null); var deleting by mutableStateOf(emptySet<Long>())
        val scene = ImageComposeScene(390, 1000, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { NotificationInboxContent(rows, deleting = deleting, onBack = {}, onRefresh = {}, onDelete = {}) }
        }
        try {
            settle(scene); assertTrue(nodes(scene).any { "Loading notifications…" in texts(it) })
            rows = listOf(announcement); deleting = setOf(1); settle(scene)
            assertTrue(nodes(scene).any { it.config.contains(SemanticsProperties.Disabled) && it.boundsInRoot.height >= 48 })
            rows = emptyList(); deleting = emptySet(); settle(scene); assertTrue(nodes(scene).any { "No notifications yet" in texts(it) })
        } finally { scene.close() }
    }
}
