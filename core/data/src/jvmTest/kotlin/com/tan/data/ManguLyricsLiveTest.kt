package com.tan.data

import com.tan.data.mapping.toLyrics
import com.tan.gratify.lyrics.SimpMusicLyricsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ManguLyricsLiveTest {
    @Test fun productionRequestRepairsTheReportedSongWithoutLosingTiming(): Unit = runBlocking {
        assumeTrue(System.getenv("GRATIFY_LYRICS_NETWORK_CHECK") == "1")
        val client = SimpMusicLyricsClient()
        try {
            val response = client.getLyrics("JENpTmMQBQY").getOrThrow()
            val result = response.mapNotNull { it.toLyrics() }.first { it.syncType == "RICH_SYNCED" }
            val line = result.lines!!.first { it.startTimeMs == "22960" }
            assertEquals("Suatu malam Adam bercerita", line.words.replace(Regex("<[^>]*>"), ""))
            assertEquals(11, Regex("<[^>]*>").findAll(line.words).count())
            assertEquals(com.tan.domain.utils.RICH_SYNC_TEXT_VERSION, line.richSyncTextVersion)
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve("settings.gradle.kts")) }
            val path = root.resolve("artifacts/lyrics-spacing/provider-check.json")
            Files.createDirectories(path.parent)
            // Report only the user-supplied line; never dump contributor metadata or full lyrics.
            Files.writeString(path, """{"videoId":"JENpTmMQBQY","productionClient":true,"syncType":"RICH_SYNCED","lineStartMs":22960,"timedFragments":11,"correctedText":"Suatu malam Adam bercerita"}""")
        } finally { client.close() }
    }
}
