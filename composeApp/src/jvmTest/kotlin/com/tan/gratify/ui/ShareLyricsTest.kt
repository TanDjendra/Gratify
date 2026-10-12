package com.tan.gratify.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import com.tan.gratify.expect.LyricsShareTarget
import com.tan.domain.data.model.social.StoryTrack
import com.tan.gratify.ui.component.lyrics.lyricsStoryDraft
import com.tan.gratify.ui.component.lyrics.LyricsShareDestinations
import com.tan.gratify.ui.screen.friends.completeStoryDraft
import com.tan.gratify.ui.component.lyrics.ShareLyricsSelection
import com.tan.gratify.ui.component.lyrics.ShareLyricsCard
import com.tan.gratify.ui.component.lyrics.toShareLyricsLines
import com.tan.gratify.ui.component.lyrics.shareCardContentColor
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.metadata.Line
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ShareLyricsTest {
    private val track = StoryTrack("song", "Menuju cahaya", "Nadira", null, 180000)
    private val timed = listOf(Line("9000", "1000", words = "<00:01.00>a<00:01.20>ku <00:02.00>ber<00:02.40>samamu"),
        Line("22000", "9000", words = "kali ini"), Line("42000", "22000", words = "Menuju cahaya"))

    @Test fun storyUsesSelectedLyricsAndTheirTimingWithoutBreakingWords() {
        val draft = lyricsStoryDraft(track, timed, 0..1, 60000, true)!!
        assertEquals("aku bersamamu\nkali ini", draft.caption)
        assertEquals(1000L, draft.startMs)
        assertEquals(30, draft.clipSeconds)
        assertTrue(draft.isValid)
    }

    @Test fun unsyncedStoryUsesPlaybackPositionAndClampsNearEnd() {
        val draft = lyricsStoryDraft(track, timed, 1..1, 179000, false)!!
        assertEquals(165000L, draft.startMs); assertEquals(15, draft.clipSeconds)
        val short = lyricsStoryDraft(track.copy(durationMs = 10000), timed, 1..1, 9000, false)!!
        assertEquals(8, short.clipSeconds); assertEquals(2000L, short.startMs); assertTrue(short.isValid)
    }

    @Test fun invalidStorySelectionsAreRejectedWithoutTruncatingText() {
        assertNull(lyricsStoryDraft(track, timed, -1..0, 0, true))
        assertNull(lyricsStoryDraft(track, timed, 0..3, 0, true))
        assertNull(lyricsStoryDraft(track, timed, IntRange.EMPTY, 0, true))
        assertNull(lyricsStoryDraft(track, listOf(timed[0].copy(words = "a".repeat(201))), 0..0, 0, true))
        assertNull(lyricsStoryDraft(track, listOf(timed[0].copy(words = "")), 0..0, 0, true))
        assertNull(lyricsStoryDraft(track.copy(durationMs = 7000), timed, 0..0, 0, true))
        assertEquals(200, lyricsStoryDraft(track, listOf(timed[0].copy(words = "a".repeat(200))), 0..0, 0, true)!!.caption.length)
    }

    @Test fun resolvingUnknownDurationPreservesSelectedCaptionAndStart() {
        val draft = lyricsStoryDraft(track.copy(durationMs = 0), timed, 1..1, 0, true)!!
        val completed = completeStoryDraft(draft.copy(audience = "public"), track)
        assertEquals(draft.caption, completed.caption); assertEquals(9000L, completed.startMs)
        assertEquals("public", completed.audience); assertTrue(completed.isValid)
        val short = completeStoryDraft(draft, track.copy(durationMs = 10000))
        assertEquals(8, short.clipSeconds); assertEquals(2000L, short.startMs); assertEquals(draft.caption, short.caption)
    }

    @Test fun allShareDestinationsAreReachableAndDispatchTheChosenTarget(): Unit = runBlocking(Dispatchers.Swing) {
        for (scale in listOf(1f, 1.5f)) {
            val dispatched = mutableListOf<LyricsShareTarget>(); var stories = 0
            val scene = ImageComposeScene(320, 620, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { Box(Modifier.padding(20.dp)) { LyricsShareDestinations(true, { dispatched += it }, { stories++ }) } }
            }
            fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
            fun nodes() = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
            try {
                repeat(15) { scene.render(it * 16_000_000L).close(); delay(16) }
                for (label in listOf("Instagram", "WhatsApp")) {
                    val node = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true && it.config.contains(SemanticsActions.OnClick) }
                    assertTrue(node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= 320)
                    node.config[SemanticsActions.OnClick].action!!.invoke()
                }
                val buttons = nodes().filter { it.config.contains(SemanticsActions.OnClick) }
                assertEquals(3, buttons.size)
                buttons[2].config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(0, stories)
                assertEquals(listOf(LyricsShareTarget.INSTAGRAM, LyricsShareTarget.WHATSAPP, LyricsShareTarget.SYSTEM), dispatched)
                val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
                val file = root.resolve("artifacts/simp-exact/share-destinations-$scale.png")
                Files.createDirectories(file.parent)
                scene.render(1_000_000_000L).use { it.encodeToData()!!.use { png -> Files.write(file, png.bytes) } }
            } finally { scene.close() }
        }
    }
    @Test fun picksStayConsecutiveAndRespectThreeLineLimit() {
        val selection = ShareLyricsSelection(4)
        selection.toggle(5); selection.toggle(6)
        assertEquals(4..6, selection.range)
        var limited = false
        selection.toggle(7) { limited = true }
        assertTrue(limited); assertEquals(4..6, selection.range)
        selection.toggle(5); assertEquals(5..5, selection.range)
        selection.toggle(11); assertEquals(11..11, selection.range)
        selection.toggle(10); assertEquals(10..11, selection.range)
        selection.toggle(10); selection.toggle(11)
        assertTrue(selection.isEmpty)
    }

    @Test fun exportedRichLyricsStripTimestampsWithoutChangingLineIndexes() {
        val data = NowPlayingScreenData.LyricsData(lyrics = Lyrics(lines = listOf(
            Line("2000", "1000", words = "<00:01.00> Menuju <00:01.500> cahaya"),
            Line("3000", "2000", words = ""), Line("4000", "3000", words = "Baris terakhir")
        ), syncType = "RICH_SYNCED"), lyricsProvider = com.tan.gratify.viewModel.LyricsProvider.LRCLIB)
        assertEquals(listOf("Menuju cahaya", "", "Baris terakhir"), data.toShareLyricsLines())
    }

    @Test fun lightCardsHaveDarkTextAndDarkCardsHaveWhiteText() {
        assertEquals(Color(0xFF141414), Color(0xFFEEE9DE).shareCardContentColor())
        assertEquals(Color.White, Color(0xFF28384D).shareCardContentColor())
    }

    @Test fun shareCardRendersThreeWrappedLinesOnSmallPhones(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(320, 620, Density(1f, 1.2f), coroutineContext = coroutineContext) {
            AppTheme { ShareLyricsCard(listOf("Langkah menuju cahaya", "Bersama dalam irama", "Langit membuka cerita"),
                "Langkah menuju cahaya", "Nadira", null, Color(0xFFEEE9DE)) }
        }
        try {
            repeat(15) { scene.render(it * 16_000_000L).close(); delay(16) }
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
            val file = root.resolve("artifacts/simp-restoration/share-card.png")
            Files.createDirectories(file.parent)
            scene.render(1_000_000_000L).use { it.encodeToData()!!.use { png -> Files.write(file, png.bytes) } }
        } finally { scene.close() }
    }
}
