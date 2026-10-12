package com.tan.data

import com.tan.data.mapping.toLyrics
import com.tan.domain.utils.toSyncedLyrics
import com.tan.domain.utils.hasDisplayableLyrics
import com.tan.domain.utils.hasVerifiedWordBoundaries
import com.tan.gratify.lyrics.models.response.LyricsResponse
import kotlin.test.*

class SimpMusicLyricsMappingTest {
    private val splitAkuSendiri = "[00:01.00]<00:01.00>a <00:01.20>ku <00:01.50>se <00:01.70>n <00:01.80>di <00:02.00>ri"

    @Test fun corruptedSyncedFieldIsRepairedFromCleanPlainWithoutRetiming() {
        val result = LyricsResponse(plainLyric = "aku sendiri", richSyncLyrics = splitAkuSendiri,
            syncedLyrics = "[00:01.10]a ku se n di ri").toLyrics(preferWordTiming = false)!!
        assertEquals("aku sendiri", result.lines!!.single().words)
        assertEquals("1100", result.lines!!.single().startTimeMs)
        assertTrue(result.hasDisplayableLyrics())
    }

    @Test fun allThreeCorruptedFieldsRemainUnconfirmedForTheFallbackResolver() {
        val response = LyricsResponse(plainLyric = splitAkuSendiri, richSyncLyrics = splitAkuSendiri,
            syncedLyrics = "[00:01.10]a ku se n di ri")
        for (wordTiming in listOf(false, true)) {
            val result = response.toLyrics(preferWordTiming = wordTiming)!!
            assertFalse(result.hasDisplayableLyrics())
            assertFalse(result.hasVerifiedWordBoundaries())
        }
    }

    @Test fun derivedSyncedFieldNeedsAnIndependentReferenceEvenForTwoShortFragments() {
        val raw = "[00:01.00]<00:01.00>ta <00:01.40>hu"
        val response = LyricsResponse(plainLyric = raw, syncedLyrics = "[00:01.00]ta hu", richSyncLyrics = raw)
        for (wordTiming in listOf(false, true)) {
            val result = response.toLyrics(preferWordTiming = wordTiming)!!
            assertFalse(result.hasDisplayableLyrics())
            assertEquals(-1, result.lines!!.single().textQualityVersion)
        }
    }

    @Test fun differingPlainTranscriptCanReplaceBrokenSyncedTextWithoutInventingTimings() {
        val result = LyricsResponse(plainLyric = "Aku sendiri!", syncedLyrics = "[00:01.00]a ku se n di ri",
            richSyncLyrics = splitAkuSendiri).toLyrics(preferWordTiming = false)!!
        assertEquals("UNSYNCED", result.syncType)
        assertEquals("Aku sendiri!", result.lines!!.single().words)
        assertTrue(result.hasDisplayableLyrics())
    }

    @Test fun lineDisplayUsesWholeWordsAndTheProvidersLineTimestamp() {
        val result = LyricsResponse(plainLyric = "aku sendiri", richSyncLyrics = splitAkuSendiri,
            syncedLyrics = "[00:01.10]aku sendiri").toLyrics(preferWordTiming = false)!!
        assertEquals("LINE_SYNCED", result.syncType)
        assertEquals("aku sendiri", result.lines!!.single().words)
        assertEquals("1100", result.lines!!.single().startTimeMs)
    }

    @Test fun lineDisplayUsesReadableSyncedTextEvenWhenTheRichTranscriptDiffers() {
        val result = LyricsResponse(plainLyric = "Aku sendiri!", richSyncLyrics = splitAkuSendiri,
            syncedLyrics = "[00:01.10]Aku sendiri!").toLyrics(preferWordTiming = false)!!
        assertEquals("LINE_SYNCED", result.syncType)
        assertEquals("Aku sendiri!", result.lines!!.single().words)
    }

    @Test fun missingLineTimingStillRepairsSyllableSpacesFromThePlainTranscript() {
        val result = LyricsResponse(plainLyric = "aku sendiri", richSyncLyrics = splitAkuSendiri)
            .toLyrics(preferWordTiming = false)!!
        val displayed = result.toSyncedLyrics()
        assertEquals("LINE_SYNCED", displayed.syncType)
        assertEquals("aku sendiri", displayed.lines!!.single().words)
        assertEquals("1000", displayed.lines!!.single().startTimeMs)
    }

    @Test fun mismatchedPlainTranscriptIsPreferredOverUnresolvedSyllablesInLineMode() {
        val result = LyricsResponse(plainLyric = "Aku sendiri!", richSyncLyrics = splitAkuSendiri,
            syncedLyrics = "invalid").toLyrics(preferWordTiming = false)!!
        assertEquals("UNSYNCED", result.syncType)
        assertEquals("Aku sendiri!", result.lines!!.single().words)
    }

    @Test fun richOnlyResponseIsRetainedWhenNoWholeLineTranscriptExists() {
        val result = LyricsResponse(richSyncLyrics = splitAkuSendiri).toLyrics(preferWordTiming = false)!!
        assertEquals("RICH_SYNCED", result.syncType)
        assertEquals("1000", result.lines!!.single().startTimeMs)
        assertEquals("a ku se n di ri", result.lines!!.single().words.replace(Regex("<[^>]*>"), ""))
    }

    @Test fun missingReferenceDoesNotMarkSpacedSyllablesAsFormatted() {
        val result = LyricsResponse(plainLyric = "", richSyncLyrics = "[00:01.00]<00:01.00>a <00:02.00>ku").toLyrics()!!
        assertEquals(0, result.lines!!.single().richSyncTextVersion)
        assertEquals("RICH_SYNCED", result.syncType)
    }
    @Test fun unreadableRichPayloadCannotSuppressReadableSyncedLyrics() {
        for (rich in listOf(" \n\t", "invalid rich payload", "[00:01.00]<00:01.00><00:02.00>")) {
            val result = LyricsResponse(plainLyric = "Aku bersamamu", richSyncLyrics = rich,
                syncedLyrics = "[00:01.00]Aku bersamamu").toLyrics()!!
            assertEquals("LINE_SYNCED", result.syncType)
            assertEquals("Aku bersamamu", result.lines!!.single().words)
        }
    }

    @Test fun malformedTimedFieldsFallBackToReadablePlainLyrics() {
        val result = LyricsResponse(plainLyric = "Aku bersamamu", richSyncLyrics = "invalid",
            syncedLyrics = "invalid").toLyrics()!!
        assertEquals("UNSYNCED", result.syncType)
        assertEquals("Aku bersamamu", result.lines!!.single().words)
    }

    @Test fun voiceMetadataIsRemovedBeforeTheResponseIsCached() {
        val result = LyricsResponse(plainLyric = "aku", richSyncLyrics = "[00:01.00]v2:<00:01.00>a <00:02.00>ku").toLyrics()!!
        assertEquals("aku", result.lines!!.single().words.replace(Regex("<[^>]*>"), ""))
        assertEquals("1000", result.lines!!.single().startTimeMs)
        assertEquals("RICH_SYNCED", result.syncType)
    }

    @Test fun naturallyWholeWordResponseRetainsOriginalTextAndTimingWhenReferenceCaseDiffers() {
        val rich = "<00:24.85>Suatu <00:25.92>malam <00:27.50>Adam <00:28.57>bercerita"
        val result = LyricsResponse(plainLyric = "Suatu malam adam bercerita",
            syncedLyrics = "[00:24.85]Suatu malam adam bercerita", richSyncLyrics = "[00:24.85]$rich").toLyrics()!!
        assertEquals(rich, result.lines!!.single().words)
        assertEquals("24850", result.lines!!.single().startTimeMs)
        assertEquals("RICH_SYNCED", result.syncType)
    }

    @Test fun realSyllablePayloadIsReconciledBeforeCachingAndRendering() {
        val result = LyricsResponse(plainLyric = "Suatu malam Adam bercerita",
            syncedLyrics = "[00:23.02] Suatu malam Adam bercerita",
            richSyncLyrics = "[00:22.96] <00:22.96>Su <00:23.68>a <00:24.04>tu <00:24.99>ma <00:25.33>lam <00:25.76>A <00:26.17>dam <00:26.58>ber <00:27.44>ce <00:27.85>ri <00:28.24>ta").toLyrics()!!
        val line = result.lines!!.single()
        assertEquals("RICH_SYNCED", result.syncType)
        assertEquals("Suatu malam Adam bercerita", line.words.replace(Regex("<[^>]*>"), ""))
        assertEquals("22960", line.startTimeMs)
        assertEquals(com.tan.domain.utils.RICH_SYNC_TEXT_VERSION, line.richSyncTextVersion)
        assertNull(result.gratifyMusicLyrics)
    }

    @Test fun oldCachedJsonDefaultsToLegacyAndNewJsonRetainsFormattingVersion() {
        val json = kotlinx.serialization.json.Json
        val legacy = json.decodeFromString<com.tan.domain.data.model.metadata.Line>("""{"endTimeMs":"3000","startTimeMs":"1000","words":"a ku"}""")
        assertEquals(0, legacy.richSyncTextVersion)
        val current = legacy.copy(richSyncTextVersion = com.tan.domain.utils.RICH_SYNC_TEXT_VERSION)
        assertEquals(current, json.decodeFromString<com.tan.domain.data.model.metadata.Line>(
            json.encodeToString(com.tan.domain.data.model.metadata.Line.serializer(), current)))
    }

    @Test fun richWordTimestampsReachThePlayerWithoutGratifyVotingMetadata() {
        val result = LyricsResponse(id = "simp-id", plainLyric = "Hello world",
            syncedLyrics = "[00:01.00]Hello world",
            richSyncLyrics = "[00:01.00]<00:01.00>Hello <00:01.50>world").toLyrics()
        assertEquals("RICH_SYNCED", result?.syncType)
        assertEquals("1000", result?.lines?.single()?.startTimeMs)
        assertEquals("<00:01.00>Hello <00:01.50>world", result?.lines?.single()?.words)
        assertNull(result?.gratifyMusicLyrics)
    }

    @Test fun emptyRichFieldFallsBackToLineTimestamps() {
        val result = LyricsResponse(plainLyric = "Hello", richSyncLyrics = "", syncedLyrics = "[00:01.00]Hello").toLyrics()
        assertEquals("LINE_SYNCED", result?.syncType)
        assertEquals("1000", result?.lines?.single()?.startTimeMs)
    }

    @Test fun plainLyricsStayUnsyncedRatherThanInventingWordTimes() {
        val result = LyricsResponse(plainLyric = "Hello").toLyrics()
        assertEquals("UNSYNCED", result?.syncType)
        assertEquals("Hello", result?.lines?.single()?.words)
    }
}
