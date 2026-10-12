package com.tan.gratify.lyrics.parser

import kotlin.test.*

class LrcRegressionTest {
    @Test fun voiceMarkersDoNotPreventSyllableReferenceMatching() {
        val words = parseRichSyncLyrics("[00:01.00] v12: <00:01.00>a <00:01.50>ku <00:02.00>ber <00:02.50>sa <00:03.00>ma <00:03.50>mu",
            syncedReference = "[00:01.00]aku bersamamu").lyrics!!.lines!!.single().words
        assertEquals("aku bersamamu", words.replace(Regex("<[^>]*>"), ""))
        assertFalse(words.contains("v12:"))
        assertEquals(6, Regex("<[^>]*>").findAll(words).count())
    }

    @Test fun literalVoiceLikeTextInsideTheLyricsIsPreserved() {
        val words = parseRichSyncLyrics("[00:01.00]<00:01.00>v1: <00:02.00>test").lyrics!!.lines!!.single().words
        assertEquals("v1: test", words.replace(Regex("<[^>]*>"), ""))
    }

    private val mangu = "[00:22.96] <00:22.96>Su <00:23.68>a <00:24.04>tu <00:24.99>ma <00:25.33>lam <00:25.76>A <00:26.17>dam <00:26.58>ber <00:27.44>ce <00:27.85>ri <00:28.24>ta"

    @Test fun providerSyllableSpacesUseMatchingSyncedLineAndKeepEveryTimestamp() {
        val line = parseRichSyncLyrics(mangu, syncedReference = "[00:23.02] Suatu malam Adam bercerita").lyrics!!.lines!!.single()
        assertEquals("Suatu malam Adam bercerita", line.words.replace(Regex("<[^>]*>"), ""))
        assertEquals(Regex("<[^>]*>").findAll(mangu).map { it.value }.toList(),
            Regex("<[^>]*>").findAll(line.words).map { it.value }.toList())
        assertEquals("22960", line.startTimeMs)
    }

    @Test fun plainReferenceWorksWhenSyncedReferenceIsUnavailable() {
        val line = parseRichSyncLyrics(mangu, plainReference = "Suatu malam Adam bercerita").lyrics!!.lines!!.single()
        assertEquals("Suatu malam Adam bercerita", line.words.replace(Regex("<[^>]*>"), ""))
    }

    @Test fun mismatchAndMissingReferenceNeverGuessWordBoundaries() {
        for (reference in listOf<String?>(null, "Different words")) {
            val line = parseRichSyncLyrics(mangu, plainReference = reference).lyrics!!.lines!!.single()
            assertEquals("Su a tu ma lam A dam ber ce ri ta", line.words.replace(Regex("<[^>]*>"), ""))
        }
    }

    @Test fun repeatedCharacterSequenceUsesNearestTimedReference() {
        val line = parseRichSyncLyrics("[00:20.00]<00:20.00>a <00:21.00>b <00:22.00>c",
            syncedReference = "[00:01.00]ab c\n[00:20.10]a bc").lyrics!!.lines!!.single()
        assertEquals("a bc", line.words.replace(Regex("<[^>]*>"), ""))
    }

    @Test fun ttmlSyllablesKeepSourceWordBoundariesAndTiming() {
        val result = parseTtmlLyrics("""<p begin="00:01.000" end="00:03.000"><span begin="00:01.000" end="00:01.100">a</span><span begin="00:01.100" end="00:01.200">ku</span> <span begin="00:01.200" end="00:01.300">ber</span><span begin="00:01.300" end="00:01.400">sa</span><span begin="00:01.400" end="00:01.500">ma</span><span begin="00:01.500" end="00:01.600">mu</span> <span begin="00:01.600" end="00:01.700">kali</span> <span begin="00:01.700" end="00:01.800">i</span><span begin="00:01.800" end="00:02.000">ni</span></p>""").lyrics!!
        assertEquals("RICH_SYNCED", result.syncType)
        val words = result.lines!!.single().words
        assertEquals("aku bersamamu kali ini", words.replace(Regex("<[^>]*>"), ""))
        assertTrue(words.contains("<00:01.10>ku"))
        assertEquals("1000", result.lines!!.single().startTimeMs)
        assertEquals("3000", result.lines!!.single().endTimeMs)
    }

    @Test fun ttmlKeepsSpacesInsideSpansAndPunctuationBetweenThem() {
        val words = parseTtmlLyrics("""<p begin="00:01.000" end="00:03.000"><span begin="00:01.000" end="00:02.000">Hello </span><span begin="00:02.000" end="00:03.000">world</span>!</p>""").lyrics!!.lines!!.single().words
        assertEquals("Hello world!", words.replace(Regex("<[^>]*>"), ""))
    }

    @Test fun precisionMultipleStampsOffsetsAndNumericSorting() {
        val result = parseSyncedLyrics("[offset:-50]\n[01:00.123]Third\n[00:10.5][00:11.05]Repeated\n[00:01]\n[00:99.00]Invalid").lyrics!!.lines!!
        assertEquals(listOf("950", "10450", "11000", "60073"), result.map { it.startTimeMs })
        assertEquals(listOf("♫", "Repeated", "Repeated", "Third"), result.map { it.words })
    }
}
