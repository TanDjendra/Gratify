/*
 * Lyrics GET client adapted from maxrave-dev/core (GPL-3.0).
 * Upstream revision: d90b53fbad07ab73cecbfe89b33b533fb9ca78ed
 * service/lyricsService/.../org/simpmusic/lyrics/SimpMusicLyrics.kt
 */
package com.tan.gratify.lyrics

import com.tan.ktorext.encoding.brotli
import com.tan.ktorext.getEngine
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun HttpClientConfig<*>.configureSimpMusicLyrics() {
    expectSuccess = false
    followRedirects = true
    // Lyrics are persisted in the application's database. Avoid a second,
    // unbounded HTTP body cache that retains even expired search responses.
    install(HttpTimeout) {
        requestTimeoutMillis = 8000
        connectTimeoutMillis = 5000
        socketTimeoutMillis = 8000
    }
    install(ContentNegotiation) {
        json(Json { isLenient = true; ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(ContentEncoding) {
        brotli(1.0F)
        gzip(0.9F)
        deflate(0.8F)
    }
}

/** Read-only port of the upstream lyrics client; retains the upstream library's User-Agent. */
class SimpMusicLyrics(
    private val httpClient: HttpClient = HttpClient(getEngine()) { configureSimpMusicLyrics() },
) {
    private val baseUrl = "https://api-lyrics.simpmusic.org/v1/"

    suspend fun findLyricsByVideoId(videoId: String) = httpClient.get(baseUrl + videoId) {
        header(HttpHeaders.Accept, "application/json")
        header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
        header(HttpHeaders.ContentType, "application/json")
    }

    suspend fun findTranslatedLyrics(videoId: String, language: String) =
        httpClient.get(baseUrl + "translated/$videoId/$language") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
            header(HttpHeaders.ContentType, "application/json")
        }

    suspend fun searchLrclibLyrics(track: String, artist: String) =
        httpClient.get("https://lrclib.net/api/search") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
            parameter("q", "$artist $track")
        }

    suspend fun searchBetterLyrics(track: String, artist: String, duration: Int?) =
        httpClient.get("https://lyrics-api.boidu.dev/getLyrics") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
            parameter("s", track)
            parameter("a", artist)
            duration?.takeIf { it > 0 }?.let { parameter("d", it) }
        }

    fun close() = httpClient.close()
}
