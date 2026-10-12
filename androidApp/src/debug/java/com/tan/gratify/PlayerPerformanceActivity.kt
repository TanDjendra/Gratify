package com.tan.gratify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.gratify.ui.component.LyricsView
import com.tan.gratify.ui.screen.player.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.LyricsProvider
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.viewModel.UIEvent
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only renderer probe. Uses production UI with synthetic time, without audio/network.
 * Results are diagnostic, not a release benchmark or a physical-device battery measurement.
 */
class PlayerPerformanceActivity : ComponentActivity() {
    private data class Scenario(val name: String, val sync: String, val playing: Boolean = true,
        val isolated: Boolean = false, val apple: Boolean = true, val seek: Boolean = false)
    private data class Sample(val total: Long, val deadline: Long, val gpu: Long, val draw: Long,
        val layout: Long)
    private val scenarios = listOf(
        Scenario("now_playing", "MUSIC"),
        Scenario("lyrics_line", "LINE_SYNCED"),
        Scenario("lyrics_rich", "RICH_SYNCED"),
        Scenario("lyrics_paused", "RICH_SYNCED", playing = false),
        Scenario("lyrics_seek", "RICH_SYNCED", seek = true),
        Scenario("lyrics_unsynced", "UNSYNCED"),
        Scenario("classic_line_isolated", "LINE_SYNCED", isolated = true, apple = false),
        Scenario("apple_line_isolated", "LINE_SYNCED", isolated = true),
        Scenario("classic_rich_isolated", "RICH_SYNCED", isolated = true, apple = false),
        Scenario("apple_rich_isolated", "RICH_SYNCED", isolated = true),
    )
    private val samples = mutableListOf<Sample>()
    private var dropped = 0
    private val monitor = HandlerThread("PlayerFrameMetrics")
    @Volatile private var startNs = Long.MAX_VALUE
    @Volatile private var endNs = Long.MIN_VALUE
    private var scenario by mutableStateOf(scenarios.first())
    private var phase by mutableIntStateOf(0)
    private val timeline = MutableStateFlow(TimeLine(1000, 320000, 100, false))
    private val listener = Window.OnFrameMetricsAvailableListener { _, frame, dropCount ->
        val vsync = frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        if (vsync >= startNs && vsync < endNs && frame.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
            synchronized(samples) {
                samples += Sample(frame.getMetric(FrameMetrics.TOTAL_DURATION),
                    if (Build.VERSION.SDK_INT >= 31) frame.getMetric(FrameMetrics.DEADLINE) else -1L,
                    if (Build.VERSION.SDK_INT >= 31) frame.getMetric(FrameMetrics.GPU_DURATION) else -1L,
                    frame.getMetric(FrameMetrics.DRAW_DURATION), frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                dropped += dropCount
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        monitor.start()
        window.addOnFrameMetricsAvailableListener(listener, Handler(monitor.looper))
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.drawColor(0xff34558a.toInt())
            canvas.drawCircle(650f, 400f, 320f, Paint().apply { color = 0xffcad7e2.toInt() })
            canvas.drawRect(0f, 740f, 1024f, 1024f, Paint().apply { color = 0xff172f54.toInt() })
        }.asImageBitmap()
        val tracks = listOf(Track(null, listOf(Artist("fixture", "Nadira")), "5:20", 320,
            true, false, null, null, "Langkah menuju cahaya", "fixture", null, null, null, null))
        setContent {
            AppTheme {
                key(phase) {
                    val data = remember(scenario.sync) { fixture(scenario.sync) }
                    val screen = remember(data) { NowPlayingScreenData("Uji performa", "Langkah menuju cahaya",
                        "Nadira", false, thumbnailURL = null, lyricsData = data, bitmap = bitmap) }
                    if (scenario.isolated) {
                        Box(Modifier.fillMaxSize().background(Color(0xff172f54))) {
                            LyricsView(data, timeline, {}, Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                appleMusicStyle = scenario.apple, isPlaying = scenario.playing)
                        }
                    } else {
                        AppleMusicPlayerContent(AppleMusicPlayerState(screen,
                            ControlState(scenario.playing, false, RepeatState.None, false, true, true, false, 1f),
                            timeline, tracks, 0, Color(0xff34558a),
                            if (scenario.sync == "MUSIC") AppleMusicPlayerTab.MUSIC else AppleMusicPlayerTab.LYRICS,
                            rememberPagerState { tracks.size }),
                            AppleMusicPlayerActions({ event ->
                                if (event is UIEvent.UpdateProgress) timeline.value = timeline.value.copy(
                                    current = (event.newProgress * timeline.value.total / 100).toLong())
                            }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
                    }
                }
            }
        }
        lifecycleScope.launch {
            var tick = 0
            while (true) {
                delay(100)
                tick++
                if (scenario.playing) {
                    val next = if (scenario.seek && tick % 20 == 0) 1500L else timeline.value.current + 100
                    timeline.value = timeline.value.copy(current = next)
                }
            }
        }
        lifecycleScope.launch {
            val report = JSONArray()
            val repeats = intent.getIntExtra("repeats", 3).coerceIn(1, 5)
            val duration = intent.getLongExtra("duration_ms", 12000L).coerceIn(3000, 60000)
            delay(5000) // Application startup and resource initialization are outside the samples.
            repeat(repeats) { run ->
                // Reverse order on the second pass to expose warm-up/order bias.
                val order = if (run % 2 == 0) scenarios else scenarios.reversed()
                for (case in order) {
                    startNs = Long.MAX_VALUE
                    endNs = Long.MIN_VALUE
                    scenario = case
                    timeline.value = TimeLine(if (case.playing) 1000 else 1500, 320000, 100, false)
                    phase++
                    delay(3000)
                    synchronized(samples) { samples.clear(); dropped = 0 }
                    val cpu = Process.getElapsedCpuTime()
                    val gc = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L
                    val elapsed = SystemClock.elapsedRealtime()
                    startNs = System.nanoTime()
                    endNs = startNs + duration * 1_000_000
                    Log.i("PlayerPerf", "START ${run + 1} ${case.name}")
                    delay(duration)
                    val cpuMs = Process.getElapsedCpuTime() - cpu
                    val elapsedMs = SystemClock.elapsedRealtime() - elapsed
                    startNs = Long.MAX_VALUE
                    endNs = Long.MIN_VALUE
                    delay(100) // Drain callbacks before reading the collected samples.
                    val snapshot = synchronized(samples) { samples.toList() }
                    val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    val result = JSONObject().put("run", run + 1).put("scenario", case.name)
                        .put("duration_ms", elapsedMs).put("cpu_ms", cpuMs)
                        .put("cpu_one_core_percent", cpuMs * 100.0 / elapsedMs)
                        .put("pss_kb", memory.totalPss).put("java_heap_kb", memory.dalvikPss)
                        .put("native_heap_kb", memory.nativePss)
                        .put("gc_count", (Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L) - gc)
                        .put("frames", snapshot.size).put("callback_drops", dropped)
                        .put("deadline_misses", snapshot.count { it.deadline > 0 && it.total >= it.deadline })
                        .put("total_ms", values(snapshot.map { it.total }))
                        .put("gpu_ms", values(snapshot.map { it.gpu }))
                        .put("draw_ms", values(snapshot.map { it.draw }))
                        .put("layout_ms", values(snapshot.map { it.layout }))
                        .put("deadline_ms", values(snapshot.map { it.deadline }))
                    report.put(result)
                    File(filesDir, "player-performance.json").writeText(JSONObject()
                        .put("complete", false).put("samples", report).toString(2))
                    Log.i("PlayerPerf", "RESULT $result")
                }
            }
            File(filesDir, "player-performance.json").writeText(JSONObject()
                .put("complete", true).put("samples", report).toString(2))
            Log.i("PlayerPerf", "COMPLETE")
        }
    }

    private fun values(values: List<Long>): JSONObject {
        val sorted = values.filter { it >= 0 }.sorted()
        fun percentile(fraction: Double) = if (sorted.isEmpty()) JSONObject.NULL else
            sorted[(kotlin.math.ceil(sorted.size * fraction).toInt() - 1).coerceIn(0, sorted.lastIndex)] / 1_000_000.0
        return JSONObject().put("count", sorted.size).put("p50", percentile(.5))
            .put("p95", percentile(.95)).put("p99", percentile(.99))
            .put("max", sorted.lastOrNull()?.let { it / 1_000_000.0 } ?: JSONObject.NULL)
            .put("raw_ns", JSONArray(sorted))
    }

    private fun fixture(sync: String): NowPlayingScreenData.LyricsData {
        val words = listOf("Langkah", "kita", "menuju", "cahaya", "bersama", "dalam", "irama", "senja")
        val lines = (0 until 80).map { row ->
            val start = row * 4000L
            val text = if (sync == "RICH_SYNCED") words.mapIndexed { index, word ->
                val ms = start + index * 500
                "<%02d:%02d.%02d> %s".format(java.util.Locale.US, ms / 60000, (ms / 1000) % 60, (ms % 1000) / 10, word)
            }.joinToString(" ") else words.joinToString(" ")
            Line((start + 4000).toString(), start.toString(), words = text)
        }
        return NowPlayingScreenData.LyricsData(Lyrics(lines = lines,
            syncType = if (sync == "MUSIC") "LINE_SYNCED" else sync), lyricsProvider = LyricsProvider.LRCLIB)
    }

    override fun onDestroy() {
        window.removeOnFrameMetricsAvailableListener(listener)
        monitor.quitSafely()
        super.onDestroy()
    }
}
