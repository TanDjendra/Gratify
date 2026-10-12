package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LibraryCollectionRenderTest {
    private fun item(id: String, title: String, artist: Boolean = false, pinned: Boolean = false, added: Long = 0) =
        LibraryItemModel(key = id, title = title, subtitle = if (artist) "Artis" else "24 lagu",
            placeholderRes = if (artist) Res.drawable.holder else Res.drawable.round_library_music_24,
            gradientColors = listOf(GratifyColors.SurfaceRaised, GratifyColors.SurfaceHighest),
            onClick = {}, type = if (artist) "Artis" else "Playlist", isCircle = artist, canLongPress = !artist,
            isPinned = pinned, addedAt = added)

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    private fun find(scene: ImageComposeScene, vararg labels: String) = nodes(scene).first { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it in labels } == true ||
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text in labels } == true
    }
    private fun click(scene: ImageComposeScene, vararg labels: String) {
        var node = find(scene, *labels)
        while (!node.config.contains(SemanticsActions.OnClick)) node = node.parent!!
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private suspend fun settle(scene: ImageComposeScene) {
        repeat(20) { scene.render(it * 16_000_000L).close(); delay(16) }
    }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/library-screen-audit-2026-10-08/$name")
        Files.createDirectories(path.parent)
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { Files.write(path, it.bytes) } }
    }

    @Test fun mobileFiltersLayoutShortcutsAndOptionsRespondWithDuplicateTitles() = runBlocking(Dispatchers.Swing) {
        var filter by mutableStateOf<String?>(null)
        var grid by mutableStateOf(false)
        var liked = 0
        var downloaded = 0
        var history = 0
        var options: String? = null
        var creates = 0
        var adds = 0
        var sorts = 0
        // Equal titles/covers are valid: identity comes from IDs, never display text.
        val entries = listOf(item("local-1", "Perjalanan pulang", pinned = true), item("local-2", "Perjalanan pulang"),
            item("artist-1", "Senja", artist = true), item("artist-2", "Senja", artist = true))
        val scene = ImageComposeScene(780, 1680, Density(2f), coroutineContext = coroutineContext) {
            AppTheme {
                Column(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                    Text("Pustaka", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(16.dp))
                    LibraryCollectionContent(items = entries.filter { filter == null || it.type == filter }, selectedFilter = filter,
                        isGridView = grid, sortOption = LibrarySortOption.TERAKHIR, onFilter = { filter = it },
                        onToggleLayout = { grid = !grid }, onSort = { sorts++ }, onCreatePlaylist = { creates++ },
                        onAddArtist = { adds++ }, onLiked = { liked++ }, onDownloaded = { downloaded++ },
                        onRecentlyAdded = { history++ }, onOptions = { options = it.key })
                }
            }
        }
        try {
            settle(scene)
            snapshot(scene, "library-mobile-list.png")
            assertTrue(find(scene, "Default order", "Urutan awal").boundsInRoot.top >=
                find(scene, "Your collection", "Koleksimu").boundsInRoot.bottom)
            click(scene, "Liked songs", "Lagu disukai")
            click(scene, "Downloaded Music", "Musik Diunduh")
            click(scene, "Recently Added", "Baru ditambahkan")
            assertEquals(listOf(1, 1, 1), listOf(liked, downloaded, history))
            click(scene, "Default order", "Urutan awal"); assertEquals(1, sorts)
            click(scene, "Create", "Buat"); assertEquals(1, creates)
            click(scene, "Options for Perjalanan pulang", "Opsi untuk Perjalanan pulang"); assertEquals("local-1", options)
            click(scene, "Show as grid", "Tampilan grid"); settle(scene); assertTrue(grid)
            assertTrue(find(scene, "Default order", "Urutan awal").boundsInRoot.top >=
                find(scene, "Your collection", "Koleksimu").boundsInRoot.bottom)
            snapshot(scene, "library-mobile-grid.png")
            click(scene, "Artists", "Artis"); settle(scene); assertEquals("Artis", filter)
            assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Liked songs" || t.text == "Lagu disukai" } == true })
            click(scene, "Add artist", "Tambah artis"); assertEquals(1, adds)
            snapshot(scene, "library-artists.png")
        } finally { scene.close() }
    }

    @Test fun narrowEmptyLoadingAndFailureStatesOfferTheRightAction() = runBlocking(Dispatchers.Swing) {
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var creates = 0
        var retries = 0
        val scene = ImageComposeScene(320, 800, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                LibraryCollectionContent(items = emptyList(), selectedFilter = null, isGridView = false,
                    sortOption = LibrarySortOption.TERAKHIR, onFilter = {}, onToggleLayout = {}, onSort = {},
                    onCreatePlaylist = { creates++ }, onAddArtist = {}, onLiked = {}, onDownloaded = {}, onRecentlyAdded = {},
                    onOptions = {}, loading = loading, hasError = failed, onRetry = { retries++ })
            }
        }
        try {
            settle(scene)
            val action = find(scene, "Create playlist", "Buat playlist")
            assertTrue(action.boundsInRoot.left >= 0 && action.boundsInRoot.right <= 320)
            click(scene, "Create playlist", "Buat playlist"); assertEquals(1, creates)
            snapshot(scene, "library-empty.png")
            loading = true; settle(scene)
            find(scene, "Loading your collection", "Memuat koleksi")
            snapshot(scene, "library-loading.png")
            loading = false; failed = true; settle(scene)
            click(scene, "Retry", "Coba lagi"); assertEquals(1, retries)
            snapshot(scene, "library-error.png")
        } finally { scene.close() }
    }

    @Test fun desktopGridStaysConstrainedAndPinnedItemsLeadEachSort() = runBlocking(Dispatchers.Swing) {
        val entries = listOf(item("1", "Zebra", pinned = true, added = 1), item("2", "Beta", added = 30), item("3", "Alpha", added = 10))
        assertEquals(listOf("1", "3", "2"), sortLibraryItems(entries, LibrarySortOption.ABJAD).map { it.key })
        assertEquals(listOf("1", "2", "3"), sortLibraryItems(entries, LibrarySortOption.BARU_DITAMBAHKAN).map { it.key })
        val scene = ImageComposeScene(1500, 850, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                LibraryCollectionContent(items = entries, selectedFilter = null, isGridView = true,
                    sortOption = LibrarySortOption.BARU_DITAMBAHKAN, onFilter = {}, onToggleLayout = {}, onSort = {},
                    onCreatePlaylist = {}, onAddArtist = {}, onLiked = {}, onDownloaded = {}, onRecentlyAdded = {}, onOptions = {})
            }
        }
        try {
            settle(scene)
            val option = find(scene, "Options for Zebra", "Opsi untuk Zebra")
            assertTrue(option.boundsInRoot.left >= 220f && option.boundsInRoot.right <= 1280f)
            snapshot(scene, "library-desktop.png")
        } finally { scene.close() }
    }

}
