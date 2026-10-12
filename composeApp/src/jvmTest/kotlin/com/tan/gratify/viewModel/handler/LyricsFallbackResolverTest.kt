package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.LyricsProvider
import com.tan.domain.utils.reconcileWordBoundaries
import com.tan.domain.utils.hasDisplayableLyrics
import kotlinx.coroutines.*
import kotlin.test.*

class LyricsFallbackResolverTest {
    @Test fun totalBudgetCancelsEnrichmentAndReturnsAlreadySafeLyrics(): Unit = runBlocking {
        var cancelled = false
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.BETTER_LYRICS),
            preferWordTiming = true, timeoutMs = 10000, totalTimeoutMs = 200) { provider ->
            if (provider == LyricsProvider.SIMPMUSIC) lyrics()
            else try { awaitCancellation() } finally { cancelled = true }
        }
        assertEquals("LINE_SYNCED", result?.lyrics?.syncType)
        assertTrue(cancelled)
    }

    @Test fun providerWorkDoesNotRunOnTheUiCaller(): Unit = runBlocking {
        val ui = java.util.concurrent.Executors.newSingleThreadExecutor { Runnable -> Thread(Runnable, "lyrics-test-ui") }.asCoroutineDispatcher()
        try {
            val thread = withContext(ui) {
                var providerThread = ""
                resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC), false) {
                    providerThread = Thread.currentThread().name
                    lyrics()
                }
                providerThread
            }
            assertNotEquals("lyrics-test-ui", thread)
        } finally { ui.close() }
    }

    @Test fun lineModeUsesFreshWholeLinesDespiteDifferentPunctuationAndLineBreaks(): Unit = runBlocking {
        val called = mutableListOf<LyricsProvider>()
        val complete = Lyrics(lines = listOf(Line("3000", "1100", words = "Aku bersamamu!")), syncType = "LINE_SYNCED")
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB, LyricsProvider.YOUTUBE), false) {
            called += it
            if (it == LyricsProvider.SIMPMUSIC) splitLyrics() else complete
        }!!
        assertEquals(complete, result.lyrics)
        assertEquals(LyricsProvider.LRCLIB, result.provider)
        assertEquals(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), called)
    }

    @Test fun lineModeDoesNotKeepPartiallyRepairedRichTextWhenWholeLinesAreAvailable(): Unit = runBlocking {
        val rich = splitLyrics().copy(lines = splitLyrics().lines!! + Line("6000", "4000", words = "<00:04.00>sen <00:04.50>di <00:05.00>ri"))
        val complete = Lyrics(lines = listOf(Line("3000", "1000", words = "aku bersamamu"),
            Line("6000", "4000", words = "sendiri!")), syncType = "LINE_SYNCED")
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), false) {
            if (it == LyricsProvider.SIMPMUSIC) rich else complete
        }!!
        assertEquals(complete, result.lyrics)
    }

    @Test fun lineModeAllowsFreshWholeWordsToReplaceEvenAVerifiedRichCache() {
        assertFalse(keepsFormattedRichCache(splitLyrics(com.tan.domain.utils.RICH_SYNC_TEXT_VERSION),
            lyrics(), preferWordTiming = false))
    }

    private fun splitLyrics(version: Int = 0) = Lyrics(lines = listOf(Line("3000", "1000",
        words = if (version >= com.tan.domain.utils.RICH_SYNC_TEXT_VERSION)
            "<00:01.00>a<00:01.10>ku <00:01.20>ber<00:01.30>sa<00:01.40>ma<00:01.50>mu"
            else "<00:01.00>a <00:01.10>ku <00:01.20>ber <00:01.30>sa <00:01.40>ma <00:01.50>mu",
        richSyncTextVersion = version,
        textQualityVersion = if (version >= com.tan.domain.utils.RICH_SYNC_TEXT_VERSION)
            com.tan.domain.utils.LYRICS_TEXT_QUALITY_VERSION else 0)), syncType = "RICH_SYNCED")

    @Test fun v1CacheCannotSuppressCorrectFreshWords() {
        assertFalse(keepsFormattedRichCache(splitLyrics(1), lyrics()))
    }

    @Test fun unverifiedRichUsesLineReferenceWithoutChangingItsTimings(): Unit = runBlocking {
        val source = splitLyrics()
        val reference = Lyrics(lines = listOf(Line("99000", "95000", words = "aku bersamamu")), syncType = "LINE_SYNCED")
        val calls = mutableListOf<LyricsProvider>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB, LyricsProvider.YOUTUBE), true) {
            calls += it
            if (it == LyricsProvider.SIMPMUSIC) source else reference
        }!!
        assertEquals(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), calls)
        val line = result.lyrics.lines!!.single()
        assertEquals("aku bersamamu", line.words.replace(Regex("<[^>]*>"), ""))
        assertEquals("1000", line.startTimeMs); assertEquals("3000", line.endTimeMs)
        assertEquals(com.tan.domain.utils.RICH_SYNC_TEXT_VERSION, line.richSyncTextVersion)
        assertEquals(LyricsProvider.SIMPMUSIC, result.provider)
        assertEquals(Regex("<[^>]*>").findAll(source.lines!!.single().words).map { it.value }.toList(),
            Regex("<[^>]*>").findAll(line.words).map { it.value }.toList())
    }

    @Test fun formattedResultDoesNotMakeExtraProviderRequests(): Unit = runBlocking {
        val source = splitLyrics(com.tan.domain.utils.RICH_SYNC_TEXT_VERSION)
        val calls = mutableListOf<LyricsProvider>()
        resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), true) { calls += it; source }
        assertEquals(listOf(LyricsProvider.SIMPMUSIC), calls)
    }

    @Test fun mismatchingReferenceDoesNotGuessWordsAndCleanFallbackReplacesUnsafeRich(): Unit = runBlocking {
        val source = splitLyrics()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), true) {
            if (it == LyricsProvider.SIMPMUSIC) source else lyrics()
        }
        assertEquals(lyrics(), result?.lyrics)
        assertEquals(source, source.reconcileWordBoundaries(null))
    }

    @Test fun reportedLineSyncedSyllablesAreRepairedBeforeAnyCandidateIsPublished(): Unit = runBlocking {
        val source = Lyrics(lines = listOf(Line("59000", "52320", words = "Kau ta hu ma na u rut an sa tu")), syncType = "LINE_SYNCED")
        val reference = Lyrics(lines = listOf(Line("60000", "52220", words = "Kau tahu mana urutan satu")), syncType = "LINE_SYNCED")
        val calls = mutableListOf<LyricsProvider>()
        val displayed = mutableListOf<ResolvedLyrics>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB, LyricsProvider.YOUTUBE), false,
            onCandidate = { displayed += it }) { provider ->
            calls += provider
            if (provider == LyricsProvider.SIMPMUSIC) source else reference
        }!!
        assertEquals(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), calls)
        assertEquals("Kau tahu mana urutan satu", result.lyrics.lines!!.single().words)
        assertEquals("52320", result.lyrics.lines!!.single().startTimeMs)
        assertEquals("59000", result.lyrics.lines!!.single().endTimeMs)
        assertEquals(LyricsProvider.SIMPMUSIC, result.provider)
        assertEquals(listOf(result), displayed)
        assertTrue(displayed.all { it.lyrics.hasDisplayableLyrics() })
    }

    @Test fun noCleanProviderReturnsUnavailableInsteadOfPublishingFragments(): Unit = runBlocking {
        val displayed = mutableListOf<ResolvedLyrics>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), false,
            onCandidate = { displayed += it }) {
            Lyrics(lines = listOf(Line("3000", "1000", words = "a ku se n di ri")), syncType = "LINE_SYNCED")
        }
        assertNull(result)
        assertTrue(displayed.isEmpty())
    }

    @Test fun confirmedCachePreventsANewProviderFromReintroducingShortSyllableSpaces(): Unit = runBlocking {
        val cached = Lyrics(syncType = "LINE_SYNCED", lines = listOf(Line("3000", "1000", words = "tahu", textQualityVersion = 1)))
        val fresh = Lyrics(syncType = "LINE_SYNCED", lines = listOf(Line("4000", "2000", words = "ta hu")))
        val displayed = mutableListOf<ResolvedLyrics>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC), false, referenceLyrics = cached,
            onCandidate = { displayed += it }) { fresh }!!
        assertEquals("tahu", result.lyrics.lines!!.single().words)
        assertEquals("2000", result.lyrics.lines!!.single().startTimeMs)
        assertEquals(listOf(result), displayed)
    }

    @Test fun unconfirmedLegacyCacheCannotTeachFreshLyricsTheWrongSpaces(): Unit = runBlocking {
        val cached = Lyrics(syncType = "LINE_SYNCED", lines = listOf(Line("3000", "1000", words = "ta hu")))
        val fresh = Lyrics(syncType = "LINE_SYNCED", lines = listOf(Line("4000", "2000", words = "tahu")))
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC), false, referenceLyrics = cached) { fresh }!!
        assertEquals(fresh, result.lyrics)
    }

    @Test fun partialLineRepairContinuesToAWholeCleanTranscript(): Unit = runBlocking {
        val source = Lyrics(lines = listOf(Line("3000", "1000", words = "a ku se n di ri"),
            Line("6000", "4000", words = "ta hu")), syncType = "LINE_SYNCED")
        val full = Lyrics(lines = listOf(Line("7000", "2000", words = "Aku sendiri, tahu!")), syncType = "LINE_SYNCED")
        val displayed = mutableListOf<ResolvedLyrics>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), false,
            onCandidate = { displayed += it }) { if (it == LyricsProvider.SIMPMUSIC) source else full }!!
        assertEquals(full, result.lyrics)
        assertEquals(listOf(result), displayed)
    }
    @Test fun timestampOnlyCacheIsNotReadableLyrics() {
        assertFalse(Lyrics(lines = listOf(Line("3000", "1000", words = "<00:01.00> <00:02.00>")),
            syncType = "RICH_SYNCED").hasReadableLines())
    }

    @Test fun timestampOnlyResponseContinuesToTheNextProvider(): Unit = runBlocking {
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), true) {
            if (it == LyricsProvider.SIMPMUSIC) Lyrics(lines = listOf(Line("3000", "1000", words = "<00:01.00>")), syncType = "RICH_SYNCED")
            else lyrics()
        }
        assertEquals(LyricsProvider.LRCLIB, result?.provider)
    }

    @Test fun legacyRichCacheCannotSuppressFreshReadableLineLyrics() {
        val legacy = lyrics("RICH_SYNCED").copy(lines = lyrics("RICH_SYNCED").lines!!.map { it.copy(textQualityVersion = 0) })
        assertFalse(keepsFormattedRichCache(legacy, lyrics()))
    }

    @Test fun formattedRichCacheStillWinsOverLineFallbackButAllowsFreshRich() {
        val formatted = Lyrics(lines = listOf(Line("3000", "1000", words = "<00:01.00>Aku",
            richSyncTextVersion = com.tan.domain.utils.RICH_SYNC_TEXT_VERSION,
            textQualityVersion = com.tan.domain.utils.LYRICS_TEXT_QUALITY_VERSION)), syncType = "RICH_SYNCED")
        assertTrue(keepsFormattedRichCache(formatted, lyrics()))
        assertFalse(keepsFormattedRichCache(formatted, lyrics("RICH_SYNCED")))
        assertFalse(keepsFormattedRichCache(formatted.copy(lines = emptyList()), lyrics()))
    }

    private fun lyrics(type: String = "LINE_SYNCED") = Lyrics(lines = listOf(Line("3000", "1000", words = "A new day",
        richSyncTextVersion = if (type == "RICH_SYNCED") com.tan.domain.utils.RICH_SYNC_TEXT_VERSION else 0,
        textQualityVersion = if (type == "RICH_SYNCED") com.tan.domain.utils.LYRICS_TEXT_QUALITY_VERSION else 0)), syncType = type)

    @Test fun failureEmptyResponseAndTimeoutStillReachLaterProviders() = runBlocking {
        val called = mutableListOf<LyricsProvider>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.GRATIFYMUSIC, LyricsProvider.BETTER_LYRICS,
            LyricsProvider.LRCLIB, LyricsProvider.YOUTUBE), true, timeoutMs = 50) { provider ->
            called += provider
            when (provider) {
                LyricsProvider.GRATIFYMUSIC -> throw IllegalStateException("Offline")
                LyricsProvider.BETTER_LYRICS -> { delay(200); lyrics() }
                LyricsProvider.LRCLIB -> Lyrics(lines = emptyList(), syncType = "LINE_SYNCED")
                else -> lyrics()
            }
        }
        assertEquals(LyricsProvider.YOUTUBE, result?.provider)
        assertEquals(4, called.size)
    }

    @Test fun showsLineLyricsThenUpgradesToRealWordTiming() = runBlocking {
        val displayed = mutableListOf<String?>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.GRATIFYMUSIC, LyricsProvider.BETTER_LYRICS,
            LyricsProvider.LRCLIB), true, onCandidate = { displayed += it.lyrics.syncType }) {
            if (it == LyricsProvider.BETTER_LYRICS) lyrics("RICH_SYNCED") else lyrics()
        }
        assertEquals(listOf<String?>("LINE_SYNCED", "RICH_SYNCED"), displayed)
        assertEquals(LyricsProvider.BETTER_LYRICS, result?.provider)
    }

    @Test fun failedWordTimingRetainsReadableLyrics() = runBlocking {
        val result = resolveLyricsFallback(listOf(LyricsProvider.GRATIFYMUSIC, LyricsProvider.BETTER_LYRICS), true) {
            if (it == LyricsProvider.GRATIFYMUSIC) lyrics() else null
        }
        assertEquals("LINE_SYNCED", result?.lyrics?.syncType)
    }

    @Test fun lineModeStopsAfterReadableLyricsWithoutFetchingWordTiming() = runBlocking {
        val called = mutableListOf<LyricsProvider>()
        val result = resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.BETTER_LYRICS,
            LyricsProvider.LRCLIB), false) {
            called += it
            if (it == LyricsProvider.BETTER_LYRICS) lyrics("RICH_SYNCED") else lyrics()
        }
        assertEquals(listOf(LyricsProvider.SIMPMUSIC), called)
        assertEquals("LINE_SYNCED", result?.lyrics?.syncType)
    }

    @Test fun cancellationStopsRequestsForPreviousSong() = runBlocking {
        val called = mutableListOf<LyricsProvider>()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            resolveLyricsFallback(listOf(LyricsProvider.GRATIFYMUSIC, LyricsProvider.BETTER_LYRICS), true) {
                called += it; started.complete(Unit); awaitCancellation()
            }
        }
        started.await(); job.cancelAndJoin()
        assertEquals(listOf(LyricsProvider.GRATIFYMUSIC), called)
    }

    @Test fun explicitProviderIsRespectedAndSpotifyNeedsLogin() = runBlocking {
        val order = lyricsProviderOrder(DataStoreManager.LRCLIB, false)
        assertEquals(LyricsProvider.LRCLIB, order.first())
        assertFalse(LyricsProvider.SPOTIFY in order)
        assertTrue(LyricsProvider.SPOTIFY in lyricsProviderOrder(DataStoreManager.GRATIFYMUSIC, true))
        val called = mutableListOf<LyricsProvider>()
        resolveLyricsFallback(order, false) { called += it; lyrics() }
        assertEquals(listOf(LyricsProvider.LRCLIB), called)
    }

    @Test fun mainAndLegacyDefaultsUseSimpMusicBeforeTheSameUpstreamFallbacks() {
        val order = lyricsProviderOrder(DataStoreManager.SIMPMUSIC, true)
        assertEquals(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.SPOTIFY, LyricsProvider.LRCLIB), order.take(3))
        assertEquals(order, lyricsProviderOrder(DataStoreManager.GRATIFYMUSIC, true))
        val better = lyricsProviderOrder(DataStoreManager.BETTER_LYRICS, false)
        assertEquals(listOf(LyricsProvider.BETTER_LYRICS, LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), better.take(3))
    }
}
