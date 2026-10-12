package com.tan.gratify.ui

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.gratify.ui.screen.friends.*
import kotlin.test.*

class StoryLyricsPreviewTest {
    @Test fun excerptIncludesTheLineAlreadyPlayingAtTheStartAndExcludesTheEndBoundary() {
        val lyrics = Lyrics(lines = listOf(Line("10000", "0", words = "Before"),
            Line("20000", "10000", words = "Selected"), Line("30000", "20000", words = "Next"),
            Line("40000", "30000", words = "Outside")), syncType = "LINE_SYNCED")
        assertEquals(listOf("Selected", "Next"), storyLyricExcerpt(lyrics, 15000, 15).map { it.text })
        assertEquals(listOf("Next"), storyLyricExcerpt(lyrics, 20000, 8).map { it.text })
    }

    @Test fun richLyricsUseOriginalTimesAndHideTimingMarkup() {
        val lyrics = Lyrics(lines = listOf(Line("0", "1000", words = "<00:01.00>Hello <00:01.50>world"),
            Line("0", "10000", words = "<00:10.000>Next")), syncType = "RICH_SYNCED")
        val selected = storyLyricExcerpt(lyrics, 2000, 8)
        assertEquals(listOf("Hello world"), selected.map { it.text })
        assertEquals(1000L, selected.single().startMs)
        assertEquals(10000L, selected.single().endMs)
    }

    @Test fun unavailableAndInstrumentalIntervalsRemainEmpty() {
        val lyrics = Lyrics(lines = listOf(Line("10000", "0", words = "Earlier"), Line("30000", "25000", words = "Later")), syncType = "LINE_SYNCED")
        assertTrue(storyLyricExcerpt(lyrics, 12000, 8).isEmpty())
        assertTrue(storyLyricExcerpt(lyrics.copy(error = true), 0, 8).isEmpty())
        assertTrue(storyLyricExcerpt(null, 0, 8).isEmpty())
    }

    @Test fun untimedLyricsDoNotInventAbsoluteTimestamps() {
        val lyrics = Lyrics(lines = listOf(Line("0", "0", words = "Plain text")), syncType = "UNSYNCED")
        assertEquals("Plain text", storyLyricExcerpt(lyrics, 45000, 15).single().text)
    }

    @Test fun elapsedClockClampsAndShowsTheFullDurationAtCompletion() {
        for (seconds in listOf(8, 15, 30)) {
            assertEquals(0L, storyElapsedMs(-20, seconds, false))
            assertEquals(seconds * 1000L, storyElapsedMs(100000, seconds, false))
            assertEquals(seconds * 1000L, storyElapsedMs(0, seconds, true))
        }
    }
}
