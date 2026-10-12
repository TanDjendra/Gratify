package com.tan.data.cache

import com.tan.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class JsonContentCacheTest {
    private fun fixture(block: suspend (java.nio.file.Path) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("gratify-content-cache")
        try { block(dir) } finally { dir.toFile().deleteRecursively() }
    }
    @Test fun restartUsesDiskWithoutASecondFetch() = fixture { dir ->
        var requests = 0
        fun load(cache: JsonContentCache) = cachedContent(cache, { "account-a:playlist" }, 1000, String.serializer()) {
            requests++; flowOf(Resource.Success("first"))
        }
        assertEquals("first", load(JsonContentCache({ dir.toString().toPath() })).first().data)
        assertEquals("first", load(JsonContentCache({ dir.toString().toPath() })).first().data)
        assertEquals(1, requests)
    }
    @Test fun expiredFirstConsumerRefreshesInsteadOfKeepingStaleForever() = fixture { dir ->
        var time = 1000L
        val cache = JsonContentCache({ dir.toString().toPath() }, { time })
        cache.write("key", "old", String.serializer()); time += 2000
        val result = cachedContent(cache, { "key" }, 1000, String.serializer()) { flowOf(Resource.Success("new")) }.first()
        assertEquals("new", result.data)
        assertEquals("new", cache.read("key", 1000, String.serializer()))
    }
    @Test fun offlineFallbackExpiresAndNeverCrossesAnAccount() = fixture { dir ->
        var time = 1000L
        val cache = JsonContentCache({ dir.toString().toPath() }, { time })
        cache.write("owner-a", "private-metadata", String.serializer()); time += 2000
        fun load(key: String) = cachedContent(cache, { key }, 1000, String.serializer()) { flowOf(Resource.Error<String>("offline")) }
        assertEquals("private-metadata", load("owner-a").first().data)
        assertTrue(load("owner-b").first() is Resource.Error)
        time += 8L * 24 * 60 * 60 * 1000
        assertTrue(load("owner-a").first() is Resource.Error)
    }
    @Test fun corruptionAndOversizedEntriesDoNotBreakFreshLoads() = fixture { dir ->
        val cache = JsonContentCache({ dir.toString().toPath() })
        cache.write("key", "valid", String.serializer())
        Files.list(dir).use { files -> Files.writeString(files.findFirst().get(), "broken") }
        assertNull(cache.read("key", 1000, String.serializer()))
        cache.write("big", "x".repeat(600_000), String.serializer())
        assertNull(cache.read("big", 1000, String.serializer()))
        assertEquals("fresh", cachedContent(cache, { "key" }, 1000, String.serializer()) { flowOf(Resource.Success("fresh")) }.first().data)
    }
    @Test fun accountSwitchCancelsBeforePersistingResponse() = fixture { dir ->
        val cache = JsonContentCache({ dir.toString().toPath() })
        var owner = "a"
        assertFailsWith<CancellationException> {
            cachedContent(cache, { owner }, 1000, String.serializer()) {
                flow { owner = "b"; emit(Resource.Success("old-account")) }
            }.first()
        }
        assertNull(cache.read("a", 1000, String.serializer()))
        assertNull(cache.read("b", 1000, String.serializer()))
    }
    @Test fun evictionBoundsDiskUsage() = fixture { dir ->
        val cache = JsonContentCache({ dir.toString().toPath() }, maxEntries = 2, maxBytes = 1500)
        repeat(10) { cache.write("$it", "x".repeat(400), String.serializer()) }
        val files = Files.list(dir).use { it.toList() }
        assertTrue(files.size <= 2)
        assertTrue(files.sumOf { Files.size(it) } <= 1500)
    }
}
