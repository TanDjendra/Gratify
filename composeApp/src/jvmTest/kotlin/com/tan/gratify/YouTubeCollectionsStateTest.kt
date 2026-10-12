package com.tan.gratify

import com.tan.domain.repository.*
import com.tan.gratify.viewModel.YouTubeCollectionsState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class YouTubeCollectionsStateTest {
    private class Fake : YouTubeLibraryRepository {
        override val connection = MutableStateFlow(YouTubeConnectionState("A", YouTubeConnectionStatus.CONNECTED))
        var requests = mutableListOf<String?>()
        var fetch: suspend (String?) -> YouTubeLibraryPage = { YouTubeLibraryPage(listOf(YouTubeLibraryItem("one", "Private title")), "next") }
        override suspend fun refreshConnection() {}
        override suspend fun beginConnection() = "https://accounts.google.com/"
        override suspend fun playlists(pageToken: String?): YouTubeLibraryPage { requests.add(pageToken); return fetch(pageToken) }
        override suspend fun tracks(playlistId: String, pageToken: String?) = playlists(pageToken)
        override suspend fun disconnect() { connection.value = connection.value.copy(status = YouTubeConnectionStatus.DISCONNECTED) }
    }
    @Test fun pageFailurePreservesCursorAndRetryDoesNotSkipTracks(): Unit = runBlocking {
        val fake = Fake(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val source = YouTubeCollectionsState(fake, scope)
            assertEquals("next", source.state.value.nextPageToken)
            fake.fetch = { throw IllegalStateException("network") }
            source.loadMore(); assertEquals(listOf("one"), source.state.value.items.map { it.id })
            assertEquals("next", source.state.value.nextPageToken)
            fake.fetch = { YouTubeLibraryPage(listOf(YouTubeLibraryItem("two", "Another"))) }
            source.loadMore(); assertEquals(listOf(null, "next", "next"), fake.requests)
            assertEquals(listOf("one", "two"), source.state.value.items.map { it.id })
            assertNull(source.state.value.error); assertNull(source.state.value.nextPageToken)
            source.loadMore(); assertEquals(3, fake.requests.size)
        } finally { scope.cancel() }
    }
    @Test fun accountSwitchCancelsOldRequestAndClearsPrivateMetadata(): Unit = runBlocking {
        val fake = Fake(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<YouTubeLibraryPage>()
        fake.fetch = { gate.await() }
        try {
            val source = YouTubeCollectionsState(fake, scope, "private-playlist", "A")
            assertTrue(source.state.value.loading)
            fake.connection.value = YouTubeConnectionState("B", YouTubeConnectionStatus.CONNECTED)
            gate.complete(YouTubeLibraryPage(listOf(YouTubeLibraryItem("old", "A secret"))))
            yield(); assertTrue(source.state.value.items.isEmpty()); assertFalse(source.state.value.loading)
            source.loadMore(); assertEquals(1, fake.requests.size)
        } finally { scope.cancel() }
    }
    @Test fun disconnectClearsItemsAndReconnectionLoadsAFreshFirstPage(): Unit = runBlocking {
        val fake = Fake(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val source = YouTubeCollectionsState(fake, scope)
            assertEquals(1, source.state.value.items.size)
            fake.disconnect(); assertTrue(source.state.value.items.isEmpty())
            fake.connection.value = YouTubeConnectionState("B", YouTubeConnectionStatus.CONNECTED)
            assertEquals(listOf<String?>(null, null), fake.requests)
            assertEquals(1, source.state.value.items.size)
        } finally { scope.cancel() }
    }
    @Test fun repeatedLoadRequestsAreSingleFlight(): Unit = runBlocking {
        val fake = Fake(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<YouTubeLibraryPage>(); fake.fetch = { gate.await() }
        try {
            val source = YouTubeCollectionsState(fake, scope)
            repeat(5) { source.loadMore() }; assertEquals(1, fake.requests.size)
            gate.complete(YouTubeLibraryPage()); assertFalse(source.state.value.loading)
        } finally { scope.cancel() }
    }
}
