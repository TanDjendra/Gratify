package com.tan.gratify.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SharedPlaylistTrack
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.domain.repository.SongRepository
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.screen.playlist.toTrack
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.viewModel.UIEvent
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.*

/** Renders production desktop components with clearly fictional, local data. */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopWorkspaceRenderTest {
    private fun output(name: String): Path {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        return root.resolve("artifacts/desktop-redesign/$name").also { Files.createDirectories(it.parent) }
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun find(scene: ImageComposeScene, vararg labels: String): SemanticsNode = scene.semanticsOwners
        .flatMap { nodes(it.rootSemanticsNode) }.filter { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it in labels } == true &&
                (node.config.contains(SemanticsActions.OnClick) || node.config.contains(SemanticsActions.SetProgress))
        }.maxBy { it.boundsInRoot.bottom }
    private var frameTime = 0L
    private suspend fun settle(scene: ImageComposeScene) { repeat(25) { frameTime += 16_000_000L; scene.render(frameTime).close(); delay(16) } }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        // Force a complete layout before exporting subcomposed desktop panels.
        // This also avoids retaining a partially repainted frame in the offscreen renderer.
        val size = scene.constraints
        scene.constraints = Constraints.fixed(size.maxWidth, size.maxHeight - 1)
        frameTime += 16_000_000L
        scene.render(frameTime).close()
        scene.constraints = size
        frameTime += 16_000_000L
        scene.render(frameTime).use { it.encodeToData()!!.use { data -> Files.write(output(name), data.bytes) } }
    }
    @Composable private fun Artwork(modifier: Modifier) {
        Canvas(modifier) {
            drawRect(Color(0xFF254F42)); drawCircle(Color(0xFF80B99B), size.width * .36f)
            drawCircle(Color(0xFF254F42), size.width * .24f)
            listOf(.12f, .2f, .32f, .52f, .32f, .2f, .12f).forEachIndexed { index, height ->
                drawRect(Color(0xFFDFEAD5), Offset(size.width * (.2f + index * .1f), size.height * (.5f - height / 2)),
                    androidx.compose.ui.geometry.Size(size.width * .04f, size.height * height))
            }
        }
    }
    private fun withSongs(block: () -> Unit) {
        val repo = Proxy.newProxyInstance(SongRepository::class.java.classLoader, arrayOf(SongRepository::class.java)) { _, method, _ ->
            if (method.name == "getSongAsFlow") flowOf(null) else error("Unexpected call: ${method.name}")
        } as SongRepository
        startKoin { modules(module { single<SongRepository> { repo } }) }
        try { block() } finally { stopKoin() }
    }

    @Test fun desktopPanelsTransportSeekingAndResizeUseTheActualComponents() = withSongs {
        runBlocking(Dispatchers.Swing) {
            val coverPath = output("demo-cover.png")
            val coverScene = ImageComposeScene(400, 400, Density(1f), coroutineContext = coroutineContext) { Artwork(Modifier.fillMaxSize()) }
            try { snapshot(coverScene, "demo-cover.png") } finally { coverScene.close() }
            val cover = coverPath.toUri().toString()
            val tracks = listOf("Perjalanan pulang" to "Langit Kota", "Pelan-pelan" to "Sore", "Ruang tenang" to "Senja", "Hari yang baru" to "Nara",
                "Jalan kecil" to "Langit Kota", "Kembali" to "Senja").mapIndexed { index, (title, artist) ->
                SharedPlaylistTrack(playlistId = "demo", videoId = "demo-$index", title = title, artists = artist)
                    .toTrack().copy(duration = "4:12", thumbnails = emptyList())
            }
            val player = NowPlayingScreenData("Indie untukmu", tracks[0].title, "Langit Kota", false, thumbnailURL = cover)
            val timeline = MutableStateFlow(TimeLine(90_000, 252_000, 80, loading = false))
            val playlists = listOf(LocalPlaylistEntity(1, "Indie untukmu", cover), LocalPlaylistEntity(2, "Santai sore", cover),
                LocalPlaylistEntity(3, "Perjalanan", cover), LocalPlaylistEntity(4, "Fokus", cover))
            for ((width, height) in listOf(1440 to 900, 1040 to 800, 900 to 600)) {
                var panel by mutableStateOf<Int?>(0)
                var controls by mutableStateOf(ControlState(true, false, RepeatState.None, false, true, true, false, .7f))
                val events = mutableListOf<UIEvent>()
                var playedIndex = -1
                var openedPlaylist = -1L
                var createCount = 0
                val scene = ImageComposeScene(width, height, Density(1f), coroutineContext = coroutineContext) {
                    AppTheme {
                        DesktopWorkspaceSurface(profileName = "Gratify", profileImage = null, selected = "home", playlists = playlists,
                            expandedLibrary = null, onExpandLibrary = {}, onCreatePlaylist = { createCount++ }, onPlaylist = { openedPlaylist = it.id },
                            onHome = {}, onSearch = {}, onLibrary = {}, onFriends = {}, onFavorite = {}, onDownloads = {},
                            onProfile = {}, onSettings = {}, onRecent = {}, onNotifications = {}, onBack = {}, canGoBack = true,
                            player = player, controls = controls, hasSong = true, timeline = timeline,
                            onPlayerEvent = { events += it; if (it == UIEvent.PlayPause) controls = controls.copy(isPlaying = !controls.isPlaying) },
                            panel = panel, onPanel = { panel = it }, queue = tracks, currentVideoId = tracks[0].videoId,
                            onQueueTrack = { playedIndex = it }, onVideo = {}, onMiniPlayer = {}) {
                            LazyColumn(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                                item { CollectionDetailHeader("Indie untukmu", "Playlist", "Gratify", "24 lagu • 1 jam 28 menit", Color(0xFF254F42),
                                    onBack = {}, onPlay = {}, onShuffle = {}, isPlaying = true, canPlay = true,
                                    artwork = { Artwork(it) }, actions = { CollectionSaveAction(false, onClick = {}); CollectionDownloadAction(0, onClick = {}) }) }
                                tracks.forEachIndexed { index, track -> item {
                                    SongFullWidthItems(track = track, isPlaying = index == 0, modifier = Modifier.fillMaxWidth(),
                                        onClickListener = {}, onMoreClickListener = {}, collectionStyle = true)
                                } }
                            }
                        }
                    }
                }
                try {
                    settle(scene)
                    Files.writeString(output("layout-$width.txt"), scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                        .filter { it.boundsInRoot.top >= height - 100 }.joinToString("\n") {
                            "${it.config.getOrNull(SemanticsProperties.ContentDescription)} ${it.config.getOrNull(SemanticsProperties.Text)} ${it.boundsInRoot}"
                        })
                    val seek = find(scene, "Seek in song", "Pindah posisi lagu")
                    assertTrue(seek.boundsInRoot.bottom <= height && seek.boundsInRoot.left >= 0 && seek.boundsInRoot.right <= width)
                    // Seek actions use 0..100 in the actual media service, not 0..1.
                    seek.config[SemanticsActions.SetProgress].action!!.invoke(.75f)
                    settle(scene)
                    assertEquals(75f, events.filterIsInstance<UIEvent.UpdateProgress>().last().newProgress)
                    val pause = find(scene, "Pause", "Jeda")
                    assertTrue(pause.boundsInRoot.bottom <= height && pause.boundsInRoot.right <= width,
                        "Footer transport must fit: ${pause.boundsInRoot}")
                    pause.config[SemanticsActions.OnClick].action!!.invoke()
                    settle(scene)
                    assertFalse(controls.isPlaying)
                    find(scene, "Play", "Putar").config[SemanticsActions.OnClick].action!!.invoke()
                    settle(scene)
                    assertTrue(controls.isPlaying)
                    snapshot(scene, "playlist-desktop-${width}x$height.png")
                    find(scene, "Queue", "Antrean").config[SemanticsActions.OnClick].action!!.invoke()
                    settle(scene)
                    assertEquals(1, panel)
                    find(scene, tracks[2].title).config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(2, playedIndex)
                    if (width == 1440) snapshot(scene, "queue-desktop.png")
                    find(scene, "Close song panel", "Tutup panel lagu").config[SemanticsActions.OnClick].action!!.invoke()
                    settle(scene)
                    assertNull(panel)
                    find(scene, "Create playlist", "Buat playlist").config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(1, createCount)
                    find(scene, playlists[1].title).config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(2, openedPlaylist)
                    if (width == 1440) snapshot(scene, "playlist-desktop-panel-closed.png")
                } finally { scene.close() }
            }
        }
    }
}
