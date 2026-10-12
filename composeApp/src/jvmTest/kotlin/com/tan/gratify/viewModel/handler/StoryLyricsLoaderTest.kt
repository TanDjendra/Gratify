package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.LyricsProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class StoryLyricsLoaderTest {
    private fun lyrics(type: String = "LINE_SYNCED") = Lyrics(lines = listOf(Line("4000", "1000", words = "Hello",
        richSyncTextVersion = if (type == "RICH_SYNCED") com.tan.domain.utils.RICH_SYNC_TEXT_VERSION else 0,
        textQualityVersion = if (type == "RICH_SYNCED") com.tan.domain.utils.LYRICS_TEXT_QUALITY_VERSION else 0)), syncType = type)

    @Test fun cacheIsShownEvenWhenAllOnlineProvidersFail() = runBlocking {
        val cached = lyrics()
        assertEquals(listOf(cached), storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { cached }, { null }).toList())
    }

    @Test fun lineLyricsAreDisplayedBeforeTheWordTimingRequestCompletesAndClosingCancelsIt() = runBlocking {
        var enrichmentCancelled = false
        val started = CompletableDeferred<Unit>()
        val flow = storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { null }) { provider ->
            if (provider == LyricsProvider.SIMPMUSIC) lyrics()
            else try { started.complete(Unit); awaitCancellation() } finally { enrichmentCancelled = true }
        }
        val received = CompletableDeferred<Lyrics>()
        val collector = launch { flow.collect { received.complete(it) } }
        assertEquals("LINE_SYNCED", received.await().syncType)
        started.await()
        collector.cancelAndJoin()
        assertTrue(enrichmentCancelled)
    }

    @Test fun invalidCacheDoesNotBlockTheMainProvider() = runBlocking {
        val result = storyLyricsFlow(DataStoreManager.SIMPMUSIC, false,
            { throw IllegalStateException("Unreadable cache") }, { lyrics("RICH_SYNCED") }).single()
        assertEquals("RICH_SYNCED", result.syncType)
    }

    @Test fun richOfflineLyricsAreNotReplacedByLineOnlyResponses() = runBlocking {
        val cached = lyrics("RICH_SYNCED").copy(lines = listOf(Line("4000", "1000", words = "<00:01.00>Hello",
            richSyncTextVersion = com.tan.domain.utils.RICH_SYNC_TEXT_VERSION,
            textQualityVersion = com.tan.domain.utils.LYRICS_TEXT_QUALITY_VERSION)))
        val result = storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { cached }) {
            if (it == LyricsProvider.SIMPMUSIC) lyrics() else null
        }.toList()
        assertEquals(listOf(cached), result)
    }
    @Test fun legacyStoryCacheCanBeRepairedFromFreshWholeWordsWithoutRetiming() = runBlocking {
        val cached = Lyrics(lines = listOf(Line("4000", "1000", words = "<00:01.00>a <00:01.50>ku",
            richSyncTextVersion = 1)), syncType = "RICH_SYNCED")
        val reference = Lyrics(lines = listOf(Line("99000", "95000", words = "aku")), syncType = "LINE_SYNCED")
        val result = storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { cached }) { reference }.toList()
        assertEquals(1, result.size, "The unconfirmed split cache must never flash on screen")
        val repaired = result.last().lines!!.single()
        assertEquals("<00:01.00>a<00:01.50>ku", repaired.words)
        assertEquals("1000", repaired.startTimeMs); assertEquals("4000", repaired.endTimeMs)
        assertEquals(com.tan.domain.utils.RICH_SYNC_TEXT_VERSION, repaired.richSyncTextVersion)
    }

    @Test fun fragmentedLineCacheIsHiddenAndReplacedOnline() = runBlocking {
        val cached = lyrics().copy(lines = listOf(Line("4000", "1000", words = "a ku se n di ri")))
        val result = storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { cached }, { lyrics() }).toList()
        assertEquals(listOf(lyrics()), result)
    }

    @Test fun fragmentedOfflineCacheDoesNotReappearWhenProvidersFail() = runBlocking {
        val cached = lyrics().copy(lines = listOf(Line("4000", "1000", words = "a ku se n di ri")))
        assertTrue(storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { cached }, { null }).toList().isEmpty())
    }
}
