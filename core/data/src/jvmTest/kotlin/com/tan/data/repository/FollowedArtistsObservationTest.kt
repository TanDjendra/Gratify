package com.tan.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.*
import com.tan.domain.data.entities.ArtistEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import kotlin.test.*

class FollowedArtistsObservationTest {
    @Test fun removingTheLastFollowedArtistEmitsEmptyWithoutReopeningTheProfile(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-artist-observation-")
        val db = Room.databaseBuilder<MusicDatabase>(name = directory.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val dao = db.getDatabaseDao()
            dao.insertArtist(ArtistEntity("artist", "Artist", null, followed = true))
            val state = dao.observeFollowedArtists().stateIn(this, SharingStarted.Eagerly, emptyList())
            withTimeout(3000) { state.first { it.size == 1 } }
            dao.updateFollowed(0, "artist", null)
            withTimeout(3000) { state.first { it.isEmpty() } }
            assertTrue(state.value.isEmpty())
            coroutineContext.cancelChildren()
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }
}
