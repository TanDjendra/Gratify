package com.tan.domain.utils

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import kotlinx.serialization.json.Json
import kotlin.test.*

class LyricsTextQualityTest {
    @Test fun repeatedReferencesKeepTheClosestTimingAndOriginalOrderOnTies() {
        val source = Lyrics(syncType = "LINE_SYNCED", lines = listOf(
            Line("3000", "1000", words = "He llo wo rld", textQualityVersion = -1),
            Line("4000", "1900", words = "He llo wo rld", textQualityVersion = -1),
        ))
        val reference = Lyrics(syncType = "LINE_SYNCED", lines = listOf(
            Line("3000", "2000", words = "Hello world"),
            Line("1000", "0", words = "Hell oworld"),
            Line("3000", "2000", words = "Hel loworld"),
        ))
        val repaired = source.reconcileWordBoundaries(reference)
        assertEquals(listOf("Hello world", "Hello world"), repaired.lines!!.map { it.words })
        assertEquals(source.lines!!.map { it.startTimeMs }, repaired.lines!!.map { it.startTimeMs })
    }

    private fun lyrics(vararg text: String) = Lyrics(syncType = "LINE_SYNCED",
        lines = text.mapIndexed { index, words -> Line("${index * 3000 + 3000}", "${index * 3000 + 1000}", words = words) })

    @Test fun reportedFragmentationTriggersReferenceSearchRegardlessOfTimingFormat() {
        for (text in listOf("Kau ta hu ma na u rut an sa tu", "a ku se n di ri", "Su a tu ma lam A dam ber ce ri ta")) {
            val source = lyrics(text)
            assertTrue(source.needsWordBoundaryReference(), text)
            assertFalse(source.hasDisplayableLyrics(), text)
        }
    }

    @Test fun ordinaryShortWordsAndNonLatinScriptsArePreserved() {
        for (text in listOf("aku dan kau di sini", "I am okay", "I am", "I am in a room on my own", "di rumah",
            "na na na na na na", "la di da la di da", "ba ba ba ba ba ba", "你 是 我 的 光", "君 の 声 が 聞 こ え る")) {
            assertTrue(lyrics(text).hasDisplayableLyrics(), text)
        }
    }

    @Test fun cleanReferenceRepairsLineSpacingWithoutChangingCharactersOrTiming() {
        val source = lyrics("Kau ta hu ma na u rut an sa tu").assessTextQuality()
        val reference = Lyrics(syncType = "LINE_SYNCED", lines = listOf(Line("99000", "95000", words = "kau tahu mana urutan satu")))
        val repaired = source.reconcileWordBoundaries(reference)
        assertEquals("Kau tahu mana urutan satu", repaired.lines!!.single().words)
        assertEquals("1000", repaired.lines!!.single().startTimeMs)
        assertEquals("3000", repaired.lines!!.single().endTimeMs)
        assertEquals(LYRICS_TEXT_QUALITY_VERSION, repaired.lines!!.single().textQualityVersion)
        assertTrue(repaired.hasDisplayableLyrics())
    }

    @Test fun partialRepairCannotCertifyUnmatchedLinesFromATaintedTranscript() {
        val source = lyrics("a ku se n di ri", "ta hu").assessTextQuality()
        val repaired = source.reconcileWordBoundaries(lyrics("aku sendiri"))
        assertEquals("aku sendiri", repaired.lines!![0].words)
        assertEquals(-1, repaired.lines!![1].textQualityVersion)
        assertFalse(repaired.hasDisplayableLyrics())
    }

    @Test fun anotherFragmentedTranscriptCannotCertifyTheSameBadSpaces() {
        val source = lyrics("a ku se n di ri").assessTextQuality()
        assertEquals(source, source.reconcileWordBoundaries(lyrics("a ku se n di ri")))
    }

    @Test fun mismatchedSpellingAndPunctuationAreNeverGuessed() {
        assertNull(reconcileLineWords("a ku", "aku!"))
        assertNull(reconcileLineWords("a ku", "kamu"))
    }

    @Test fun oldAndCorrectedCacheJsonRemainCompatibleAndCorrectionSurvivesConversions() {
        val json = Json
        val old = json.decodeFromString<Line>("""{"startTimeMs":"1000","endTimeMs":"3000","words":"a ku se n di ri"}""")
        assertEquals(0, old.textQualityVersion)
        assertFalse(Lyrics(syncType = "LINE_SYNCED", lines = listOf(old)).hasDisplayableLyrics())
        val fixed = lyrics("a ku se n di ri").assessTextQuality().reconcileWordBoundaries(lyrics("aku sendiri"))
        val cached = fixed.toLyricsEntity("example").toLyrics()
        assertEquals(fixed, cached)
        assertEquals(fixed.lines!!.single(), json.decodeFromString<Line>(json.encodeToString(Line.serializer(), fixed.lines!!.single())))
        val rich = fixed.copy(syncType = "RICH_SYNCED", lines = fixed.lines!!.map { it.copy(words = "<00:01.00>aku <00:02.00>sendiri") })
        assertEquals(LYRICS_TEXT_QUALITY_VERSION, rich.toSyncedLyrics().lines!!.single().textQualityVersion)
    }

    @Test fun rawTimingAndLegacyFalseRichCertificationCannotReachTheDisplay() {
        assertFalse(lyrics("<00:01.00>aku sendiri").hasDisplayableLyrics())
        val oldRich = lyrics("<00:01.00>a <00:02.00>ku <00:03.00>se <00:04.00>n <00:05.00>di <00:06.00>ri")
            .let { it.copy(syncType = "RICH_SYNCED", lines = it.lines!!.map { line -> line.copy(richSyncTextVersion = 2) }) }
        assertFalse(oldRich.hasVerifiedWordBoundaries())
        assertFalse(oldRich.hasDisplayableLyrics())
    }
}
