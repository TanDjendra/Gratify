package com.tan.data

import com.tan.data.mapping.toLyrics
import com.tan.gratify.lyrics.SimpMusicLyricsClient
import com.tan.data.benchmark.upstream.parseRichSyncLyrics as upstreamRich
import com.tan.data.benchmark.upstream.parseTtmlLyrics as upstreamTtml
import com.tan.gratify.lyrics.parser.parseTtmlLyrics
import com.tan.domain.utils.richSyncTextSignature
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import kotlin.test.*

/** Manual host benchmark, not an Android FPS or battery test. */
class ManguLyricsProcessingBenchmark {
    private fun root() = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }
    private fun stats(samples: List<Long>) = buildJsonObject {
        val sorted = samples.sorted()
        put("medianMs", sorted[sorted.size / 2] / 1_000_000.0)
        put("p95Ms", sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1_000_000.0)
        put("maxMs", sorted.last() / 1_000_000.0)
    }

    @Test fun compareSameLiveResponseUsingOriginalParserAndProductionMapping(): Unit = runBlocking {
        assumeTrue(System.getenv("GRATIFY_LYRICS_NETWORK_CHECK") == "1")
        val client = SimpMusicLyricsClient()
        try {
            val input = client.getLyrics("JENpTmMQBQY").getOrThrow().first { !it.richSyncLyrics.isNullOrBlank() }
            val rich = input.richSyncLyrics!!
            val baseline = upstreamRich(rich).toLyrics()
            val corrected = input.toLyrics()!!
            val phrase = "Suatu malam Adam bercerita"
            val oldLine = baseline.lines!!.first { it.startTimeMs == "22960" }
            val newLine = corrected.lines!!.first { it.startTimeMs == "22960" }
            val tags = Regex("<[^>]*>")
            assertEquals("Su a tu ma lam A dam ber ce ri ta", oldLine.words.replace(tags, ""))
            assertEquals(phrase, newLine.words.replace(tags, ""))
            assertEquals(baseline.lines!!.map { it.startTimeMs }, corrected.lines!!.map { it.startTimeMs })
            assertEquals(baseline.lines!!.map { tags.findAll(it.words).map { tag -> tag.value }.toList() },
                corrected.lines!!.map { tags.findAll(it.words).map { tag -> tag.value }.toList() })

            val before = mutableListOf<Long>(); val after = mutableListOf<Long>()
            val stdout = System.out
            val silent = PrintStream(OutputStream.nullOutputStream())
            // Existing parsers log a sample. Exclude console I/O from both measurements.
            try {
                System.setOut(silent)
                repeat(50) { upstreamRich(rich).toLyrics(); input.toLyrics() }
                repeat(300) { index ->
                    fun measureOriginal() { val start = System.nanoTime(); upstreamRich(rich).toLyrics(); before += System.nanoTime() - start }
                    fun measureCorrected() { val start = System.nanoTime(); input.toLyrics(); after += System.nanoTime() - start }
                    if (index % 2 == 0) { measureOriginal(); measureCorrected() }
                    else { measureCorrected(); measureOriginal() }
                }
            } finally { System.setOut(stdout); silent.close() }

            // Independently check the alternate provider with the same title/artist/duration.
            val better = client.searchBetterLyrics("Mangu", "Fourtwnty", 262).getOrNull()
            fun matchingLine(lines: List<com.tan.gratify.lyrics.domain.Lyrics.LyricsX.Line>?) =
                lines.orEmpty().firstOrNull { richSyncTextSignature(it.words) == richSyncTextSignature(phrase) }
            val betterOld = better?.let { matchingLine(upstreamTtml(it).lyrics?.lines) }
            val betterNew = better?.let { matchingLine(parseTtmlLyrics(it).lyrics?.lines) }
            val report = buildJsonObject {
                put("scope", "Host JVM parsing, live response fetched once, no frame processing")
                put("videoId", "JENpTmMQBQY"); put("samplesEach", 300); put("warmupPairs", 50)
                put("lines", corrected.lines!!.size)
                put("originalCoreParser", stats(before)); put("gratifyProductionMapping", stats(after))
                put("originalFirstLine", oldLine.words.replace(tags, "")); put("correctedFirstLine", phrase)
                put("allLineAndFragmentTimestampsPreserved", true)
                put("betterLyricsAvailable", better != null)
                put("betterMatchingPhraseFound", betterOld != null && betterNew != null)
                if (betterOld != null && betterNew != null) {
                    put("betterUpstreamFirstLine", betterOld.words.replace(tags, ""))
                    put("betterGratifyFirstLine", betterNew.words.replace(tags, ""))
                }
                put("apkBuilt", false); put("androidPerformanceMeasured", false)
            }
            val path = root().resolve("artifacts/lyrics-performance-2026-10-03/processing.json")
            Files.createDirectories(path.parent)
            Files.writeString(path, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        } finally { client.close() }
    }
}
