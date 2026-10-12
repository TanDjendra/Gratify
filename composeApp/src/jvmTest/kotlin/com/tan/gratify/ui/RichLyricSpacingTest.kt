package com.tan.gratify.ui

import com.tan.gratify.extension.displayText
import com.tan.gratify.extension.displayWordGroups
import com.tan.gratify.extension.parseRichSyncWords
import com.tan.gratify.ui.screen.player.simp.content.stripRichSyncTimestamps
import com.tan.gratify.ui.component.lyrics.stripRichLyricsTimestamps
import kotlin.test.*

class RichLyricSpacingTest {
    @Test fun correctWholeWordMarkupKeepsItsOwnCapitalization() {
        val source = "<00:24.85>Suatu <00:25.92>malam <00:27.50>Adam <00:28.57>bercerita"
        assertEquals(source, com.tan.domain.utils.reconcileRichSyncWords(source, "Suatu malam adam bercerita"))
    }

    @Test fun referenceOnlyRepairsSpacesAndDoesNotReplaceOriginalLetters() {
        val source = "<00:01.00>A <00:02.00>KU <00:03.00>ber <00:04.00>sa <00:05.00>ma <00:06.00>mu"
        val repaired = com.tan.domain.utils.reconcileRichSyncWords(source, "aku bersamamu")!!
        assertEquals("AKU bersamamu", repaired.stripRichSyncTimestamps())
        assertEquals("AKU bersamamu", parseRichSyncWords(repaired, "1000", "8000")!!.displayText())
        assertEquals(repaired, com.tan.domain.utils.reconcileRichSyncWords(repaired, "aku bersamamu"))
    }

    @Test fun alreadyCorrectSpacingIsUnchangedAndRepairIsIdempotent() {
        val source = "<00:01.00>Hello <00:02.00>world"
        assertEquals(source, com.tan.domain.utils.reconcileRichSyncWords(source, "Hello world"))
        val broken = "<00:01.00>Hel <00:01.50>lo <00:02.00>world"
        val repaired = com.tan.domain.utils.reconcileRichSyncWords(broken, "Hello world")!!
        assertEquals(repaired, com.tan.domain.utils.reconcileRichSyncWords(repaired, "Hello world"))
    }

    @Test fun providerInsertedSpacesBecomeFourWholeWordsWhileSyllableTimingStaysIntact() {
        val source = "<00:22.96>Su <00:23.68>a <00:24.04>tu <00:24.99>ma <00:25.33>lam <00:25.76>A <00:26.17>dam <00:26.58>ber <00:27.44>ce <00:27.85>ri <00:28.24>ta"
        val corrected = com.tan.domain.utils.reconcileRichSyncWords(source, "Suatu malam Adam bercerita")!!
        val parsed = parseRichSyncWords(corrected, "22960", "29000")!!
        assertEquals(listOf("Suatu", "malam", "Adam", "bercerita"), parsed.displayWordGroups().map { group -> group.joinToString("") { parsed.words[it].text } })
        assertEquals(listOf(22960L, 23680L, 24040L, 24990L, 25330L, 25760L, 26170L, 26580L, 27440L, 27850L, 28240L), parsed.words.map { it.startTimeMs })
        assertEquals("Suatu malam Adam bercerita", corrected.stripRichSyncTimestamps())
        assertEquals("Suatu malam Adam bercerita", stripRichLyricsTimestamps(corrected))
    }

    @Test fun reconciliationHandlesEmptyFragmentsPunctuationAndHtmlWithoutJoiningWords() {
        val source = "<00:01.00>A&amp;B <00:02.00><00:03.00>he <00:04.00>llo <00:05.00>!"
        val corrected = com.tan.domain.utils.reconcileRichSyncWords(source, "A&amp;B hello!")!!
        assertEquals("A&B hello!", corrected.stripRichSyncTimestamps())
        assertEquals(5, Regex("<[^>]*>").findAll(corrected).count())
        assertNull(com.tan.domain.utils.reconcileRichSyncWords(source, "A&B goodbye!"))
    }

    private val syllables = "<00:01.00>a<00:01.10>ku <00:01.20>ber<00:01.30>sa<00:01.40>ma<00:01.50>mu <00:01.60>kali <00:01.70>i<00:01.80>ni"

    @Test fun syllablesStayJoinedWithTheirOriginalTiming() {
        val parsed = parseRichSyncWords(syllables, "1000", "3000")!!
        assertEquals("aku bersamamu kali ini", parsed.displayText())
        assertEquals(listOf("aku", "bersamamu", "kali", "ini"), parsed.displayWordGroups().map { group -> group.joinToString("") { parsed.words[it].text } })
        assertEquals(listOf(1000L, 1100L, 1200L, 1300L, 1400L, 1500L, 1600L, 1700L, 1800L), parsed.words.map { it.startTimeMs })
    }

    @Test fun noAnimationAndShareTextHaveTheSameWordBoundaries() {
        assertEquals("aku bersamamu kali ini", syllables.stripRichSyncTimestamps())
        assertEquals("aku bersamamu kali ini", stripRichLyricsTimestamps(syllables))
    }

    @Test fun ordinaryWordsWhitespaceAndPunctuationStillRenderNormally() {
        for ((source, expected) in listOf(
            "<00:01.00> Aku <00:02.00> bersamamu <00:03.00> kali <00:04.00> ini" to "Aku bersamamu kali ini",
            "<00:01.00>Hello<00:01.50>, <00:02.00>world<00:03.00>!" to "Hello, world!",
            "<00:01.00>你<00:02.00>好" to "你好",
            "<00:01.00>A&amp;B <00:02.00>bersama" to "A&B bersama",
            "<00:01.00>Aku <00:01.50><00:02.00>bersamamu" to "Aku bersamamu",
        )) assertEquals(expected, parseRichSyncWords(source, "1000", "5000")!!.displayText())
    }
}
