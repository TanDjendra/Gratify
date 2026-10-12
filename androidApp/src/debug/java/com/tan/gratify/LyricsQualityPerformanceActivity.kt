package com.tan.gratify

import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.utils.*
import com.tan.gratify.ui.screen.player.simp.component.LyricsView
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.LyricsProvider
import com.tan.gratify.viewModel.NowPlayingScreenData
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in diagnostic of the current line-only renderer and text-quality functions.
 * Synthetic lyrics; no accounts, network, audio decoder or battery measurement.
 */
class LyricsQualityPerformanceActivity : ComponentActivity() {
    private var data by mutableStateOf<NowPlayingScreenData.LyricsData?>(null)
    private var playing by mutableStateOf(false)
    private var phase by mutableIntStateOf(0)
    private val timeline = MutableStateFlow(TimeLine(1000, 320000, 100, false))
    private val monitor = HandlerThread("LyricsQualityFrames")
    private val frames = mutableListOf<Pair<Long, Long>>()
    @Volatile private var startNs = Long.MAX_VALUE
    @Volatile private var endNs = Long.MIN_VALUE
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        if (vsync >= startNs && vsync < endNs && metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
            synchronized(frames) { frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) to metrics.getMetric(FrameMetrics.DEADLINE) }
        }
    }
    private val report = JSONObject().put("complete", false)
    private val ui = JSONArray()
    private val processing = JSONArray()
    private val retention = JSONArray()
    private var sink = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor.start()
        window.addOnFrameMetricsAvailableListener(listener, Handler(monitor.looper))
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(Color(0xff172f54))) {
                    key(phase) {
                        data?.let { LyricsView(it, timeline, {}, Modifier.fillMaxSize(), animationsEnabled = false, isPlaying = playing) }
                            ?: Text("Pemeriksaan performa", color = Color.White)
                    }
                }
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(100)
                if (playing) timeline.value = timeline.value.copy(current = timeline.value.current + 100)
            }
        }
        lifecycleScope.launch {
            delay(5000)
            report.put("scope", "Android debug emulator; production Simp LyricsView; line-only mode; synthetic 80/500-line fixtures")
                .put("audio_network_battery_included", false)
            report.put("processing", processing).put("ui", ui).put("retention", retention)
            withContext(Dispatchers.Default) {
                for (count in listOf(80, 500)) {
                    val clean = fixture(count)
                    val confirmed = clean.copy(lines = clean.lines!!.map { it.copy(textQualityVersion = 1) })
                    val split = clean.copy(lines = clean.lines!!.map { it.copy(words = "Kau ta hu ma na u rut an sa tu", textQualityVersion = -1) })
                    benchmark("clean_assess_and_guard", count) { clean.assessTextQuality().hasDisplayableLyrics().hashCode() }
                    benchmark("confirmed_cache_assess_and_guard", count) { confirmed.assessTextQuality().hasDisplayableLyrics().hashCode() }
                    benchmark("fragmented_repair_and_guard", count) { split.assessTextQuality().reconcileWordBoundaries(clean).hasDisplayableLyrics().hashCode() }
                    benchmark("display_normalization", count) { clean.lines!!.sumOf { lyricDisplayText(it.words).length } }
                }
            }
            save()
            for (round in 1..3) {
                val scenarios = if (round % 2 == 1) listOf("line_playing", "line_paused", "rich_as_lines") else listOf("rich_as_lines", "line_paused", "line_playing")
                for (name in scenarios) {
                    playing = name != "line_paused"
                    timeline.value = TimeLine(1000, 320000, 100, false)
                    phase++
                    data = displayData(fixture(80, rich = name == "rich_as_lines"))
                    delay(3000)
                    collectMemory()
                    synchronized(frames) { frames.clear() }
                    val before = memory("before")
                    val cpu = Process.getElapsedCpuTime()
                    val elapsed = SystemClock.elapsedRealtime()
                    startNs = System.nanoTime()
                    endNs = startNs + 15_000_000_000L
                    Log.i("LyricsQualityPerf", "UI $round $name")
                    delay(15000)
                    val elapsedMs = SystemClock.elapsedRealtime() - elapsed
                    val cpuMs = Process.getElapsedCpuTime() - cpu
                    startNs = Long.MAX_VALUE
                    delay(150)
                    val sample = synchronized(frames) { frames.toList() }
                    ui.put(JSONObject().put("round", round).put("scenario", name)
                        .put("duration_ms", elapsedMs).put("cpu_ms", cpuMs)
                        .put("cpu_one_core_percent", cpuMs * 100.0 / elapsedMs)
                        .put("before", before).put("after", memory("after"))
                        .put("frames", sample.size).put("deadline_misses", sample.count { it.second > 0 && it.first >= it.second })
                        .put("render_ms", stats(sample.map { it.first })))
                    save()
                }
            }
            data = null; playing = false; phase++
            collectMemory()
            retention.put(memory("ui_disposed_baseline"))
            val refs = mutableListOf<WeakReference<Lyrics>>()
            repeat(60) { cycle ->
                playing = true; phase++
                timeline.value = TimeLine(1000 + cycle * 100L, 320000, 100, false)
                data = trackedData(cycle, refs)
                delay(500)
                data = null; playing = false; phase++
                delay(250)
                if ((cycle + 1) % 10 == 0) {
                    collectMemory()
                    retention.put(memory("disposed_${cycle + 1}").put("fixture_weak_refs_alive", refs.count { it.get() != null }))
                    save()
                }
            }
            withContext(Dispatchers.Default) {
                repeat(2000) { iteration ->
                    val model = fixture(80)
                    refs += WeakReference(model)
                    sink = sink xor model.assessTextQuality().hasDisplayableLyrics().hashCode() xor iteration
                }
            }
            collectMemory()
            retention.put(memory("after_2000_transcripts").put("fixture_weak_refs_alive", refs.count { it.get() != null }).put("tracked_models", refs.size))
            val cpu = Process.getElapsedCpuTime()
            val elapsed = SystemClock.elapsedRealtime()
            delay(15000)
            report.put("disposed_idle_cpu_one_core_percent", (Process.getElapsedCpuTime() - cpu) * 100.0 / (SystemClock.elapsedRealtime() - elapsed))
            collectMemory()
            retention.put(memory("disposed_idle_final").put("fixture_weak_refs_alive", refs.count { it.get() != null }))
            report.put("complete", true).put("sink", sink)
            save()
            Log.i("LyricsQualityPerf", "COMPLETE")
        }
    }

    private fun fixture(count: Int, rich: Boolean = false) = Lyrics(syncType = if (rich) "RICH_SYNCED" else "LINE_SYNCED",
        lines = List(count) { row ->
            val start = row * 4000L
            Line((start + 4000).toString(), start.toString(), words = if (rich) {
                listOf("Kau", "tahu", "mana", "urutan", "satu").mapIndexed { index, word ->
                    val ms = start + index * 500
                    "<%02d:%02d.%02d>%s".format(java.util.Locale.US, ms / 60000, ms / 1000 % 60, ms % 1000 / 10, word)
                }.joinToString(" ")
            } else "Kau tahu mana urutan satu")
        })
    private fun displayData(lyrics: Lyrics) = NowPlayingScreenData.LyricsData(lyrics, lyricsProvider = LyricsProvider.LRCLIB)
    private fun trackedData(cycle: Int, refs: MutableList<WeakReference<Lyrics>>): NowPlayingScreenData.LyricsData {
        val model = fixture(if (cycle % 2 == 0) 80 else 500, rich = cycle % 3 == 0)
        refs += WeakReference(model)
        return displayData(model)
    }
    private fun benchmark(name: String, count: Int, block: () -> Int) {
        repeat(20) { sink = sink xor block() }
        val samples = mutableListOf<Long>()
        val cpu = Debug.threadCpuTimeNanos()
        val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0
        repeat(100) {
            val start = System.nanoTime()
            sink = sink xor block()
            samples += System.nanoTime() - start
        }
        val cpuMs = (Debug.threadCpuTimeNanos() - cpu) / 1e6
        val bytes = (Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0) - allocated
        processing.put(JSONObject().put("name", name).put("lines", count).put("samples", samples.size)
            .put("wall_ms", stats(samples)).put("thread_cpu_ms_per_operation", cpuMs / samples.size)
            .put("process_allocated_bytes_per_operation", bytes.toDouble() / samples.size))
        Log.i("LyricsQualityPerf", "PROCESSING $name $count")
    }
    private fun stats(values: List<Long>): JSONObject {
        val sorted = values.sorted()
        fun p(f: Double): Any = if (sorted.isEmpty()) JSONObject.NULL else sorted[(kotlin.math.ceil(sorted.size * f).toInt() - 1).coerceIn(0, sorted.lastIndex)] / 1e6
        return JSONObject().put("p50", p(.5)).put("p95", p(.95)).put("max", p(1.0))
    }
    private fun memory(label: String): JSONObject {
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val runtime = Runtime.getRuntime()
        return JSONObject().put("label", label).put("pss_kb", memory.totalPss)
            .put("java_live_bytes", runtime.totalMemory() - runtime.freeMemory())
            .put("native_allocated_bytes", Debug.getNativeHeapAllocatedSize())
            .put("gc_count", Debug.getRuntimeStat("art.gc.gc-count"))
            .put("threads", File("/proc/self/task").list()?.size ?: -1)
    }
    private suspend fun collectMemory() {
        repeat(3) { System.gc(); System.runFinalization(); delay(250) }
    }
    private fun save() { File(filesDir, "lyrics-quality-performance.json").writeText(report.toString(2)) }
    override fun onDestroy() {
        window.removeOnFrameMetricsAvailableListener(listener)
        monitor.quitSafely()
        super.onDestroy()
    }
}
