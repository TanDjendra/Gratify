package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.search.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SearchRenderTest {
    private var frame = 0L
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun has(scene: ImageComposeScene, text: String) = nodes(scene).any { text in texts(it) }
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).first {
        (label in texts(it) || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true) && it.config.contains(SemanticsActions.OnClick)
    }
    private fun click(scene: ImageComposeScene, label: String) = action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(scene: ImageComposeScene) { repeat(15) { frame += 16_000_000; scene.render(frame).close(); delay(10) } }
    private fun snapshot(scene: ImageComposeScene, name: String, directory: String = "search-2026-10-09") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val file = root.resolve("artifacts/$directory/$name.png"); Files.createDirectories(file.parent)
        frame += 16_000_000; scene.render(frame).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
    }
    @Test fun controlsHaveExplicitSubmitClearImeAndWorkingFilters(): Unit = runBlocking(Dispatchers.Swing) {
        var query by mutableStateOf(""); var type by mutableStateOf(SearchType.ALL); var submitted = 0
        val scene = ImageComposeScene(390, 500, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            SearchControls(query, type, { query = it }, { submitted++ }, { type = it })
        } }
        try {
            settle(scene); assertTrue(action(scene, "Search now").config.contains(SemanticsProperties.Disabled))
            val input = nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
            nodes(scene).first { it.config.contains(SemanticsActions.OnImeAction) }.config[SemanticsActions.OnImeAction].action!!.invoke(); assertEquals(0, submitted)
            input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(" @ ")); settle(scene)
            assertTrue(action(scene, "Search now").config.contains(SemanticsProperties.Disabled))
            nodes(scene).first { it.config.contains(SemanticsActions.OnImeAction) }.config[SemanticsActions.OnImeAction].action!!.invoke(); assertEquals(0, submitted)
            input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Nara")); settle(scene)
            click(scene, "Search now"); assertEquals(1, submitted)
            nodes(scene).first { it.config.contains(SemanticsActions.OnImeAction) }.config[SemanticsActions.OnImeAction].action!!.invoke(); assertEquals(2, submitted)
            click(scene, "Songs"); settle(scene); assertEquals(SearchType.SONGS, type)
            click(scene, "Clear search"); settle(scene); assertEquals("", query)
            input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("a".repeat(240))); settle(scene); assertEquals(200, query.length)
        } finally { scene.close() }
    }
    @Test fun externalFilterSelectionStaysFullyVisibleOnSmallLargeTextAndWideScreens(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(320 to 1.5f, 320 to 2f, 1100 to 1f)) {
            var type by mutableStateOf(SearchType.ALL)
            val scene = ImageComposeScene(width, 240, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme {
                SearchControls("Nara", type, {}, {}, { type = it })
            } }
            suspend fun checkSelected(label: String) {
                repeat(3) { settle(scene) }
                val chip = action(scene, label)
                assertTrue(chip.config[SemanticsProperties.Selected])
                assertTrue(chip.boundsInRoot.width >= chip.size.width - 1f, "$label is clipped at $width/$scale")
                assertTrue(chip.boundsInRoot.height >= 48f)
                assertTrue(chip.boundsInRoot.left >= 16f && chip.boundsInRoot.right <= width - 16f)
                assertEquals(1, nodes(scene).count { it.config.getOrNull(SemanticsProperties.Selected) == true })
            }
            try {
                settle(scene)
                type = SearchType.USERS; checkSelected("Users")
                type = SearchType.FEATURED_PLAYLISTS; checkSelected("Featured Playlists")
                type = SearchType.ALL; checkSelected("All")
                snapshot(scene, "capsules-$width-$scale", "search-capsules-2026-10-09")
            } finally { scene.close() }
        }
    }
    @Test fun historySubmitEditIndividualDeleteAndConfirmClearWork(): Unit = runBlocking(Dispatchers.Swing) {
        var values by mutableStateOf(listOf("Nara", "Ari")); var submitted = ""; var filled = ""; var clearCalls = 0
        val scene = ImageComposeScene(390, 1000, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            SearchQueryList(values, true, onSubmit = { submitted = it }, onFill = { filled = it }, onRemove = { values = values - it }, onClear = { clearCalls++; values = emptyList() })
        } }
        try {
            settle(scene); click(scene, "Nara"); assertEquals("Nara", submitted)
            click(scene, "Edit query"); assertEquals("Nara", filled); click(scene, "Remove recent search"); settle(scene); assertFalse(has(scene, "Nara"))
            click(scene, "Clear search history"); settle(scene); assertTrue(has(scene, "Clear recent searches?")); click(scene, "Cancel"); settle(scene); assertEquals(0, clearCalls)
            click(scene, "Clear search history"); settle(scene)
            nodes(scene).last { "Clear search history" in texts(it) && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
            settle(scene); assertEquals(1, clearCalls); assertTrue(has(scene, "Your next discovery starts here")); snapshot(scene, "history-empty")
        } finally { scene.close() }
    }
    @Test fun historyAndSuggestionsFitSmallLargeTextAndWideScreens(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            val scene = ImageComposeScene(width, 1100, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme { Column {
                SearchControls("Musik untuk perjalanan pulang", SearchType.ALL, {}, {}, {})
                Box(Modifier.weight(1f)) { SearchQueryList(listOf("Musik untuk perjalanan pulang yang tenang", "Nara", "Indie Indonesia"), true, onSubmit = {}, onFill = {}) }
            } } }
            try {
                settle(scene); snapshot(scene, "history-$width")
                assertTrue(action(scene, "Search now").boundsInRoot.height >= 48)
                val rowActions = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { label -> label == "Edit query" || label == "Remove recent search" } == true }
                assertTrue(rowActions.isNotEmpty()); assertTrue(rowActions.all { it.boundsInRoot.height >= 48 && it.boundsInRoot.width >= 48 })
                assertTrue(nodes(scene).filter { texts(it).isNotEmpty() && it.boundsInRoot.width > 0 }.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width })
            } finally { scene.close() }
        }
        val suggestions = ImageComposeScene(390, 1100, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            SearchQueryList(listOf("Nara", "Nara acoustic"), false, problem = true, onSubmit = {}, onFill = {}, recommendations = { item { Text("Recommended music") } })
        } }
        try { settle(suggestions); assertTrue(has(suggestions, "Recommended music")); assertTrue(has(suggestions, "Nara")); snapshot(suggestions, "suggestions") }
        finally { suggestions.close() }
    }
    @Test fun loadingFailureNoMatchAndPeoplePromptHaveDistinctActions(): Unit = runBlocking(Dispatchers.Swing) {
        var phase by mutableStateOf<SearchScreenUIState>(SearchScreenUIState.Empty); var type by mutableStateOf(SearchType.USERS)
        var retry = 0; var all = 0
        val scene = ImageComposeScene(320, 1000, Density(1f, 1.5f), coroutineContext = coroutineContext) { AppTheme {
            SearchResultStatus(phase, "Nara", type, onRetry = { retry++ }, onAll = { all++ })
        } }
        try {
            settle(scene); assertTrue(has(scene, "Enter a name to find a Gratify user.")); snapshot(scene, "people-prompt-320")
            phase = SearchScreenUIState.Loading; settle(scene); assertTrue(has(scene, "Looking for your next listen"))
            phase = SearchScreenUIState.Error; settle(scene); click(scene, "Retry"); assertEquals(1, retry); snapshot(scene, "error-320")
            phase = SearchScreenUIState.Success; type = SearchType.SONGS; settle(scene); assertTrue(has(scene, "No matches for “Nara”")); click(scene, "Search all music"); assertEquals(1, all); snapshot(scene, "no-match-320")
        } finally { scene.close() }
    }
    @Test fun busyHistoryDisablesDestructiveActionsAndFailureAllowsRetry(): Unit = runBlocking(Dispatchers.Swing) {
        var busy by mutableStateOf(true); var retries = 0
        val scene = ImageComposeScene(390, 900, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            SearchQueryList(listOf("Nara"), true, busy = busy, problem = true, onSubmit = {}, onFill = {}, onRetry = { retries++ })
        } }
        try {
            settle(scene); assertTrue(action(scene, "Remove recent search").config.contains(SemanticsProperties.Disabled))
            assertTrue(action(scene, "Clear search history").config.contains(SemanticsProperties.Disabled))
            busy = false; settle(scene); click(scene, "Retry"); assertEquals(1, retries)
        } finally { scene.close() }
    }
}
