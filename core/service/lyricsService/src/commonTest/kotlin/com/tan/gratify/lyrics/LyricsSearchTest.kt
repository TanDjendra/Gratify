package com.tan.gratify.lyrics

import com.tan.gratify.lyrics.models.response.LrclibObject
import com.tan.gratify.lyrics.parser.parseTtmlLyrics
import kotlin.test.*

class LyricsSearchTest {
    private fun row(id: Int, duration: Float, sync: String? = "[00:01.00]A new day", plain: String? = "A new day") =
        LrclibObject(id, "A new day", "A new day", "Example", "Example", duration, false, plain, sync)

    @Test fun unknownDurationDoesNotRejectAllMatches() {
        assertEquals(1, selectLrclibResult(listOf(row(1, 180f)), 0)?.id)
        assertEquals(1, selectLrclibResult(listOf(row(1, 180f)), null)?.id)
    }
    @Test fun emptyFirstResultDoesNotHideLaterSyncedResult() {
        assertEquals(3, selectLrclibResult(listOf(row(1, 180f, null, null), row(2, 180f, null), row(3, 182f)), 180)?.id)
    }
    @Test fun nearestValidRecordingWinsWithoutAcceptingDifferentDurations() {
        assertEquals(2, selectLrclibResult(listOf(row(1, 190f), row(2, 180f), row(3, 300f)), 180)?.id)
        assertNull(selectLrclibResult(listOf(row(3, 300f)), 180))
    }
    @Test fun videoPackagingIsRemovedButRecordingVersionIsPreserved() {
        assertEquals("Losing You", lyricsSearchTitle("Losing You (Official Music Video)"))
        assertEquals("Losing You (Live)", lyricsSearchTitle("Losing You (Live) [Official Audio]"))
        assertEquals("Losing You (Remix)", lyricsSearchTitle("Losing You (Remix)"))
    }

    @Test fun sameDurationCannotSelectAnotherSongArtistOrRecordingVersion() {
        val rows = listOf(row(1, 180f).copy(trackName = "Wrong", artistName = "Perunggu"),
            row(2, 180f).copy(trackName = "33x", artistName = "Other band"),
            row(3, 180f).copy(trackName = "33x (Live)", artistName = "Perunggu"),
            row(4, 181f).copy(trackName = "33x", artistName = "Perunggu - Topic"))
        assertEquals(4, selectLrclibResult(rows, 180, "33x (Official Audio)", "Perunggu")?.id)
        assertEquals(3, selectLrclibResult(rows, 180, "33x (Live)", "Perunggu")?.id)
        assertNull(selectLrclibResult(rows.take(3), 180, "33x", "Perunggu"))
    }

    @Test fun cleanTranscriptWinsOverFragmentedNearestDuration() {
        val bad = row(1, 180f, "[00:01.00]a ku se n di ri").copy(trackName = "33x", artistName = "Perunggu")
        val clean = row(2, 181f, "[00:01.00]aku sendiri").copy(trackName = "33x", artistName = "Perunggu")
        assertEquals(2, selectLrclibResult(listOf(bad, clean), 180, "33x", "Perunggu")?.id)
    }
    @Test fun betterLyricsWordTimesSurviveParsingForTheSimpRenderer() {
        val parsed = parseTtmlLyrics("""<tt><body><p begin="1.200" end="4.000"><span begin="1.200" end="2.000">A</span><span begin="2.000" end="4.000">day</span></p></body></tt>""").lyrics!!
        assertEquals("RICH_SYNCED", parsed.syncType)
        assertEquals("1200", parsed.lines!!.first().startTimeMs)
        assertEquals("4000", parsed.lines!!.first().endTimeMs)
        assertTrue(parsed.lines!!.first().words.contains("<00:02.00>day"))
    }
}
