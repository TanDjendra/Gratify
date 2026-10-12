package com.tan.gratify.ui.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.*
import com.tan.domain.utils.reconcileRichSyncWords
import com.tan.gratify.extension.parseRichSyncWords
import com.tan.gratify.extension.displayText
import com.tan.gratify.ui.benchmark.upstream.parseRichSyncWords as upstreamParse
import com.tan.gratify.ui.benchmark.upstream.RichSyncLyricsLineItem as UpstreamLine
import com.tan.gratify.ui.screen.player.simp.component.RichSyncLyricsLineItem
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Compare active-line components on host; this does not measure Android frame deadlines. */
@OptIn(ExperimentalComposeUiApi::class)
class ManguLyricsAnimationBenchmark {
    private val source = "<00:22.96>Su <00:23.68>a <00:24.04>tu <00:24.99>ma <00:25.33>lam <00:25.76>A <00:26.17>dam <00:26.58>ber <00:27.44>ce <00:27.85>ri <00:28.24>ta"
    private fun root() = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }
    private fun stats(values: List<Long>) = buildJsonObject {
        val sorted = values.sorted()
        put("samples", sorted.size)
        put("medianMs", sorted[sorted.size / 2] / 1_000_000.0)
        put("p95Ms", sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1_000_000.0)
        put("p99Ms", sorted[(sorted.size * .99).toInt().coerceAtMost(sorted.lastIndex)] / 1_000_000.0)
        put("maxMs", sorted.last() / 1_000_000.0)
    }

    @Test fun compareOriginalActiveRendererWithCorrectedProductionRenderer(): Unit = runBlocking(Dispatchers.Swing) {
        assumeTrue(System.getenv("GRATIFY_LYRICS_PERFORMANCE_CHECK") == "1")
        val original = upstreamParse(source, "22960", "29000")!!
        val corrected = parseRichSyncWords(reconcileRichSyncWords(source, "Suatu malam Adam bercerita")!!, "22960", "29000")!!
        assertEquals(original.words.map { it.startTimeMs }, corrected.words.map { it.startTimeMs })
        assertEquals("Su a tu ma lam A dam ber ce ri ta", original.words.joinToString(" ") { it.text })
        assertEquals("Suatu malam Adam bercerita", corrected.displayText())
        val folder = root().resolve("artifacts/lyrics-performance-2026-10-03")
        Files.createDirectories(folder)
        val results = mutableListOf<JsonObject>()
        val originalSamples = mutableListOf<Long>(); val correctedSamples = mutableListOf<Long>()
        val cpuClock = ManagementFactory.getThreadMXBean()
        val csv = StringBuilder("variant,round,frame,render_ns,thread_cpu_ns\n")
        // Reverse order on alternating rounds to reduce warmup/order bias.
        repeat(3) { round ->
            val variants = if (round % 2 == 0) listOf(false, true) else listOf(true, false)
            for (useCorrection in variants) {
                val name = if (useCorrection) "gratify-corrected" else "simpmusic-original-component"
                val position = mutableLongStateOf(21500L)
                val scene = ImageComposeScene(390, 220, Density(1f), coroutineContext = coroutineContext) {
                    AppTheme {
                        Column(Modifier.fillMaxSize().background(Color(0xFF086D72)).padding(20.dp)) {
                            Text("Mangu · Fourtwnty", color = Color.White)
                            if (useCorrection) RichSyncLyricsLineItem(parsedLine = corrected, translatedWords = null,
                                currentTimeMs = position.longValue, isCurrent = true, isPlaying = true,
                                customFontSize = 32.sp, glow = Shadow(Color.White),
                                pendingColorOverride = Color(0xFF9B9B9B), wrappedLineSpacing = 12.dp)
                            else UpstreamLine(parsedLine = original, translatedWords = null,
                                currentTimeMs = position.longValue, isCurrent = true,
                                customFontSize = 32.sp, glow = Shadow(Color.White),
                                pendingColorOverride = Color(0xFF9B9B9B), wrappedLineSpacing = 12.dp)
                        }
                    }
                }
                val timings = mutableListOf<Long>(); val cpu = mutableListOf<Long>()
                try {
                    repeat(60) { frame ->
                        position.longValue = 21500L + frame * 50 / 3
                        scene.render(frame * 16_666_667L).close(); delay(16)
                    }
                    repeat(300) { frame ->
                        if (frame % 3 == 0) position.longValue = 22500L + frame * 50 / 3
                        val cpuStart = if (cpuClock.isCurrentThreadCpuTimeSupported) cpuClock.currentThreadCpuTime else -1L
                        val start = System.nanoTime()
                        scene.render((frame + 60) * 16_666_667L).close()
                        val elapsed = System.nanoTime() - start
                        val cpuElapsed = if (cpuStart >= 0) cpuClock.currentThreadCpuTime - cpuStart else -1L
                        timings += elapsed
                        if (cpuElapsed >= 0) cpu += cpuElapsed
                        csv.append("$name,${round + 1},$frame,$elapsed,$cpuElapsed\n")
                        if (round == 0 && frame == 150) scene.render((frame + 60) * 16_666_667L).use { image ->
                            image.encodeToData()!!.use { data -> Files.write(folder.resolve("$name.png"), data.bytes) }
                        }
                        delay(16)
                    }
                } finally { scene.close() }
                (if (useCorrection) correctedSamples else originalSamples).addAll(timings)
                results += buildJsonObject {
                    put("variant", name); put("round", round + 1)
                    put("renderCall", stats(timings)); if (cpu.isNotEmpty()) put("renderThreadCpu", stats(cpu))
                }
            }
        }
        val report = buildJsonObject {
            put("scope", "Host JVM ImageComposeScene, active line only, software/offscreen renderer; not an Android APK")
            put("input", "User-reported Mangu first rich line, 11 original timed fragments")
            put("width", 390); put("fontSp", 32); put("measuredFramesEach", 900)
            put("warmupFramesPerRun", 60); put("timelineTickMs", 50); put("nominalFrameStepNs", 16666667)
            put("original", stats(originalSamples)); put("corrected", stats(correctedSamples))
            put("rounds", JsonArray(results))
            put("limitations", "Render-call duration excludes coroutine work outside the call, delay and PNG encoding. No Android FPS, GPU, full player, audio decode, memory-leak or battery measurements.")
            put("apkBuilt", false)
        }
        Files.writeString(folder.resolve("animation.json"), Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        Files.writeString(folder.resolve("render-samples.csv"), csv.toString())
    }
}
