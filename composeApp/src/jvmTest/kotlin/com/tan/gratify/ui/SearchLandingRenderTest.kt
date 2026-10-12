package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import com.tan.domain.data.model.mood.*
import com.tan.gratify.ui.screen.search.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SearchLandingRenderTest {
    private var frame = 0L
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun labels(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private fun click(s: ImageComposeScene, text: String) = nodes(s).first { text in labels(it) && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(s: ImageComposeScene) { repeat(20) { frame += 16_000_000; s.render(frame).close(); delay(12) } }
    private fun snapshot(s: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val file = root.resolve("artifacts/search-landing-2026-10-09/$name.png"); Files.createDirectories(file.parent)
        frame += 16_000_000; s.render(frame).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
    }

    @Test fun browseDestinationsUseOnlyValidServerIdsAndKeepGenrePriority() {
        val mood = Mood(arrayListOf(Genre("pop-real", "Pop"), Genre("", "Fake genre"), Genre("empty", " ")),
            arrayListOf(MoodsMoment("pop-real", "Duplicate"), MoodsMoment("focus-real", "Focus"), MoodsMoment(" ", "No destination")))
        assertEquals(listOf(SearchCategoryEntry("pop-real", "Pop"), SearchCategoryEntry("focus-real", "Focus")), searchBrowseCategories(mood))
        assertEquals(emptyList(), searchBrowseCategories(null))
        assertEquals(SearchCoverTheme.POP, searchCoverTheme("Musik"))
        assertNotEquals(searchCategoryColor("K-pop"), searchCategoryColor("Pop"))
        assertEquals(searchCategoryColor("1"), searchCategoryColor("1"))
    }

    @Test fun landingFieldKeepsFocusRequesterAndSourceSelectionThenSupportsTypingSubmitAndClear(): Unit = runBlocking(Dispatchers.Swing) {
        var query by mutableStateOf(""); var type by mutableStateOf(SearchType.ALL)
        var focused by mutableStateOf(false); var submitted = 0
        val requester = FocusRequester()
        val scene = ImageComposeScene(390, 800, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            Column {
                SearchControls(query, type, { query = it }, { submitted++ }, { type = it }, requester, { focused = it }, landing = query.isEmpty() && !focused)
                SearchCategoryRow(listOf(SearchCategoryEntry("pod", "Podcasts")), 2, true) { type = SearchType.PODCASTS; requester.requestFocus() }
            }
        } }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { "Songs, artists, playlists" in labels(it) })
            assertFalse(nodes(scene).any { "Search now" in labels(it) })
            assertFalse(nodes(scene).any { it.config.contains(SemanticsProperties.Selected) })
            assertEquals(1, nodes(scene).count { it.config.contains(SemanticsActions.SetText) })
            snapshot(scene, "landing-input")
            click(scene, "Podcasts"); settle(scene)
            assertTrue(focused); assertEquals(SearchType.PODCASTS, type)
            assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Selected) == true && "Podcasts" in labels(it) })
            nodes(scene).first { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Nara"))
            settle(scene); click(scene, "Search now"); assertEquals(1, submitted)
            click(scene, "Clear search"); settle(scene); assertEquals("", query)
        } finally { scene.close() }
    }

    @Test fun categoryGridKeepsReadableTitlesEqualRowsAndIndependentTargetsAtAdaptiveWidths(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale, expectedColumns) in listOf(Triple(390, 1f, 2), Triple(320, 1.5f, 1), Triple(1100, 1f, 4))) {
            val columns = searchBrowseColumns((width - 32).dp, scale); assertEquals(expectedColumns, columns)
            val entries = listOf("Pop", "Indie Indonesia", "Musik untuk perjalanan pulang yang tenang", "Fokus & belajar").map { SearchCategoryEntry(it, it) }
            var selected = ""
            val scene = ImageComposeScene(width, 1200, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme {
                Column(Modifier.padding(16.dp)) { entries.chunked(columns).forEach { row -> SearchCategoryRow(row, columns) { selected = it.id } } }
            } }
            try {
                settle(scene)
                val buttons = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
                assertEquals(4, buttons.size)
                buttons.forEach { n ->
                    assertTrue(n.boundsInRoot.width >= 48 && n.boundsInRoot.height >= 104)
                    assertTrue(n.boundsInRoot.left >= 16 && n.boundsInRoot.right <= width - 16)
                    assertTrue(n.boundsInRoot.bottom <= 1200)
                }
                buttons.chunked(columns).forEach { row -> assertTrue(row.all { kotlin.math.abs(it.boundsInRoot.height - row.first().boundsInRoot.height) < 1 }) }
                click(scene, "Indie Indonesia"); assertEquals("Indie Indonesia", selected)
                click(scene, "Fokus & belajar"); assertEquals("Fokus & belajar", selected)
                snapshot(scene, "categories-$width-$scale")
            } finally { scene.close() }
        }
    }

    @Test fun compactFieldCollapsesAndExternalFocusRestoresItWithoutReplacingInput(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f)) {
            var shown by mutableStateOf(true); var focused by mutableStateOf(false)
            var query by mutableStateOf(""); val requester = FocusRequester()
            val scene = ImageComposeScene(width, 900, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme {
                Column {
                    SearchScrollingControls(shown || focused) {
                        SearchControls(query, SearchType.ALL, { query = it }, {}, {}, requester, { focused = it }, landing = !focused)
                    }
                    androidx.compose.material3.Text("Browse marker")
                }
            } }
            try {
                settle(scene)
                val editor = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
                assertTrue(editor.boundsInRoot.height in 48f..64f)
                val before = nodes(scene).first { "Browse marker" in labels(it) }.boundsInRoot.top
                shown = false; settle(scene)
                assertEquals(0f, nodes(scene).first { "Browse marker" in labels(it) }.boundsInRoot.top)
                assertTrue(before >= 64)
                requester.requestFocus(); settle(scene)
                assertTrue(focused)
                val restored = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
                assertTrue(restored.boundsInRoot.height >= 48)
                restored.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Nara")); settle(scene)
                assertEquals("Nara", query)
            } finally { scene.close() }
        }
    }

    @Test fun publicCardRetainsCreatorAndPlaylistDestinationAtLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        var selected = ""
        val scene = ImageComposeScene(320, 600, Density(1f, 1.5f), coroutineContext = coroutineContext) { AppTheme {
            Column(Modifier.padding(16.dp)) {
                SearchCategoryRow(listOf(SearchCategoryEntry("shared-123", "Playlist sore", subtitle = "Ari & Nara")), 1) { selected = it.id }
            }
        } }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { "Ari & Nara" in labels(it) })
            val target = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) }
            assertTrue(target.boundsInRoot.width >= 288 && target.boundsInRoot.height >= 104)
            click(scene, "Playlist sore"); assertEquals("shared-123", selected)
        } finally { scene.close() }
    }
}
