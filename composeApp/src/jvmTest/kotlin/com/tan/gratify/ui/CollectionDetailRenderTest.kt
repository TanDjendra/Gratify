package com.tan.gratify.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.domain.data.entities.SharedPlaylistTrack
import com.tan.domain.repository.SongRepository
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.screen.playlist.toTrack
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.*

/** Renders the actual detail components without a device, remote service or private account data. */
@OptIn(ExperimentalComposeUiApi::class)
class CollectionDetailRenderTest {
    private fun output(name: String): Path {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        return root.resolve("artifacts/playlist-redesign/$name").also { Files.createDirectories(it.parent) }
    }

    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

    private fun find(scene: ImageComposeScene, vararg labels: String): SemanticsNode =
        scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.first {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { label -> label in labels } == true
        }

    private suspend fun settle(scene: ImageComposeScene) {
        repeat(40) { scene.render(it * 16_000_000L).close(); delay(16) }
    }

    private fun snapshot(scene: ImageComposeScene, name: String) {
        val image = scene.render(1_000_000_000L)
        try { image.encodeToData()!!.use { Files.write(output(name), it.bytes) } } finally { image.close() }
    }

    @Composable
    private fun Artwork(modifier: Modifier) {
        Canvas(modifier) {
            drawRect(Color(0xFF254F42))
            drawCircle(Color(0xFF80B99B), size.width * 0.35f)
            drawCircle(Color(0xFF254F42), size.width * 0.225f)
            listOf(0.12f, 0.2f, 0.32f, 0.52f, 0.32f, 0.2f, 0.12f).forEachIndexed { index, height ->
                val x = size.width * (0.2f + index * 0.1f)
                drawRect(Color(0xFFDFEAD5), Offset(x, size.height * (0.5f - height / 2)), androidx.compose.ui.geometry.Size(size.width * 0.04f, size.height * height))
            }
        }
    }

    private fun withSongs(block: () -> Unit) {
        val repository = Proxy.newProxyInstance(SongRepository::class.java.classLoader, arrayOf(SongRepository::class.java)) { _, method, _ ->
            if (method.name == "getSongAsFlow") flowOf(null) else error("Unexpected repository call: ${method.name}")
        } as SongRepository
        startKoin { modules(module { single<SongRepository> { repository } }) }
        try { block() } finally { stopKoin() }
    }

    @Test
    fun mobileControlsRespondAndRenderWithProductionSongRows() = withSongs {
        runBlocking(Dispatchers.Swing) {
            var playing by mutableStateOf(false)
            var saved by mutableStateOf(false)
            var playCount = 0
            val scene = ImageComposeScene(780, 1680, Density(2f), coroutineContext = coroutineContext) {
                AppTheme {
                    LazyColumn(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                        item {
                            CollectionDetailHeader(title = "Indie untukmu", kind = "Playlist", subtitle = "Gratify",
                                metadata = "24 lagu • 1 jam 28 menit", accent = Color(0xFF254F42), onBack = {},
                                onPlay = { playing = !playing; playCount++ }, onShuffle = {}, isPlaying = playing, canPlay = true,
                                artwork = { Artwork(it) }, actions = {
                                    CollectionSaveAction(saved, onClick = { saved = !saved })
                                    CollectionDownloadAction(0, onClick = {})
                                    CollectionDetailAction(Icons.Rounded.MoreHoriz, "More options", onClick = {})
                                })
                        }
                        listOf("Perjalanan pulang" to "Langit Kota", "Pelan-pelan" to "Sore", "Ruang tenang" to "Senja", "Hari yang baru" to "Nara").forEachIndexed { index, (title, artist) ->
                            item {
                                SongFullWidthItems(track = SharedPlaylistTrack(playlistId = "preview", videoId = "preview-$index", title = title,
                                    artists = artist).toTrack().copy(thumbnails = emptyList()), isPlaying = playing && index == 0,
                                    onClickListener = {}, onMoreClickListener = {}, modifier = Modifier.fillMaxWidth(), collectionStyle = true)
                            }
                        }
                    }
                }
            }
            try {
                settle(scene)
                find(scene, "Play", "Putar").config[SemanticsActions.OnClick].action!!.invoke()
                settle(scene)
                assertEquals(1, playCount)
                find(scene, "Pause", "Jeda")
                find(scene, "Save to your library", "Simpan ke koleksi").config[SemanticsActions.OnClick].action!!.invoke()
                settle(scene)
                assertTrue(saved)
                find(scene, "Remove from your library", "Hapus dari koleksi")
                snapshot(scene, "playlist-mobile.png")
            } finally { scene.close() }
        }
    }

    @Test
    fun wideAlbumAndLongTitleStayWithinTheViewport() = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(1100, 680, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                LazyColumn(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                    item {
                        CollectionDetailHeader(title = "Ruang untuk mendengarkan — perjalanan yang tidak terburu-buru", kind = "Album", subtitle = "Senja",
                            metadata = "2026 • 12 lagu • 42 menit", accent = Color(0xFF254F42), onBack = {}, onPlay = {}, onShuffle = {},
                            isPlaying = false, canPlay = true, artwork = { Artwork(it) },
                            actions = { CollectionSaveAction(false, onClick = {}); CollectionDownloadAction(0, onClick = {}) })
                    }
                }
            }
        }
        try {
            settle(scene)
            assertTrue(find(scene, "Play", "Putar").boundsInRoot.right <= 1100f)
            assertTrue(find(scene, "Save to your library", "Simpan ke koleksi").boundsInRoot.left >= 0f)
            snapshot(scene, "album-desktop.png")
        } finally { scene.close() }
    }

    @Test
    fun emptyCollectionDisablesPlaybackAndNarrowLayoutFits() = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(320, 780, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                LazyColumn(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                    item {
                        CollectionDetailHeader(title = "Playlist baru", kind = "Playlist", subtitle = "Koleksimu", metadata = "0 lagu",
                            accent = GratifyColors.SurfaceRaised, onBack = {}, onPlay = { error("Empty playlist must not play") },
                            onShuffle = {}, isPlaying = false, canPlay = false, artwork = { Artwork(it) },
                            actions = { CollectionSaveAction(false, onClick = {}); CollectionDownloadAction(0, enabled = false, onClick = {}) })
                        CollectionDetailEmpty()
                    }
                }
            }
        }
        try {
            settle(scene)
            assertTrue(find(scene, "Play", "Putar").config.contains(SemanticsProperties.Disabled))
            assertTrue(find(scene, "Play", "Putar").boundsInRoot.right <= 320f)
            snapshot(scene, "playlist-empty.png")
        } finally { scene.close() }
    }
}
