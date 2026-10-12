package com.tan.data

import com.tan.data.mapping.toLyrics
import com.tan.data.benchmark.upstream.parseRichSyncLyrics as upstreamParse
import com.tan.domain.utils.richSyncTextSignature
import com.tan.gratify.lyrics.SimpMusicLyricsClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ManguVideoVariantsTest {
    @Test fun sameTitleReceivesDifferentWordBoundariesDependingOnVideoId(): Unit = runBlocking {
        assumeTrue(System.getenv("GRATIFY_LYRICS_NETWORK_CHECK") == "1")
        val client = SimpMusicLyricsClient()
        val phrase = "Suatu malam Adam bercerita"
        val tag = Regex("<[^>]*>")
        val cases = listOf("JENpTmMQBQY" to 11, "fUdfDKyrbjk" to 4,
            "McIPitKTfw4" to 11, "WNGbZRb3czc" to 11)
        val rows = mutableListOf<JsonObject>()
        try {
            for ((videoId, fragments) in cases) {
                val response = client.getLyrics(videoId).getOrThrow().first { !it.richSyncLyrics.isNullOrBlank() }
                val original = upstreamParse(response.richSyncLyrics!!).toLyrics()
                val corrected = response.toLyrics()!!
                val old = original.lines!!.first { richSyncTextSignature(it.words) == richSyncTextSignature(phrase) }
                val fresh = corrected.lines!!.first { richSyncTextSignature(it.words) == richSyncTextSignature(phrase) }
                assertEquals(fragments, tag.findAll(old.words).count())
                assertEquals(phrase.lowercase(), fresh.words.replace(tag, "").lowercase())
                assertEquals(old.startTimeMs, fresh.startTimeMs)
                assertEquals(tag.findAll(old.words).map { it.value }.toList(), tag.findAll(fresh.words).map { it.value }.toList())
                assertEquals((if (fragments == 4) phrase else "Su a tu ma lam A dam ber ce ri ta").lowercase(), old.words.replace(tag, "").lowercase())
                rows += buildJsonObject {
                    put("videoId", videoId); put("timedFragments", fragments)
                    put("lineStartMs", old.startTimeMs.toLong())
                    put("upstreamText", old.words.replace(tag, "")); put("gratifyText", fresh.words.replace(tag, ""))
                    put("timestampsUnchanged", true)
                }
            }
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve("settings.gradle.kts")) }
            val path = root.resolve("artifacts/simpmusic-source-investigation-2026-10-03/live-video-variants.json")
            Files.createDirectories(path.parent)
            Files.writeString(path, Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(rows)))
        } finally { client.close() }
    }
}
