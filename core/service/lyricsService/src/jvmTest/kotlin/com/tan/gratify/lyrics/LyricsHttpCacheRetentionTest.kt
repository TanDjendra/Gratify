package com.tan.gratify.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Diagnostic of production cache configuration, with synthetic GET responses only. */
class LyricsHttpCacheRetentionTest {
    @Test fun quantifyDefaultCacheRetentionAcrossDistinctTrackUrls(): Unit = runBlocking {
        assumeTrue(System.getenv("GRATIFY_LYRICS_PERFORMANCE_CHECK") == "1")
        var networkCalls = 0
        val client = HttpClient(MockEngine {
            networkCalls++
            // Both public lyrics endpoints returned no Cache-Control/Expires in the audit.
            respond("x".repeat(4096), HttpStatusCode.OK)
        }) { configureSimpMusicLyrics() }
        try {
            assertNull(client.pluginOrNull(HttpCache), "Database-backed lyrics must not install an unbounded HTTP RAM cache")
            val urls = List(1500) { Url("https://lyrics.fixture.invalid/track/$it") }
            val checkpoints = mutableListOf<String>()
            var totalBytes = 0L
            for ((index, url) in urls.withIndex()) {
                assertEquals(4096, client.get(url).bodyAsText().length)
                if ((index + 1) % 500 == 0) {
                    System.gc(); delay(100)
                    val entries = 0
                    checkpoints += "{\"requests\": ${index + 1}, \"retained_entries\": $entries, \"retained_body_bytes\": $totalBytes}"
                    assertEquals(0, entries)
                }
            }
            client.get(urls.first()).bodyAsText()
            assertEquals(1501, networkCalls)
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve("settings.gradle.kts")) }
            val folder = root.resolve(System.getenv("GRATIFY_LYRICS_PERFORMANCE_OUTPUT") ?: "artifacts/lyrics-performance-2026-10-11")
            Files.createDirectories(folder)
            Files.writeString(folder.resolve("http-cache-retention.json"), """
                {
                  "scope": "Production configureSimpMusicLyrics, Ktor 3.5.0, MockEngine, synthetic GET responses without Cache-Control/Expires",
                  "http_cache_installed": false,
                  "requests": 1500,
                  "payload_bytes_each": 4096,
                  "retained_body_bytes": $totalBytes,
                  "earliest_entry_survives": false,
                  "network_calls_including_cached_reread": $networkCalls,
                  "checkpoints": [${checkpoints.joinToString(",")}],
                  "real_server_growth_measured": false
                }
            """.trimIndent())
        } finally { client.close() }
    }
}
