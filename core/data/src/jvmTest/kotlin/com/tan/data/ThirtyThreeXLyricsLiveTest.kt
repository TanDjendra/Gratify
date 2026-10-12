package com.tan.data

import com.tan.data.mapping.toLyrics
import com.tan.domain.utils.*
import com.tan.gratify.lyrics.SimpMusicLyricsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Opt-in public GETs; no account credentials, contributor metadata, or full lyrics are saved. */
class ThirtyThreeXLyricsLiveTest {
    @Test fun productionClientsFindCleanTextForTheReportedRecordingAndKeepTheCacheSafe(): Unit = runBlocking {
        assumeTrue(System.getenv("GRATIFY_LYRICS_NETWORK_CHECK") == "1")
        val client = SimpMusicLyricsClient()
        try {
            val source = client.getLyrics("_eVLDvD7lE8").getOrThrow().first()
                .toLyrics(preferWordTiming = false)!!.assessTextQuality()
            val reference = client.searchLrclibLyrics("33x", "Perunggu", 434).getOrThrow()!!.toLyrics()
            assertTrue(reference.hasDisplayableLyrics())
            val repaired = source.reconcileWordBoundaries(reference)
            val selected = repaired.takeIf { it.hasDisplayableLyrics() } ?: reference
            assertTrue(selected.hasDisplayableLyrics())
            val target = selected.lines!!.first { lyricDisplayText(it.words).equals("Kau tahu mana urutan satu", ignoreCase = true) }
            assertTrue(target.startTimeMs.toLong() in 52000..53000)
            val cache = selected.toLyricsEntity("_eVLDvD7lE8").toLyrics().assessTextQuality()
            assertTrue(cache.hasDisplayableLyrics())
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve("settings.gradle.kts")) }
            val report = root.resolve("artifacts/lyrics-quality-2026-10-11/live-33x.txt")
            Files.createDirectories(report.parent)
            Files.writeString(report, """
                productionClient=true
                videoId=_eVLDvD7lE8
                sourceDisplayable=${source.hasDisplayableLyrics()}
                sourceLines=${source.lines?.size}
                referenceDisplayable=${reference.hasDisplayableLyrics()}
                referenceLines=${reference.lines?.size}
                matchedLines=${repaired.lines.orEmpty().count { it.hasConfirmedLyricText() }}
                selectedLines=${selected.lines?.size}
                selectedSyncType=${selected.syncType}
                targetStartMs=${target.startTimeMs}
                persistedCacheDisplayable=${cache.hasDisplayableLyrics()}
            """.trimIndent())
        } finally { client.close() }
    }
}
