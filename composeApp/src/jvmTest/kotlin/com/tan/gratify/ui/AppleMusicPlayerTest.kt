package com.tan.gratify.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.gratify.extension.ParsedRichSyncLine
import com.tan.gratify.extension.WordTiming
import com.tan.gratify.ui.component.appleWordEnd
import com.tan.gratify.ui.component.appleWordProgress
import com.tan.gratify.ui.component.AppleMusicLyricsLine
import com.tan.gratify.ui.screen.player.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AppleMusicPlayerTest {
    private val controls = ControlState(false, false, RepeatState.None, false, true, true, false, 1f)
    private val cover = ImageBitmap(480, 480).also {
        Canvas(it).drawRect(0f, 0f, 480f, 480f, Paint().apply { color = Color(0xFF34558A) })
        Canvas(it).drawCircle(Offset(300f, 190f), 145f, Paint().apply { color = Color(0xFFCAD7E2) })
        Canvas(it).drawRect(0f, 350f, 480f, 480f, Paint().apply { color = Color(0xFF172F54) })
    }
    private fun track(id: String, title: String) = Track(null, listOf(Artist("artist", "Nadira")), "3:40", 220,
        true, false, null, null, title, id, null, null, null, null)
    private val tracks = listOf(track("song-one", "Langkah menuju cahaya"), track("song-two", "Pulang dalam irama"))
    private fun lyrics(sync: String = "LINE_SYNCED") = NowPlayingScreenData.LyricsData(
        Lyrics(lines = listOf(
            Line("2000", "0", words = if (sync == "RICH_SYNCED") "<00:00.00> Langkah <00:01.00> kita" else "Langkah kita"),
            Line("5000", "2000", words = if (sync == "RICH_SYNCED") "<00:02.00> Menuju <00:03.00> cahaya" else "Menuju cahaya"),
            Line("8000", "5000", words = "Bersama dalam irama"),
            Line("11000", "8000", words = "Langit membuka cerita"),
        ), syncType = sync), lyricsProvider = LyricsProvider.LRCLIB)
    private fun screen(data: NowPlayingScreenData.LyricsData? = lyrics()) = NowPlayingScreenData(
        "Perjalanan sore", "Langkah menuju cahaya", "Nadira", false, thumbnailURL = null, lyricsData = data, bitmap = cover)
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun all(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    private fun label(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private fun click(scene: ImageComposeScene, name: String) {
        val node = all(scene).first { label(it) == name && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private suspend fun settle(scene: ImageComposeScene, start: Long = 0L) {
        repeat(25) { scene.render(start + it * 16_000_000L).close(); delay(16) }
    }
    private fun save(scene: ImageComposeScene, name: String, time: Long = 2_000_000_000L) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/lyrics-line-only-2026-10-08/$name.png")
        Files.createDirectories(path.parent)
        scene.render(time).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }

    @Test fun wordTimingHandlesBackwardSeekAndMalformedFinalTimestamp() {
        assertEquals(0f, appleWordProgress(900, 1000, 2000))
        assertEquals(.5f, appleWordProgress(1500, 1000, 2000))
        assertEquals(1f, appleWordProgress(2500, 1000, 2000))
        assertEquals(.1f, appleWordProgress(1100, 1000, 2000))
        val line = ParsedRichSyncLine(listOf(WordTiming("Langkah", 1000), WordTiming("kita", 1500)), 1000, Long.MAX_VALUE)
        assertEquals(1500L, appleWordEnd(line, 0))
        assertEquals(2000L, appleWordEnd(line, 1))
    }

    @Test fun liveTimelineUpdatesTimesAndKeepsSeekCallbacksCurrent(): Unit = runBlocking(Dispatchers.Swing) {
        val timeline = MutableStateFlow(TimeLine(2500, 220000, 50, false))
        val events = mutableListOf<UIEvent>()
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                AppleMusicPlayerContent(AppleMusicPlayerState(screen(), controls, timeline, tracks, 0,
                    Color(0xFF34558A), AppleMusicPlayerTab.MUSIC, rememberPagerState { tracks.size }, previewAndroid = true),
                    AppleMusicPlayerActions(events::add, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        fun hasTime(expected: String) = all(scene).any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text == expected
            } == true
        }
        try {
            settle(scene)
            assertTrue(hasTime("00:02"), "Initial elapsed time must be rendered")
            timeline.value = TimeLine(12500, 220000, 50, false)
            settle(scene, 500_000_000L)
            assertTrue(hasTime("00:12"), "Position updates must still reach elapsed time")
            val seek = all(scene).first { node ->
                node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..1f &&
                    node.config.getOrNull(SemanticsActions.SetProgress) != null
            }
            assertTrue(seek.config[SemanticsActions.SetProgress].action!!.invoke(.5f))
            assertEquals(UIEvent.UpdateProgress(50f), events.last(), "Seek must use the current slider value")
            timeline.value = TimeLine(500, 220000, 50, false)
            settle(scene, 1_000_000_000L)
            assertTrue(hasTime("00:00"), "Backward seeks must immediately replace the displayed position")
        } finally { scene.close() }
    }

    @Test fun playerTabsKeepPlaybackIndependentAndControlsReachable(): Unit = runBlocking(Dispatchers.Swing) {
        val tab = mutableStateOf(AppleMusicPlayerTab.MUSIC)
        val events = mutableListOf<UIEvent>()
        var queueIndex = -1
        var managed = false
        val timeline = MutableStateFlow(TimeLine(2500, 220000, 50, false))
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                AppleMusicPlayerContent(AppleMusicPlayerState(screen(), controls, timeline, tracks, 0,
                    Color(0xFF34558A), tab.value, rememberPagerState { tracks.size }, previewAndroid = true),
                    AppleMusicPlayerActions(events::add, { tab.value = it }, { queueIndex = it }, {}, {}, {}, {}, {}, {}, {}, { managed = true }))
            }
        }
        try {
            settle(scene)
            save(scene, "now-playing")
            click(scene, "Play")
            assertEquals(listOf<UIEvent>(UIEvent.PlayPause), events)
            events.clear()
            click(scene, "Lyrics")
            settle(scene, 500_000_000L)
            assertEquals(AppleMusicPlayerTab.LYRICS, tab.value)
            assertTrue(events.isEmpty())
            save(scene, "lyrics")
            val line = all(scene).first { it.config.getOrNull(SemanticsActions.OnClick) != null &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Menuju cahaya" } == true }
            line.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(UIEvent.UpdateProgress(2000f * 100 / 220000), events.last())
            click(scene, "Queue")
            settle(scene, 1_000_000_000L)
            save(scene, "queue")
            val row = all(scene).first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == tracks[1].title } == true
            }
            row.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, queueIndex)
            click(scene, "Track options")
            settle(scene, 1_200_000_000L)
            assertTrue(all(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Remove from queue" } == true })
            val play = all(scene).first { label(it) == "Play" }
            assertTrue(play.boundsInRoot.bottom <= 844f)
        } finally { scene.close() }
    }

    @Test fun lyricsRenderAcrossScreenSizesAndKeepPausedWordsStill(): Unit = runBlocking(Dispatchers.Swing) {
        for ((name, width, height, scale, sync) in listOf(
            RenderCase("rich-lyrics", 390, 844, 1f, "RICH_SYNCED"),
            RenderCase("large-text", 360, 640, 1.5f, "LINE_SYNCED"),
            RenderCase("landscape", 900, 420, 1f, "UNSYNCED"),
            RenderCase("empty-lyrics", 390, 844, 1f, "EMPTY"),
            RenderCase("main-large-text", 360, 640, 1.5f, "MUSIC"),
            RenderCase("video-landscape", 900, 420, 1f, "VIDEO"),
        )) {
            val timeline = MutableStateFlow(TimeLine(2500, 220000, 50, false))
            val scene = ImageComposeScene(width, height, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme {
                    AppleMusicPlayerContent(AppleMusicPlayerState(screen(if (sync == "EMPTY") null else lyrics(sync)).copy(isVideo = sync == "VIDEO"), controls,
                        timeline, tracks, 0, Color(0xFF34558A),
                        if (sync == "MUSIC" || sync == "VIDEO") AppleMusicPlayerTab.MUSIC else AppleMusicPlayerTab.LYRICS,
                        rememberPagerState { tracks.size }, showVideo = sync == "VIDEO", previewAndroid = true),
                        AppleMusicPlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                        video = { modifier -> Box(modifier.background(Color(0xFF264D78))
                            .semantics { contentDescription = "Video preview" }) })
                }
            }
            try {
                settle(scene)
                save(scene, name)
                val play = all(scene).first { label(it) == "Play" }
                assertTrue(play.boundsInRoot.bottom <= height, "$name: transport clipped")
                if (sync == "VIDEO") {
                    val frame = all(scene).first { label(it) == "Video preview" }
                    assertTrue(frame.boundsInRoot.bottom < play.boundsInRoot.top, "Video must not cover playback controls")
                }
                if (sync == "RICH_SYNCED") {
                    val first = scene.render(3_000_000_000L).use { it.encodeToData()!!.use { data -> data.bytes.copyOf() } }
                    delay(180)
                    val second = scene.render(5_000_000_000L).use { it.encodeToData()!!.use { data -> data.bytes.copyOf() } }
                    assertContentEquals(first, second, "Paused lyrics must not keep singing")
                    timeline.value = TimeLine(500, 220000, 50, false)
                    settle(scene, 6_000_000_000L)
                    save(scene, "lyrics-after-backward-seek", 7_000_000_000L)
                }
            } finally { scene.close() }
        }
    }

    @Test fun richHighlightRebasesWithinTheSameWordAndStopsAfterResume(): Unit = runBlocking(Dispatchers.Swing) {
        val position = mutableStateOf(2900L)
        val playing = mutableStateOf(false)
        val parsed = ParsedRichSyncLine(listOf(WordTiming("Menuju", 2000), WordTiming("cahaya", 3000)), 2000, 5000)
        val scene = ImageComposeScene(390, 250, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                com.tan.gratify.ui.screen.player.simp.component.RichSyncLyricsLineItem(
                    parsedLine = parsed, translatedWords = null, currentTimeMs = position.value, isCurrent = true,
                    isPlaying = playing.value, customFontSize = com.tan.gratify.ui.screen.player.simp.component.AppleMusicLyricFontSize,
                    glow = com.tan.gratify.ui.screen.player.simp.component.AppleMusicActiveLineGlow,
                    modifier = Modifier.background(Color(0xFF172F54)))
            }
        }
        fun pixels(time: Long) = scene.render(time).use { it.encodeToData()!!.use { data -> data.bytes.copyOf() } }
        try {
            settle(scene)
            val almostComplete = pixels(500_000_000L)
            assertTrue(all(scene).any { label(it) == "Menuju" }, "Animated glyphs must be read as a complete word")
            position.value = 2100L
            settle(scene, 600_000_000L)
            val afterSeek = pixels(1_100_000_000L)
            assertFalse(almostComplete.contentEquals(afterSeek), "Seek within a word must reduce its highlight")
            playing.value = true
            settle(scene, 1_200_000_000L)
            val resumed = pixels(1_700_000_000L)
            assertFalse(afterSeek.contentEquals(resumed), "Resuming must animate the singing word")
            playing.value = false
            settle(scene, 1_800_000_000L)
            val paused = pixels(2_300_000_000L)
            delay(100)
            assertContentEquals(paused, pixels(3_500_000_000L), "Pause after resume must cancel the word animation")
            position.value = 3100L
            settle(scene, 3_600_000_000L)
            assertFalse(paused.contentEquals(pixels(4_100_000_000L)), "Crossing a word boundary must move the highlight")
            position.value = 2900L
            settle(scene, 4_200_000_000L)
            assertContentEquals(almostComplete, pixels(4_700_000_000L),
                "Seeking back across a word boundary must restore the original word highlight")
        } finally { scene.close() }
    }

    @Test fun loadingReadyAndPlayingKeepTheSamePlayerLayout(): Unit = runBlocking(Dispatchers.Swing) {
        val time = MutableStateFlow(TimeLine(0, 0, 0, true))
        val playing = mutableStateOf(false)
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                AppleMusicPlayerContent(AppleMusicPlayerState(screen(), controls.copy(isPlaying = playing.value),
                    time, tracks, 0, Color(0xFF34558A), AppleMusicPlayerTab.MUSIC, rememberPagerState { tracks.size }, previewAndroid = true),
                    AppleMusicPlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        fun textBounds() = all(scene).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { word -> word.text == "Nadira" } == true
        }.boundsInRoot
        try {
            settle(scene)
            assertTrue(all(scene).any { label(it) == "Play" }, "Upstream keeps the play control while loading")
            val loadingBounds = textBounds()
            save(scene, "loading-consistent")
            time.value = TimeLine(0, 220000, 0, false)
            settle(scene, 500_000_000L)
            assertEquals(loadingBounds, textBounds())
            assertTrue(all(scene).any { label(it) == "Play" })
            playing.value = true
            settle(scene, 1_000_000_000L)
            assertEquals(loadingBounds, textBounds())
            assertTrue(all(scene).any { label(it) == "Pause" })
            assertTrue(all(scene).any { label(it) == "Lyrics" })
        } finally { scene.close() }
    }

    @Test fun standardModeKeepsWordsStillAndFollowsLineChanges(): Unit = runBlocking(Dispatchers.Swing) {
        val position = mutableStateOf(2100L)
        val parsed = ParsedRichSyncLine(listOf(WordTiming("Menuju", 2000), WordTiming("cahaya", 3000)), 2000, 5000)
        val time = MutableStateFlow(TimeLine(position.value, 220000, 50, false))
        val scene = ImageComposeScene(390, 250, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                com.tan.gratify.ui.screen.player.simp.component.LyricsView(lyrics("RICH_SYNCED"), time, {},
                    modifier = Modifier.background(Color(0xFF172F54)), isPlaying = true)
            }
        }
        fun pixels(time: Long) = scene.render(time).use { it.encodeToData()!!.use { data -> data.bytes.copyOf() } }
        try {
            settle(scene)
            val before = pixels(500_000_000L)
            position.value = 2900L
            time.value = time.value.copy(current = position.value)
            settle(scene, 600_000_000L)
            assertContentEquals(before, pixels(1_100_000_000L))
            position.value = 3200L
            time.value = time.value.copy(current = position.value)
            settle(scene, 1_200_000_000L)
            assertContentEquals(before, pixels(1_700_000_000L), "No animation must not start a new wipe at a word boundary")
            save(scene, "standard-lyrics")
            assertTrue(all(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { word -> word.text == "Menuju cahaya" } == true })
            position.value = 5200L
            time.value = time.value.copy(current = position.value)
            settle(scene, 1_800_000_000L)
            assertFalse(before.contentEquals(pixels(2_300_000_000L)), "Standard lyrics must refocus when the sung line changes")
            save(scene, "standard-lyrics-next-line", 2_400_000_000L)
        } finally { scene.close() }
    }

    @Test fun fullscreenLyricsUseTheSameRendererAndExposePlayback(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, height) in listOf(390 to 844, 900 to 420)) {
            val timeline = MutableStateFlow(TimeLine(2500, 220000, 50, false))
            val events = mutableListOf<UIEvent>()
            val scene = ImageComposeScene(width, height, Density(1f), coroutineContext = coroutineContext) {
                AppTheme {
                    AppleMusicPlayerContent(AppleMusicPlayerState(screen(lyrics("RICH_SYNCED")), controls,
                        timeline, tracks, 0, Color(0xFF34558A), AppleMusicPlayerTab.LYRICS,
                        rememberPagerState { tracks.size }, previewAndroid = true),
                        AppleMusicPlayerActions(events::add, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}), fullscreenLyrics = true)
                }
            }
            try {
                settle(scene)
                save(scene, "fullscreen-${width}x$height")
                assertTrue(all(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Menuju cahaya" } == true },
                    "Fullscreen must show complete lines even when the provider has word timestamps")
                val slider = all(scene).first { it.config.getOrNull(SemanticsActions.SetProgress) != null }
                assertTrue(slider.boundsInRoot.bottom <= height, "Fullscreen progress must remain reachable")
            } finally { scene.close() }
        }
    }

    private data class RenderCase(val name: String, val width: Int, val height: Int, val scale: Float, val sync: String)

    @Test fun restoredAnimationFlagCannotEnableWordsInThePlayerOrFullscreen(): Unit = runBlocking(Dispatchers.Swing) {
        for (fullscreen in listOf(false, true)) {
            val timeline = MutableStateFlow(TimeLine(2500, 220000, 50, false))
            val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
                AppTheme {
                    AppleMusicPlayerContent(AppleMusicPlayerState(screen(lyrics("RICH_SYNCED")), controls,
                        timeline, tracks, 0, Color(0xFF34558A), AppleMusicPlayerTab.LYRICS,
                        rememberPagerState { tracks.size }, previewAndroid = true, lyricsAnimationsEnabled = true),
                        AppleMusicPlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}), fullscreenLyrics = fullscreen)
                }
            }
            try {
                settle(scene)
                assertTrue(all(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Menuju cahaya" } == true })
                assertFalse(all(scene).any { label(it) == "Menuju" }, "Standard mode must render whole lines, not animated words")
                assertFalse(all(scene).any { label(it) in listOf("SimpMusic animation", "Standard · line by line") },
                    "The retired animation toggle must not appear")
                save(scene, if (fullscreen) "standard-fullscreen" else "standard-player")
            } finally { scene.close() }
        }
    }

    @Test fun compactLyricStripShowsTheWholeLineWithLegacyAnimationFlag(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                AppleMusicPlayerContent(AppleMusicPlayerState(screen(lyrics("RICH_SYNCED")), controls.copy(isPlaying = true),
                    MutableStateFlow(TimeLine(2500, 220000, 50, false)), tracks, 0, Color(0xFF34558A), AppleMusicPlayerTab.MUSIC,
                    rememberPagerState { tracks.size }, previewAndroid = true, lyricsAnimationsEnabled = true),
                    AppleMusicPlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        try {
            settle(scene)
            assertTrue(all(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Menuju cahaya" } == true })
            assertFalse(all(scene).any { label(it) == "Menuju" })
            save(scene, "line-only-compact")
        } finally { scene.close() }
    }

    @Test fun heartActionsStayAlignedAcrossPlayerTabsAndAfterToggling(): Unit = runBlocking(Dispatchers.Swing) {
        for ((tab, width, scale) in listOf(
            Triple(AppleMusicPlayerTab.MUSIC, 390, 1f),
            Triple(AppleMusicPlayerTab.LYRICS, 360, 1.5f),
            Triple(AppleMusicPlayerTab.QUEUE, 390, 1f),
        )) {
            val liked = mutableStateOf(false)
            val events = mutableListOf<UIEvent>()
            val scene = ImageComposeScene(width, 844, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme {
                    AppleMusicPlayerContent(AppleMusicPlayerState(screen(), controls.copy(isLiked = liked.value),
                        MutableStateFlow(TimeLine(2500, 220000, 50, false)), tracks, 0,
                        Color(0xFF34558A), tab, rememberPagerState { tracks.size }, previewAndroid = true,
                        isYouTubeLoggedIn = true),
                        AppleMusicPlayerActions({ event -> events.add(event); if (event == UIEvent.ToggleLike) liked.value = !liked.value }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
                }
            }
            try {
                settle(scene)
                val heart = all(scene).first { label(it) == "Like" }
                val originalBounds = heart.boundsInRoot
                val more = all(scene).filter { label(it) == "More options" }.minBy { kotlin.math.abs(it.boundsInRoot.center.y - originalBounds.center.y) }
                assertEquals(originalBounds.center.y, more.boundsInRoot.center.y, .5f)
                assertTrue(originalBounds.width >= 48f && originalBounds.height >= 48f)
                click(scene, "Like")
                settle(scene, 500_000_000L)
                assertEquals(listOf<UIEvent>(UIEvent.ToggleLike), events)
                assertEquals(originalBounds, all(scene).first { label(it) == "Liked" }.boundsInRoot)
                save(scene, "gratify-heart-${tab.name.lowercase()}")
                click(scene, "Liked")
                settle(scene, 1_000_000_000L)
                assertEquals(2, events.size)
                assertEquals(originalBounds, all(scene).first { label(it) == "Like" }.boundsInRoot)
            } finally { scene.close() }
        }
    }

    @Test fun richSyllablesTouchWithoutExtraGapsAndWrapAsWholeWords(): Unit = runBlocking(Dispatchers.Swing) {
        val syllables = "<00:01.00>a<00:01.10>ku <00:01.20>ber<00:01.30>sa<00:01.40>ma<00:01.50>mu <00:01.60>kali <00:01.70>i<00:01.80>ni"
        val parsed = com.tan.gratify.extension.parseRichSyncWords(syllables, "1000", "3000")!!
        val scene = ImageComposeScene(360, 400, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                com.tan.gratify.ui.screen.player.simp.component.RichSyncLyricsLineItem(
                    parsedLine = parsed, translatedWords = null, currentTimeMs = 1000, isCurrent = true,
                    modifier = Modifier.background(Color(0xFF172F54)), isPlaying = false)
            }
        }
        try {
            settle(scene)
            val a = all(scene).first { label(it) == "a" }.boundsInRoot
            val ku = all(scene).first { label(it) == "ku" }.boundsInRoot
            assertEquals(a.right, ku.left, .5f, "Syllables inside aku must have no artificial layout gap")
            val ber = all(scene).first { label(it) == "ber" }.boundsInRoot
            val mu = all(scene).first { label(it) == "mu" }.boundsInRoot
            assertEquals(ber.top, mu.top, .5f, "bersamamu must wrap as a whole word")
            save(scene, "gratify-syllable-spacing")
        } finally { scene.close() }
    }
}
