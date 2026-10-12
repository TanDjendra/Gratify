package com.tan.media3.exoplayer

import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@UnstableApi
class PlaybackPreparationTest {
    private fun player(state: Int, count: Int = 1, error: PlaybackException? = null): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> state
                "getMediaItemCount" -> count
                "getPlayerError" -> error
                else -> null
            }
        } as Player

    @Test fun failedPreloadIsRejectedEvenIfItPreviouslyHadBufferedAudio() {
        assertFalse(isPreparedPreload(player(Player.STATE_READY, error =
            object : PlaybackException("Expired source", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                android.os.Bundle.EMPTY, 0L) {})))
        assertFalse(isPreparedPreload(player(Player.STATE_IDLE)))
        assertFalse(isPreparedPreload(player(Player.STATE_ENDED)))
    }

    @Test fun readyAndStillBufferingPreloadsAreAcceptedButEmptyPlayersAreRejected() {
        assertTrue(isPreparedPreload(player(Player.STATE_READY)))
        assertTrue(isPreparedPreload(player(Player.STATE_BUFFERING)))
        assertFalse(isPreparedPreload(player(Player.STATE_READY, count = 0)))
    }

    @Test fun shuffledPreloadFollowsActualUpcomingQueueInsteadOfSequentialIndices() {
        assertEquals(listOf(7, 1), upcomingPreloadIndices(listOf(0, 5, 7, 1, 4), 5, false, 2))
    }

    @Test fun repeatAllWrapsWithoutPreloadingTheCurrentTrackOrDuplicatingPlayers() {
        assertEquals(listOf(4, 0), upcomingPreloadIndices(listOf(4, 0, 2), 2, true, 2))
        assertEquals(listOf(0), upcomingPreloadIndices(listOf(0, 1), 1, true, 2))
        assertEquals(emptyList<Int>(), upcomingPreloadIndices(listOf(0), 0, true, 2))
    }

    @Test fun endOfQueueAndRemovedCurrentTrackDoNotSpawnPlayers() {
        assertEquals(emptyList<Int>(), upcomingPreloadIndices(listOf(0, 1), 1, false, 2))
        assertEquals(emptyList<Int>(), upcomingPreloadIndices(listOf(0, 1), 8, true, 2))
    }

    @Test fun stalledLookupTimesOutAndCancelsItsNetworkWork(): Unit = runBlocking {
        var cleanedUp = false
        try {
            resolveStreamWithDeadline(30) { try { awaitCancellation() } finally { cleanedUp = true } }
            fail("A stalled source must not wait forever")
        } catch (_: TimeoutCancellationException) {
            assertTrue(cleanedUp)
        }
    }

    @Test fun successfulLookupKeepsItsResolvedUrl(): Unit = runBlocking {
        assertEquals("https://example.test/audio", resolveStreamWithDeadline { "https://example.test/audio" })
    }
}
