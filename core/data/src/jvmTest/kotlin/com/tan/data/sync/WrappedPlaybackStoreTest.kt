package com.tan.data.sync

import DatabaseDao
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.analytics.PlaybackEventEntity
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.*

class WrappedPlaybackStoreTest {
    private suspend fun fixture(block: suspend (DatabaseDao, AccountLibraryStore) -> Unit) {
        val dir = Files.createTempDirectory("gratify-wrapped")
        val db = Room.databaseBuilder<MusicDatabase>(name = dir.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try { val store = AccountLibraryStore(db); store.activate("A", null); block(db.getDatabaseDao(), store) }
        finally { db.close(); dir.toFile().deleteRecursively() }
    }
    private fun event(date: String, id: String, seconds: Long = 60) = PlaybackEventEntity(
        videoId = id, timestamp = LocalDateTime.parse(date), listenedSecond = seconds)
    @Test fun yearRangeIncludesJanuaryFirstAndExcludesNextYear(): Unit = runBlocking {
        fixture { dao, _ ->
            listOf(event("2025-12-31T23:59:59", "old"), event("2026-01-01T00:00", "first"),
                event("2026-12-31T23:59:59", "last"), event("2027-01-01T00:00", "next")).forEach { dao.insertPlaybackEvent(it) }
            assertEquals(listOf("first", "last"), dao.getPlaybackEventsInRange(LocalDateTime(2026, 1, 1, 0, 0), LocalDateTime(2027, 1, 1, 0, 0)).map { it.videoId })
        }
    }
    @Test fun yearsUseEpochConverterAndIgnoreInvalidListening(): Unit = runBlocking {
        fixture { dao, _ ->
            listOf(event("2025-01-01T00:00", "a"), event("2026-01-01T00:00", "b"), event("2026-02-01T00:00", "c"),
                event("2024-01-01T00:00", "zero", 0), event("2023-01-01T00:00", " "), event("2022-01-01T00:00", "corrupt", 86401)).forEach { dao.insertPlaybackEvent(it) }
            assertEquals(listOf(2026, 2025), dao.getPlaybackYears())
        }
    }
    @Test fun tiesAreOrderedByEventIdAndLargeYearsHaveNoPagingGaps(): Unit = runBlocking {
        fixture { dao, _ ->
            repeat(1201) { dao.insertPlaybackEvent(event("2026-01-01T12:00", "song-$it")) }
            val rows = dao.getPlaybackEventsInRange(LocalDateTime(2026, 1, 1, 0, 0), LocalDateTime(2027, 1, 1, 0, 0))
            assertEquals(1201, rows.size); assertEquals(rows.map { it.eventId }.sorted(), rows.map { it.eventId })
            assertEquals(1201, rows.map { it.videoId }.distinct().size)
        }
    }
    @Test fun accountSwitchesKeepWrappedHistorySeparatedAndRestoreIt(): Unit = runBlocking {
        fixture { dao, store ->
            dao.insertPlaybackEvent(event("2025-01-01T00:00", "A-song")); store.activate("B", "A")
            assertTrue(dao.getPlaybackYears().isEmpty()); dao.insertPlaybackEvent(event("2026-01-01T00:00", "B-song"))
            store.activate("A", "B"); assertEquals(listOf(2025), dao.getPlaybackYears())
            assertEquals(listOf("A-song"), dao.getPlaybackEventsInRange(LocalDateTime(2025, 1, 1, 0, 0), LocalDateTime(2026, 1, 1, 0, 0)).map { it.videoId })
            store.activate("B", "A"); assertEquals(listOf(2026), dao.getPlaybackYears())
        }
    }
}
