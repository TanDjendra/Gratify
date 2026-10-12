package com.tan.gratify.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class SimpMusicLyricsClientTest {
    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK,
        inspect: (HttpRequestData) -> Unit = {}): SimpMusicLyricsClient = SimpMusicLyricsClient(
        SimpMusicLyrics(HttpClient(MockEngine { request ->
            inspect(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureSimpMusicLyrics() }),
    )

    @Test fun videoIdRequestMatchesUpstreamAndRetainsRichTiming(): Unit = runBlocking {
        val client = client("""{"success":true,"data":[{"id":"upstream-id","videoId":"fJ9rUzIMcZQ","plainLyric":"Hello world","syncedLyrics":"[00:01.00]Hello world","richSyncLyrics":"[00:01.00]<00:01.00>Hello <00:01.50>world"}]}""") {
            assertEquals(HttpMethod.Get, it.method)
            assertEquals("https://api-lyrics.simpmusic.org/v1/fJ9rUzIMcZQ", it.url.toString())
            assertEquals("SimpMusicLyrics/1.0", it.headers[HttpHeaders.UserAgent])
            assertNull(it.headers[HttpHeaders.Authorization])
            assertNull(it.headers["X-HMAC"])
        }
        try {
            val lyric = client.getLyrics("fJ9rUzIMcZQ").getOrThrow().single()
            assertTrue(lyric.richSyncLyrics!!.contains("<00:01.50>"))
        } finally { client.close() }
    }

    @Test fun translationUsesTheUpstreamEndpoint(): Unit = runBlocking {
        val client = client("""{"success":true,"data":{"id":"translation","videoId":"fJ9rUzIMcZQ","translatedLyric":"[00:01.00]Halo","language":"id","vote":0,"contributor":"","contributorEmail":""}}""") {
            assertEquals("/v1/translated/fJ9rUzIMcZQ/id", it.url.encodedPath)
        }
        try {
            assertEquals("[00:01.00]Halo", client.getTranslatedLyrics("fJ9rUzIMcZQ", "id").getOrThrow().translatedLyric)
            assertTrue(client.getTranslatedLyrics("fJ9rUzIMcZQ", "invalid").isFailure)
        } finally { client.close() }
    }

    @Test fun deniedAndEmptyResponsesBecomeFailures(): Unit = runBlocking {
        val denied = client("""{"success":false,"error":{"error":true,"code":403,"reason":"Unavailable"}}""", HttpStatusCode.Forbidden)
        val empty = client("""{"success":true,"data":null}""")
        try {
            assertTrue(denied.getLyrics("fJ9rUzIMcZQ").isFailure)
            assertTrue(empty.getLyrics("fJ9rUzIMcZQ").isFailure)
        } finally { denied.close(); empty.close() }
    }

    @Test fun lrclibUsesTheCombinedQueryAndKeepsSyncedLyricsWhenDurationUnknown(): Unit = runBlocking {
        val client = client("""[{"id":1,"name":"Song","trackName":"Song","artistName":"Artist","albumName":null,"duration":180.0,"instrumental":false,"plainLyrics":"Hello","syncedLyrics":"[00:01.00]Hello"}]""") {
            assertEquals("lrclib.net", it.url.host)
            assertEquals("Artist Song", it.url.parameters["q"])
            assertNull(it.url.parameters["track_name"])
        }
        try {
            assertEquals("LINE_SYNCED", client.searchLrclibLyrics("Song", "Artist", 0).getOrThrow()?.lyrics?.syncType)
        } finally { client.close() }
    }

    @Test fun betterLyricsUsesUpstreamParametersAndReturnsTtml(): Unit = runBlocking {
        val client = client("""{"ttml":"<tt><body/></tt>"}""") {
            assertEquals("lyrics-api.boidu.dev", it.url.host)
            assertEquals("Song", it.url.parameters["s"])
            assertEquals("Artist", it.url.parameters["a"])
            assertEquals("180", it.url.parameters["d"])
        }
        try { assertEquals("<tt><body/></tt>", client.searchBetterLyrics("Song", "Artist", 180).getOrThrow()) }
        finally { client.close() }
    }

    @Test fun cancellationDoesNotTurnIntoAProviderFailure(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val client = SimpMusicLyricsClient(SimpMusicLyrics(HttpClient(MockEngine {
            started.complete(Unit)
            awaitCancellation()
        }) { configureSimpMusicLyrics() }))
        var returned = false
        try {
            val job = launch { client.getLyrics("fJ9rUzIMcZQ"); returned = true }
            started.await()
            job.cancelAndJoin()
            assertFalse(returned)
        } finally { client.close() }
    }
}
