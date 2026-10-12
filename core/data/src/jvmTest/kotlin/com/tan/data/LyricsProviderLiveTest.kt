package com.tan.data

import com.tan.data.mapping.toLyrics
import com.tan.gratify.lyrics.GratifyLyricsClient
import com.tan.gratify.lyrics.SimpMusicLyricsClient
import com.tan.gratify.lyrics.parser.parseTtmlLyrics
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Opt-in verification of the actual app HTTP client, without credentials or posting content. */
class LyricsProviderLiveTest {
    @Test fun publicRequestsUseTheProductionClientAndKeepTiming(): Unit = runBlocking {
        assumeTrue("Set GRATIFY_LYRICS_NETWORK_CHECK=1 for the manual network check",
            System.getenv("GRATIFY_LYRICS_NETWORK_CHECK") == "1")
        val client = SimpMusicLyricsClient()
        val gratify = GratifyLyricsClient()
        val lrclib = client.searchLrclibLyrics("Bohemian Rhapsody", "Queen", 354).getOrThrow()?.toLyrics()
        assertNotNull(lrclib)
        assertTrue(lrclib.lines.orEmpty().isNotEmpty())
        val primary = client.getLyrics("fJ9rUzIMcZQ")
            .getOrThrow().mapNotNull { it.toLyrics() }.firstOrNull()
        assertNotNull(primary)
        assertTrue(primary.lines.orEmpty().isNotEmpty())
        assertNull(primary.gratifyMusicLyrics, "SimpMusic IDs must not be sent to Gratify voting endpoints")
        val legacy = gratify.getLyrics("fJ9rUzIMcZQ", "Bohemian Rhapsody", "Queen", duration = 354)
            .getOrNull()?.firstOrNull()?.toLyrics()
        val better = client.searchBetterLyrics("Bohemian Rhapsody", "Queen", 354)
        val wordTiming = better.getOrNull()?.let { parseTtmlLyrics(it).toLyrics() }
        if (wordTiming != null) assertTrue(wordTiming.lines.orEmpty().isNotEmpty())
        val report = """
            LRCLIB: ${lrclib.syncType}, ${lrclib.lines?.size} lines
            SimpMusic primary: ${primary.syncType}, ${primary.lines?.size} lines
            SimpMusic primary word-timed lines: ${primary.lines.orEmpty().count { it.words.contains(Regex("<\\d+:\\d+")) }}
            Gratify fallback: ${legacy?.syncType ?: "unavailable"}, ${legacy?.lines?.size ?: 0} lines
            Better Lyrics: ${wordTiming?.syncType ?: "unavailable"}, ${wordTiming?.lines?.size ?: 0} lines
            Better Lyrics request error: ${better.exceptionOrNull()?.javaClass?.simpleName ?: "none"}
        """.trimIndent()
        client.close()
        val path = Path.of("../../artifacts/lyrics-provider-audit/simpmusic-production-client-network.txt")
        Files.createDirectories(path.parent)
        Files.writeString(path, report)
    }
}
