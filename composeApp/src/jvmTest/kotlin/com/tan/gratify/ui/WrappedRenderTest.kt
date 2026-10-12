package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.home.wrapped.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WrappedRenderTest {
    private val sample = WrappedState(year = 2026, years = listOf(2026, 2025), loading = false, trackingEnabled = true,
        summary = WrappedSummary(minutes = 8420, plays = 2048, uniqueTracks = 312, activeDays = 186, totalSeconds = 505200,
            biggestDay = "2026-07-12", biggestDaySeconds = 10800, longestStreak = 24, clockBand = 3,
            monthlySeconds = listOf(42000, 61000, 36000, 54000, 25000, 72000, 45000, 41000, 9200, 20000, 0, 0)),
        tracks = listOf(WrappedRank("Menuju Cahaya", 124, "a", subtitle = "Nadira"), WrappedRank("Pelan-Pelan Pulang", 98, "b", subtitle = "Ari")),
        artists = listOf(WrappedRank("Nadira", 230, "n", 132000), WrappedRank("Ari", 198, "r", 101400)), playlistAvailable = true)
    private var frame = 0L
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun click(scene: ImageComposeScene, label: String) = nodes(scene).first {
        (label in texts(it) || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true) && it.config.contains(SemanticsActions.OnClick)
    }.config[SemanticsActions.OnClick].action!!.invoke()
    private fun has(scene: ImageComposeScene, text: String) = nodes(scene).any { text in texts(it) }
    private suspend fun settle(scene: ImageComposeScene) { repeat(20) { frame += 16_000_000; scene.render(frame).close(); delay(10) } }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val file = root.resolve("artifacts/wrapped-2026-10-08/$name.png"); Files.createDirectories(file.parent)
        frame += 16_000_000; scene.render(frame).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
    }
    @Test fun recapAndEveryStoryFitSmallLargeTextAndWideScreens(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            val scene = ImageComposeScene(width, 1400, Density(1f, scale), coroutineContext = coroutineContext) { AppTheme { WrappedContent(sample) } }
            try {
                settle(scene); snapshot(scene, "overview-$width"); click(scene, "Open my story"); settle(scene)
                repeat(7) { page ->
                    assertTrue(nodes(scene).filter { texts(it).isNotEmpty() }.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width }, "page $page width $width")
                    val labels = listOf("Close story", "Previous", if (page == 6) "Back to recap" else "Next")
                    val navigation = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && texts(it).any { label -> label in labels } }
                    assertTrue(navigation.all { it.boundsInRoot.height >= 48 }, "page $page controls")
                    assertTrue(has(scene, "Story ${page + 1} of 7")); snapshot(scene, "story-${page + 1}-$width")
                    if (page < 6) { click(scene, "Next"); settle(scene) }
                }
                click(scene, "Back to recap"); settle(scene); assertTrue(has(scene, "Open my story"))
            } finally { scene.close() }
        }
    }
    @Test fun storiesWaitForReaderAndPreviousCloseAndYearSelectionResetNavigation(): Unit = runBlocking(Dispatchers.Swing) {
        var state by mutableStateOf(sample)
        val scene = ImageComposeScene(390, 1200, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            WrappedContent(state, onSelectYear = { state = state.copy(year = it, provisional = false) })
        } }
        try {
            settle(scene); click(scene, "Open my story"); settle(scene); settle(scene)
            assertTrue(has(scene, "Story 1 of 7")); click(scene, "Next"); settle(scene); click(scene, "Next"); settle(scene)
            assertTrue(has(scene, "The songs you came back to")); click(scene, "Previous"); settle(scene); assertTrue(has(scene, "Time with music"))
            click(scene, "Close story"); settle(scene); click(scene, "2025"); settle(scene)
            assertFalse(has(scene, "Year so far · this recap is still growing")); click(scene, "Open my story"); settle(scene)
            assertTrue(has(scene, "Story 1 of 7")); assertTrue(has(scene, "WRAPPED 2025"))
        } finally { scene.close() }
    }
    @Test fun loadingErrorEmptyAndDisabledHistoryHaveActionsWithoutFakeStats(): Unit = runBlocking(Dispatchers.Swing) {
        var state by mutableStateOf(WrappedState(loading = false))
        var settings = 0; var retries = 0
        val scene = ImageComposeScene(390, 1400, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            WrappedContent(state, onSettings = { settings++ }, onRefresh = { retries++ })
        } }
        try {
            settle(scene); assertTrue(has(scene, "Your story starts with a song")); assertFalse(has(scene, "Open my story"))
            click(scene, "Listening settings"); assertEquals(1, settings); snapshot(scene, "empty")
            state = state.copy(error = true); settle(scene); click(scene, "Try again"); assertEquals(1, retries); snapshot(scene, "error")
            state = state.copy(error = false, loading = true); settle(scene); assertTrue(has(scene, "Gathering your music…"))
            state = sample; settle(scene); click(scene, "How this recap is counted"); settle(scene)
            assertTrue(nodes(scene).any { texts(it).any { value -> value.startsWith("Minutes use recorded listening time.") } })
        } finally { scene.close() }
    }
    @Test fun shareExportsOnlyCardAndFailureCanBeRetried(): Unit = runBlocking(Dispatchers.Swing) {
        var calls = 0; var bitmap: ImageBitmap? = null
        val gate = CompletableDeferred<Unit>()
        val scene = ImageComposeScene(390, 1400, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            WrappedContent(sample, onShare = { image -> bitmap = image; calls++; if (calls == 1) false else { gate.await(); true } })
        } }
        try {
            settle(scene); click(scene, "Open my story"); settle(scene)
            repeat(6) { click(scene, "Next"); settle(scene) }
            click(scene, "Share my recap"); settle(scene)
            assertEquals(1, calls); assertTrue(has(scene, "The image could not be shared. Try again.")); assertEquals(342, bitmap!!.width)
            assertTrue(bitmap!!.height in 250..900); snapshot(scene, "share-failure")
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
            org.jetbrains.skia.Image.makeFromBitmap(bitmap!!.asSkiaBitmap()).use { image -> image.encodeToData()!!.use {
                Files.write(root.resolve("artifacts/wrapped-2026-10-08/share-card.png"), it.bytes) } }
            click(scene, "Share my recap"); settle(scene)
            assertTrue(nodes(scene).first { "Preparing image…" in texts(it) && it.config.contains(SemanticsActions.OnClick) }.config.contains(SemanticsProperties.Disabled))
            gate.complete(Unit); settle(scene); assertEquals(2, calls); assertFalse(has(scene, "The image could not be shared. Try again."))
            assertTrue(has(scene, "Your recap image is ready. On desktop, paste it into an app."))
        } finally { scene.close() }
    }
    @Test fun playlistBusyFailureAndOpenCallbacksAreAccurate(): Unit = runBlocking(Dispatchers.Swing) {
        var state by mutableStateOf(sample); var saves = 0; var opened: Long? = null
        val scene = ImageComposeScene(390, 1400, Density(1f), coroutineContext = coroutineContext) { AppTheme {
            WrappedContent(state, onSavePlaylist = { saves++ }, onOpenPlaylist = { opened = it })
        } }
        try {
            settle(scene); click(scene, "Save my top songs"); assertEquals(1, saves)
            state = state.copy(savingPlaylist = true); settle(scene)
            assertTrue(nodes(scene).first { "Saving playlist…" in texts(it) && it.config.contains(SemanticsActions.OnClick) }.config.contains(SemanticsProperties.Disabled))
            state = state.copy(savingPlaylist = false, playlistError = true); settle(scene)
            assertTrue(has(scene, "The playlist could not be saved. Try again.")); click(scene, "Save my top songs"); assertEquals(2, saves)
            state = state.copy(playlistError = false, playlistId = 42); settle(scene); click(scene, "Open my playlist"); assertEquals(42L, opened)
        } finally { scene.close() }
    }
    @Test fun accountReloadClosesStoryBeforeDisplayingNewData(): Unit = runBlocking(Dispatchers.Swing) {
        var state by mutableStateOf(sample)
        val scene = ImageComposeScene(390, 1200, Density(1f), coroutineContext = coroutineContext) { AppTheme { WrappedContent(state) } }
        try { settle(scene); click(scene, "Open my story"); settle(scene); state = WrappedState(loading = true); settle(scene)
            assertFalse(has(scene, "Close story")); assertFalse(has(scene, "Menuju Cahaya")); assertTrue(has(scene, "Gathering your music…"))
        } finally { scene.close() }
    }
    @Test fun sharingRemainsReachableByScrollingWithLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        var calls = 0
        val scene = ImageComposeScene(320, 900, Density(1f, 1.5f), coroutineContext = coroutineContext) { AppTheme { WrappedContent(sample, onShare = { calls++; true }) } }
        try {
            settle(scene); click(scene, "Open my story"); settle(scene)
            repeat(6) { click(scene, "Next"); settle(scene) }
            repeat(4) {
                nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) }.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 250f)
                settle(scene)
            }
            val share = nodes(scene).first { "Share my recap" in texts(it) && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(share.boundsInRoot.height >= 48); click(scene, "Share my recap"); settle(scene); assertEquals(1, calls)
            snapshot(scene, "share-320-scrolled")
        } finally { scene.close() }
    }
}
