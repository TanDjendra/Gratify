package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.model.home.Content
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.screen.home.QuickPicksContent
import com.tan.gratify.ui.screen.friends.FriendListItem
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.FriendActivityItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*
import com.tan.domain.data.model.home.HomeItem as HomeSection

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
class MusicRefreshRenderTest {
    private fun output(name: String): Path {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        return root.resolve("artifacts/music-quality/$name").also { Files.createDirectories(it.parent) }
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun all(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    private suspend fun settle(scene: ImageComposeScene) {
        repeat(20) { scene.render(it * 16_000_000L).close(); delay(16) }
    }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        scene.render(1_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(output(name), data.bytes) } }
    }

    @Test fun quickPicksRespondAtPhoneAndTabletWidthsWithLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        for (width in listOf(390, 900)) {
            var played = 0
            val content = (1..8).map { Content(null, null, null, false, null, null, emptyList(),
                "Perjalanan pulang yang selalu menemanimu $it", "sample-$it", null) }
            val scene = ImageComposeScene(width, 860, Density(1f, 1.3f), coroutineContext = coroutineContext) {
                AppTheme {
                    Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(16.dp)) {
                        QuickPicksContent(HomeSection(content, "Pilihan cepat"), onPlay = { played++ })
                    }
                }
            }
            try {
                settle(scene)
                val buttons = all(scene).filter { it.config.contains(SemanticsActions.OnClick) }
                assertEquals(8, buttons.size)
                assertTrue(buttons.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width })
                buttons.first().config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1, played)
                snapshot(scene, "quick-picks-$width.png")
            } finally { scene.close() }
        }
    }

    @Test fun friendProfileAndSongActionsRemainSeparate(): Unit = runBlocking(Dispatchers.Swing) {
        var profiles = 0
        var plays = 0
        val friend = FriendActivityItem(UserProfile(id = "preview", displayName = "Nara Prameswari"),
            true, "Ruang tenang untuk perjalanan pulang", "Senja & Langit Kota", "", "Sedang mendengarkan")
        val scene = ImageComposeScene(390, 720, Density(1f, 1.3f), coroutineContext = coroutineContext) {
            AppTheme {
                Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(16.dp)) {
                    HomeSectionHeading("Sedang mendengarkan", "Aktivitas temanmu")
                    Spacer(Modifier.height(20.dp))
                    FriendListItem(friend.copy(videoId = "preview"), { profiles++ }, { plays++ })
                    Spacer(Modifier.height(16.dp))
                    FriendListItem(friend.copy(isOnline = false, lastActiveText = "Aktif kemarin"), { profiles++ }, { plays++ })
                }
            }
        }
        try {
            settle(scene)
            val play = all(scene).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { label -> label.startsWith("Putar ") } == true }
            play.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, plays)
            assertEquals(0, profiles)
            snapshot(scene, "friends-large-text.png")
        } finally { scene.close() }
    }

    @Test fun lyricsAndRecoveryActionsRender(): Unit = runBlocking(Dispatchers.Swing) {
        var retries = 0
        val scene = ImageComposeScene(390, 780, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                Column(Modifier.fillMaxSize().background(GratifyColors.AccentContainer).padding(24.dp)) {
                    Text("Lirik", style = typo().titleLarge)
                    Spacer(Modifier.height(24.dp))
                    LyricsLineItem("Pelan-pelan kita berjalan", null, true, false)
                    LyricsLineItem("Menemukan ruang untuk pulang", "Finding a place to call home", true, true)
                    LyricsLineItem("Di antara suara kota", null, false, false)
                    Spacer(Modifier.height(24.dp))
                    DiscoveryFooter(false, true, false) { retries++ }
                }
            }
        }
        try {
            settle(scene)
            all(scene).first { it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, retries)
            snapshot(scene, "lyrics-and-retry.png")
        } finally { scene.close() }
    }
}
