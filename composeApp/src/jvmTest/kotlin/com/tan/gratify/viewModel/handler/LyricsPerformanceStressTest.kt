package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.LyricsProvider
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Opt-in workload audit; the fixture provider has no account/network dependencies. */
class LyricsPerformanceStressTest {
    private fun model() = Lyrics(syncType = "LINE_SYNCED", lines = List(80) {
        Line("${it * 4000 + 4000}", "${it * 4000}", words = "Kau tahu mana urutan satu")
    })

    @Test fun cancellationAndRepeatedStoryClosureReleasePendingLookups(): Unit = runBlocking {
        assumeTrue(System.getenv("GRATIFY_LYRICS_PERFORMANCE_CHECK") == "1")
        val live = AtomicInteger(0)
        val peak = AtomicInteger(0)
        var canceled = 0
        val references = mutableListOf<WeakReference<Lyrics>>()
        repeat(1000) {
            val started = CompletableDeferred<Unit>()
            val job = launch {
                resolveLyricsFallback(listOf(LyricsProvider.SIMPMUSIC, LyricsProvider.LRCLIB), false) {
                    val active = live.incrementAndGet()
                    peak.updateAndGet { maxOf(it, active) }
                    try { started.complete(Unit); awaitCancellation() }
                    finally { live.decrementAndGet(); canceled++ }
                }
            }
            started.await()
            job.cancelAndJoin()
            assertEquals(0, live.get())
        }
        repeat(1000) {
            val result = storyLyricsFlow(DataStoreManager.SIMPMUSIC, false, { null }) { provider ->
                if (provider == LyricsProvider.SIMPMUSIC) model().also { references += WeakReference(it) }
                else {
                    live.incrementAndGet()
                    try { awaitCancellation() } finally { live.decrementAndGet(); canceled++ }
                }
            }.first()
            assertEquals(80, result.lines!!.size)
            assertEquals(0, live.get())
        }
        repeat(4) { System.gc(); System.runFinalization(); delay(100) }
        val surviving = references.count { it.get() != null }
        val order = lyricsProviderOrder(DataStoreManager.SIMPMUSIC, true)
        var timeoutCalls = 0
        val timeoutStart = System.nanoTime()
        assertNull(resolveLyricsFallback(order, false, timeoutMs = 25) {
            timeoutCalls++
            live.incrementAndGet()
            try { awaitCancellation() } finally { live.decrementAndGet() }
        })
        val elapsedMs = (System.nanoTime() - timeoutStart) / 1e6
        assertEquals(order.size, timeoutCalls)
        assertEquals(0, live.get())
        assertTrue(surviving <= 1, "Unexpected retained story lyric models: $surviving")
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val folder = root.resolve(System.getenv("GRATIFY_LYRICS_PERFORMANCE_OUTPUT") ?: "artifacts/lyrics-performance-2026-10-11")
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("cancellation-stress.json"), """
            {
              "scope": "Host JVM production resolver/story flow; synthetic cancellable providers; not complete LyricsHandler/network engine",
              "rapid_cancellations": 1000,
              "story_open_close_cycles": 1000,
              "cancelled_fetches": $canceled,
              "pending_fetches_after_completion": ${live.get()},
              "peak_tracked_fetches": ${peak.get()},
              "tracked_story_models": ${references.size},
              "story_models_alive_after_gc": $surviving,
              "sequential_timeout_provider_count": $timeoutCalls,
              "test_timeout_per_provider_ms": 25,
              "sequential_timeouts_wall_ms": $elapsedMs,
              "production_timeout_per_provider_ms": 8000,
              "maximum_production_providers": ${order.size}
            }
        """.trimIndent())
    }
}
