package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.domain.data.entities.UserProfile
import com.tan.gratify.ui.component.ScreenHeader
import com.tan.gratify.ui.component.BottomSafeContent
import com.tan.gratify.ui.screen.friends.FriendsActivityContent
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class FriendsRedesignRenderTest {
    private val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    private val friends = listOf(
        FriendActivityItem(UserProfile(id = "nara", displayName = "Nara Prameswari", note = "Lagu untuk perjalanan pulang", noteUpdatedAt = now.toString()),
            true, "Ruang tenang", "Senja & Langit Kota", "sample-nara", "Sedang memutar"),
        FriendActivityItem(UserProfile(id = "bima", displayName = "Bima Putra"),
            true, "Langkah baru", "Ruang Selatan", "sample-bima", "Sedang memutar"),
        FriendActivityItem(UserProfile(id = "sari", displayName = "Sari"),
            false, "Pagi di rumah", "Nadira", "sample-sari", "Offline"),
        FriendActivityItem(UserProfile(id = "dita", displayName = "Dita Maharani", note = "Catatan ini sudah kedaluwarsa", noteUpdatedAt = (now - 25 * 60 * 60 * 1000L).toString()),
            false, "Belum memutar lagu", "", "", "Offline"),
    )
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun all(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private suspend fun settle(scene: ImageComposeScene, start: Long = 0) {
        repeat(20) { scene.render(start + it * 16_000_000L).close(); delay(16) }
    }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/friends-redesign/$name.png")
        Files.createDirectories(path.parent)
        scene.render(2_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }

    @Test fun friendsFeedKeepsActionsSeparateAndLastProfileAbovePlayer(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 360 to 1.5f, 900 to 1.3f)) {
            var profile = ""
            var song = ""
            var edits = 0
            val showBottomOverlay = mutableStateOf(false)
            val scene = ImageComposeScene(width, 1000, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme {
                    Box(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                        BottomSafeContent(PaddingValues(top = 76.dp, bottom = 156.dp)) { padding ->
                            FriendsActivityContent(FriendsActivityState(friendsList = friends, myNote = "Hari ini ditemani musik santai", myNoteUpdatedAt = now.toString()),
                                "Alia", null, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                                onEditNote = { edits++ }, onProfile = { profile = it }, onPlay = { song = it },
                                onFindFriends = {}, onRetry = {})
                        }
                        ScreenHeader("Friends", "Alia", null, {})
                        if (showBottomOverlay.value) {
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(156.dp)
                                .background(GratifyColors.Navigation))
                        }
                    }
                }
            }
            try {
                settle(scene)
                val play = all(scene).first { description(it)?.startsWith("Putar Ruang tenang") == true }
                play.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("sample-nara", song)
                assertEquals("", profile)
                val avatar = all(scene).first { description(it)?.startsWith("Profil Nara") == true }
                avatar.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("nara", profile)
                all(scene).first { description(it) == "Edit catatan kamu" }
                    .config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1, edits)
                snapshot(scene, "friends-$width")
                showBottomOverlay.value = true
                all(scene).first { it.config.contains(SemanticsActions.ScrollBy) }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10000f)
                settle(scene, 3_000_000_000L)
                val last = all(scene).first { description(it) == "Lihat profil Dita Maharani" }
                assertTrue(last.boundsInRoot.bottom <= 844f, "Last friend covered by bottom chrome at $width")
                assertTrue(last.boundsInRoot.left >= 0 && last.boundsInRoot.right <= width)
                last.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("dita", profile)
                assertTrue(all(scene).any { text(it)?.contains("Catatan ini sudah kedaluwarsa") == true })
                assertFalse(all(scene).any { description(it)?.startsWith("Putar Belum memutar") == true })
            } finally { scene.close() }
        }
    }

    @Test fun emptyErrorAndLoadingKeepRecoveryAndNoteActionsAvailable(): Unit = runBlocking(Dispatchers.Swing) {
        val state = mutableStateOf(FriendsActivityState())
        var finds = 0
        var retries = 0
        var edits = 0
        val scene = ImageComposeScene(390, 900, Density(1f, 1.3f), coroutineContext = coroutineContext) {
            AppTheme {
                Box(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                    FriendsActivityContent(state.value, "Alia", null, onEditNote = { edits++ },
                        onProfile = {}, onPlay = {}, onFindFriends = { finds++ }, onRetry = { retries++ })
                }
            }
        }
        try {
            settle(scene)
            all(scene).first { text(it)?.contains("Temukan teman") == true && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, finds)
            snapshot(scene, "friends-empty")
            state.value = FriendsActivityState(error = "network")
            settle(scene, 3_000_000_000L)
            all(scene).first { text(it)?.contains("Coba lagi") == true && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, retries)
            state.value = FriendsActivityState(isLoading = true)
            settle(scene, 4_000_000_000L)
            assertTrue(all(scene).any { description(it) == "Memuat aktivitas teman" })
            all(scene).first { description(it) == "Edit catatan kamu" }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, edits)
        } finally { scene.close() }
    }
}
