package com.tan.media3.utils

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.TreeSet

@UnstableApi
class ConfigurableCacheEvictorTest {
    private class Fixture {
        val evictor = ConfigurableCacheEvictor()
        val spans = TreeSet<CacheSpan>()
        val removed = mutableListOf<String>()
        val cache: Cache = Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { proxy, method, args ->
            when (method.name) {
                "getKeys" -> spans.map { it.key }.toSet()
                "getCachedSpans" -> TreeSet(spans.filter { it.key == args!![0] })
                "removeSpan" -> {
                    val span = args!![0] as CacheSpan
                    assertTrue(spans.remove(span))
                    removed += span.key
                    evictor.onSpanRemoved(proxy as Cache, span)
                    null
                }
                else -> error("Unexpected cache call ${method.name}")
            }
        } as Cache

        fun add(key: String, touched: Long, length: Long = 10): CacheSpan {
            val span = CacheSpan(key, 0, length, touched, File("$key.cache"))
            spans.add(span)
            evictor.onSpanAdded(cache, span)
            return span
        }
    }

    @Test
    fun delayedPreferencesDoNotDeleteExistingAudio() {
        val fixture = Fixture()
        fixture.add("old", 1)
        fixture.add("recent", 2)
        assertTrue(fixture.removed.isEmpty())
        fixture.evictor.setMaxBytes(fixture.cache, Long.MAX_VALUE)
        assertEquals(2, fixture.spans.size)
    }

    @Test
    fun savedBudgetEvictsOldestAcrossKeysAndContinuesEnforcingLimit() {
        val fixture = Fixture()
        fixture.add("z-oldest", 1)
        fixture.add("a-newest", 3)
        fixture.add("b-middle", 2)
        fixture.evictor.setMaxBytes(fixture.cache, 20)
        assertEquals(listOf("z-oldest"), fixture.removed)
        fixture.add("c-new", 4)
        assertEquals(listOf("z-oldest", "b-middle"), fixture.removed)
        fixture.evictor.setMaxBytes(fixture.cache, 10)
        assertEquals(setOf("c-new"), fixture.spans.map { it.key }.toSet())
    }

    @Test
    fun touchingAudioPreservesItWhenBudgetShrinks() {
        val fixture = Fixture()
        val old = fixture.add("old-but-played", 1)
        fixture.add("other", 2)
        val touched = CacheSpan(old.key, old.position, old.length, 3, old.file)
        fixture.spans.remove(old)
        fixture.spans.add(touched)
        fixture.evictor.onSpanTouched(fixture.cache, old, touched)
        fixture.evictor.setMaxBytes(fixture.cache, 10)
        assertEquals(listOf("other"), fixture.removed)
    }

    @Test
    fun zeroBudgetDisablesCacheAndUnlimitedRestoresWrites() {
        val fixture = Fixture()
        fixture.add("first", 1)
        fixture.evictor.setMaxBytes(fixture.cache, 0)
        fixture.add("second", 2)
        assertTrue(fixture.spans.isEmpty())
        fixture.evictor.setMaxBytes(fixture.cache, Long.MAX_VALUE)
        fixture.add("third", 3)
        assertEquals(setOf("third"), fixture.spans.map { it.key }.toSet())
    }
}
