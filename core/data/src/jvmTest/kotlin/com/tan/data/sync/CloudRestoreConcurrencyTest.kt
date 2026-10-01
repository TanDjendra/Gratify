package com.tan.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.QueueEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.*

class CloudRestoreConcurrencyTest {
    private suspend fun withDatabase(test: suspend (MusicDatabase) -> Unit) {
        val directory = Files.createTempDirectory("gratify-cloud-concurrency-")
        val db = Room.databaseBuilder<MusicDatabase>(name = directory.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO).build()
        try { test(db) }
        finally { db.close(); directory.toFile().deleteRecursively() }
    }

    private fun song(id: String) = SongEntity(videoId = id, title = id, duration = "1:00", durationSeconds = 60,
        isAvailable = true, isExplicit = false, likeStatus = "LIKE", videoType = "MUSIC_VIDEO_TYPE_ATV",
        category = null, resultType = null, liked = true)

    private fun queue(id: String) = QueueEntity(listTrack = listOf(Track(album = null, artists = null,
        duration = null, durationSeconds = 60, isAvailable = true, isExplicit = false, likeStatus = null,
        thumbnails = null, title = id, videoId = id, videoType = null, category = null,
        feedbackTokens = null, resultType = null)))

    @Test fun localEditsMadeDuringCloudReadTakePriorityOverItsOldSnapshot(): Unit = runBlocking {
        withDatabase { db ->
            AccountLibraryStore(db).activate("A", null)
            val dao = db.getDatabaseDao(); val cloud = song("song")
            dao.insertSong(cloud)
            dao.setLibraryFlag("user_liked_songs", "song", 0, null, "new-unlike")
            assertFalse(dao.restoreRemoteLikedSong("A", cloud))
            assertFalse(dao.getSong("song")!!.liked)
            dao.setLibraryFlag("user_liked_songs", "song", 1, LocalDateTime.parse("2026-01-01T00:00:00"), "new-like")
            assertFalse(dao.applyRemoteLibraryRemoval("A", "user_liked_songs", "song"))
            assertTrue(dao.getSong("song")!!.liked)
            dao.acknowledgeLibraryRemoval("A", "user_liked_songs", "song", "new-like")
            assertTrue(dao.applyRemoteLibraryRemoval("A", "user_liked_songs", "song"))
            assertFalse(dao.getSong("song")!!.liked)
        }
    }

    @Test fun aQueueChangedDuringTheRequestIsPreservedAndUnchangedQueueCanRestore(): Unit = runBlocking {
        withDatabase { db ->
            AccountLibraryStore(db).activate("A", null)
            val dao = db.getDatabaseDao(); val before = queue("before"); val current = queue("new-playback"); val cloud = queue("cloud")
            dao.recoverQueue(before)
            dao.recoverQueue(current)
            assertFalse(dao.restoreQueueIfUnchanged("A", before, cloud))
            assertEquals(current, dao.getQueue().single())
            assertTrue(dao.restoreQueueIfUnchanged("A", current, cloud))
            assertEquals(cloud, dao.getQueue().single())
        }
    }

    @Test fun lateRecoveryCannotWriteIntoAnotherAccountsLibrary(): Unit = runBlocking {
        withDatabase { db ->
            val store = AccountLibraryStore(db); val dao = db.getDatabaseDao()
            store.activate("A", null); dao.insertSong(song("A-song"))
            store.activate("B", "A"); dao.insertSong(song("B-song"))
            assertFails { dao.restoreRemoteLikedSong("A", song("A-song")) }
            assertFails { dao.applyRemoteLibraryRemoval("A", "user_liked_songs", "B-song") }
            assertFails { dao.restoreQueueIfUnchanged("A", null, queue("A-queue")) }
            assertNull(dao.getSong("A-song"))
            assertTrue(dao.getSong("B-song")!!.liked)
            assertTrue(dao.getQueue().isEmpty())
        }
    }
}
