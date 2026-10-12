package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.domain.data.model.searchResult.songs.*
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.gratify.ui.screen.search.*
import com.tan.gratify.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SearchCardsTest {
    private var frame = 0L
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun labels(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).first { label in labels(it) && it.config.contains(SemanticsActions.OnClick) }
    private suspend fun settle(scene: ImageComposeScene) { repeat(15) { frame += 16_000_000; scene.render(frame).close(); delay(10) } }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val file = root.resolve("artifacts/search-cards-2026-10-09/$name.png"); Files.createDirectories(file.parent)
        frame += 16_000_000; scene.render(frame).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
    }

    @Test fun sparseMetadataDoesNotInventCreatorsAndKeepsPodcastType() {
        val song = SongsResult(null, listOf(Artist(null, " "), Artist("a", " Nara ")), null, " 4:12 ", null, null, true, "Song",
            listOf(Thumbnail(1, "https://example.com/cover.jpg", 1), Thumbnail(1, "", 1)), null, "id", null, "")
        val info = song.searchCardInfo()
        assertEquals("", info.title); assertEquals("Nara", info.creator); assertEquals("4:12", info.detail)
        assertEquals("https://example.com/cover.jpg", info.artwork); assertTrue(info.explicit); assertTrue(info.playable)
        val podcast = PlaylistsResult(" ", "pod", "", "10", "Podcast", emptyList(), " Talk ").searchCardInfo()
        assertEquals(SearchCardKind.PODCAST, podcast.kind); assertEquals("Talk", podcast.title)
        assertNull(podcast.creator); assertFalse(podcast.playable)
    }

    @Test fun queueAndMoreHaveSeparateAccessibleTargetsAndDoNotOpenTrack(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(320 to 1.5f, 320 to 2f, 390 to 1f, 1100 to 1f)) {
            var played = 0; var queued = 0; var more = 0
            val scene = ImageComposeScene(width, 600, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme {
                SearchResultCard(SearchCardInfo("id", "Nara", SearchCardKind.SONG, "Ari & Nara", "4:12", explicit = true),
                    isCurrent = true, onClick = { played++ }, onQueue = { queued++ }, onMore = { more++ })
            } }
            try {
                settle(scene)
                for (label in listOf("Add Nara to queue", "More options for Nara")) {
                    val node = action(scene, label)
                    assertTrue(node.boundsInRoot.width >= 48f && node.boundsInRoot.height >= 48f, "$label at $width/$scale")
                    assertTrue(node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= width)
                    node.config[SemanticsActions.OnClick].action!!.invoke()
                }
                assertEquals(1, queued); assertEquals(1, more); assertEquals(0, played)
                action(scene, "Nara").config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, played)
                assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.StateDescription) == "Current track" })
                assertTrue(nodes(scene).any { "Song · 4:12 · Explicit" in labels(it) })
                snapshot(scene, "result-$width-$scale")
            } finally { scene.close() }
        }
    }

    @Test fun incompleteTargetsCannotNavigateOrQueue(): Unit = runBlocking(Dispatchers.Swing) {
        var calls = 0
        val scene = ImageComposeScene(320, 600, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            SearchResultCard(SearchCardInfo("", "", SearchCardKind.SONG), onClick = { calls++ }, onQueue = { calls++ }, onMore = { calls++ })
        } }
        try {
            settle(scene)
            val targets = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
            assertTrue(targets.size >= 3); assertTrue(targets.all { it.config.contains(SemanticsProperties.Disabled) })
            assertEquals(0, calls); assertTrue(nodes(scene).any { "Untitled" in labels(it) })
        } finally { scene.close() }
    }

    @Test fun browseTitlesHaveTheirOwnSpaceAndGridAdaptsToTextAndWidth(): Unit = runBlocking(Dispatchers.Swing) {
        assertEquals(1, searchBrowseColumns(288.dp, 1f)); assertEquals(2, searchBrowseColumns(358.dp, 1f))
        assertEquals(1, searchBrowseColumns(358.dp, 1.5f)); assertEquals(4, searchBrowseColumns(1068.dp, 1f))
        for ((width, scale) in listOf(320 to 1.5f, 390 to 1f, 1100 to 1f)) {
            var opened = 0
            val title = "Musik untuk perjalanan pulang yang tenang"
            val scene = ImageComposeScene(width, 1200, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoxWithConstraints {
                        val columns = searchBrowseColumns(maxWidth, scale)
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(columns) { index -> SearchBrowseCard(if (index == 0) title else "Indie Indonesia",
                                Brush.linearGradient(GratifyColors.BrowseArtwork.take(2)), modifier = Modifier.weight(1f).fillMaxHeight(), subtitle = "Ari & Nara", onClick = { opened++ }) }
                        }
                    }
                    SearchPersonCard("user", "Ari dengan nama profil yang sangat panjang", null) { opened++ }
                    SearchResultCard(SearchCardInfo("pod", "Cerita sebelum tidur", SearchCardKind.PODCAST), onClick = { opened++ })
                }
            } }
            try {
                settle(scene); val card = action(scene, title)
                assertTrue(card.boundsInRoot.height >= 156f)
                val browseCards = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && (title in labels(it) || "Indie Indonesia" in labels(it)) }
                assertEquals(1, browseCards.map { it.boundsInRoot.height }.distinct().size, "Uneven grid at $width/$scale")
                card.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, opened)
                assertTrue(nodes(scene).filter { labels(it).isNotEmpty() && it.boundsInRoot.width > 0 }.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width })
                // The whole card merges its text for accessibility; its children aren't separate nodes.
                assertTrue(title in labels(card))
                assertFalse(nodes(scene).any { label -> labels(label).any { it.startsWith("#") || it == "Unknown" } })
                snapshot(scene, "browse-$width-$scale")
            } finally { scene.close() }
        }
    }
}
