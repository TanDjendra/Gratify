package com.tan.data.sync

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CloudReadRetryTest {
    private fun transportFailure() = HttpRequestException("Synthetic connect timeout", HttpRequestBuilder())

    @Test fun transientConnectionFailuresRecoverWithoutDuplicatingTheSuccessfulRead(): Unit = runBlocking {
        var calls = 0
        assertEquals("snapshot", retryCloudRead(retryDelayMillis = 0) {
            calls++
            if (calls < 3) throw transportFailure()
            "snapshot"
        })
        assertEquals(3, calls)
    }

    @Test fun failedConnectionRetriesAreBounded(): Unit = runBlocking {
        var calls = 0
        assertFailsWith<HttpRequestException> {
            retryCloudRead(retryDelayMillis = 0) { calls++; throw transportFailure() }
        }
        assertEquals(3, calls)
    }

    @Test fun cancellationAndPermanentFailuresAreNeverRetried(): Unit = runBlocking {
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("owner changed"))) {
            var calls = 0
            val actual = assertFails { retryCloudRead(retryDelayMillis = 0) { calls++; throw failure } }
            assertSame(failure, actual)
            assertEquals(1, calls)
        }
    }

    @Test fun aFailedLibraryDoesNotBlockPlaylistsOrReportFullSuccess(): Unit = runBlocking {
        val failure = transportFailure()
        var playlistRestored = false
        assertSame(failure, assertFails {
            restoreLibraryAndPlaylists(library = { throw failure }, playlists = { playlistRestored = true })
        })
        assertTrue(playlistRestored)
    }

    @Test fun cancellationStopsFurtherRecoveryWork(): Unit = runBlocking {
        var playlistRequested = false
        assertFailsWith<CancellationException> {
            restoreLibraryAndPlaylists(library = { throw CancellationException() }, playlists = { playlistRequested = true })
        }
        assertFalse(playlistRequested)
    }
}
