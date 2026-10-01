package com.tan.data.sync

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.data.db.datasource.LocalDataSource
import com.tan.data.dataStore.DataStoreManagerImpl
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class PlaylistIdentityTest {
    private fun database(path: String) = Room.databaseBuilder<MusicDatabase>(name = path)
        .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO).build()

    private fun song(id: String) = SongEntity(videoId = id, title = id, duration = "1:00", durationSeconds = 60,
        isAvailable = true, isExplicit = false, likeStatus = "INDIFFERENT", videoType = "MUSIC_VIDEO_TYPE_ATV",
        category = null, resultType = null)

    @Test fun restoreAcrossDevicesPreservesSameTitlesAndCollidingNumericIds(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-playlist-devices")
        val a = database(directory.resolve("a.db").toString())
        val b = database(directory.resolve("b.db").toString())
        try {
            AccountLibraryStore(a).activate("owner", null); AccountLibraryStore(b).activate("owner", null)
            val da = a.getDatabaseDao(); val db = b.getDatabaseDao()
            val source = LocalDataSource(da, DataStoreManagerImpl(MemoryPreferences()))
            val first = source.insertLocalPlaylist(LocalPlaylistEntity(title = "Same title"))
            val second = source.insertLocalPlaylist(LocalPlaylistEntity(title = "Same title"))
            val firstEntity = da.getLocalPlaylist(first)!!; val secondEntity = da.getLocalPlaylist(second)!!
            val firstSyncId = requireNotNull(firstEntity.syncId)
            assertNotEquals(firstEntity.syncId, secondEntity.syncId)
            val unrelated = db.insertLocalPlaylist(LocalPlaylistEntity(title = "Same title", syncId = "unrelated-device-playlist"))
            assertEquals(first, unrelated)
            assertTrue(db.restoreCloudPlaylist("owner", firstEntity.copy(id = 0, tracks = listOf("a")), listOf(song("a"))))
            assertTrue(db.restoreCloudPlaylist("owner", secondEntity.copy(id = 0, tracks = listOf("b")), listOf(song("b"))))
            assertFalse(db.restoreCloudPlaylist("owner", firstEntity.copy(id = 0), listOf(song("a"))))
            assertEquals(3, db.getAllLocalPlaylists("", 100, 0).size)
            val restored = db.getLocalPlaylistBySyncId(firstEntity.syncId!!)!!
            assertNotEquals(first, restored.id)
            assertEquals(listOf("a"), db.getAllPlaylistPairSongByPosition(restored.id).map { it.songId })
            db.applyPlaylistTombstones("owner", listOf(firstSyncId))
            assertNull(db.getLocalPlaylistBySyncId(firstSyncId))
            assertNotNull(db.getLocalPlaylist(unrelated)); assertNotNull(db.getLocalPlaylistBySyncId(secondEntity.syncId!!))
        } finally { a.close(); b.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun legacyIdentityAssignmentIsAtomicAndDeletionOutboxUsesStableIdentity(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-playlist-identity")
        val database = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(database); store.activate("A", null)
            val dao = database.getDatabaseDao()
            val id = dao.insertLocalPlaylist(LocalPlaylistEntity(title = "Legacy"))
            val identities = listOf("identity-candidate-one", "identity-candidate-two").map { candidate ->
                async(Dispatchers.IO) { dao.ensurePlaylistSyncId(id, "A", candidate) }
            }.awaitAll()
            assertEquals(1, identities.distinct().size)
            dao.updateLocalPlaylistTitle("Renamed", id)
            assertEquals(identities.first(), dao.ensurePlaylistSyncId(id, "A", "must-not-replace"))
            dao.deleteOwnedLocalPlaylist(id, "revision")
            assertEquals(identities.first(), dao.getLibraryRemovals("A", "cloud_playlists").single().itemId)
            store.activate("B", "A")
            assertTrue(dao.getLibraryRemovals("B", "cloud_playlists").isEmpty())
            assertFails { dao.ensurePlaylistSyncId(id, "A", "wrong-account") }
            store.activate("A", "B")
            assertEquals(identities.first(), dao.getLibraryRemovals("A", "cloud_playlists").single().itemId)
        } finally { database.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun failedRestoreLeavesNoPartialPlaylistAndPendingDeletionPreventsRestore(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-playlist-rollback")
        val database = database(directory.resolve("test.db").toString())
        try {
            AccountLibraryStore(database).activate("A", null)
            val dao = database.getDatabaseDao()
            val playlist = LocalPlaylistEntity(title = "Bad payload", syncId = "identity-retry-safe")
            val syncId = requireNotNull(playlist.syncId)
            assertFails { dao.restoreCloudPlaylist("A", playlist, listOf(song("duplicate"), song("duplicate"))) }
            assertNull(dao.getLocalPlaylistBySyncId(syncId))
            assertTrue(dao.restoreCloudPlaylist("A", playlist, listOf(song("valid"))))
            val restored = dao.getLocalPlaylistBySyncId(syncId)!!
            dao.deleteOwnedLocalPlaylist(restored.id, "delete")
            assertFalse(dao.restoreCloudPlaylist("A", playlist, listOf(song("valid"))))
            assertNull(dao.getLocalPlaylistBySyncId(syncId))
        } finally { database.close(); directory.toFile().deleteRecursively() }
    }
    @Test fun emptyAndSingleSongPlaylistsAreValidAndAccountSwitchIsRejected(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-create-playlist")
        val database = database(directory.resolve("test.db").toString())
        try {
            AccountLibraryStore(database).activate("A", null)
            val dao = database.getDatabaseDao()
            val emptyId = dao.createLocalPlaylistWithSongs("A", LocalPlaylistEntity(title = "Empty", syncId = "empty"), emptyList())
            assertTrue(dao.getLocalPlaylist(emptyId)!!.tracks.isNullOrEmpty())
            val oneId = dao.createLocalPlaylistWithSongs("A", LocalPlaylistEntity(title = "One", syncId = "one"), listOf(song("one-song")))
            assertEquals(listOf("one-song"), dao.getAllPlaylistPairSongByPosition(oneId).map { it.songId })
            assertFails { dao.createLocalPlaylistWithSongs("B", LocalPlaylistEntity(title = "Wrong account", syncId = "wrong-owner"), emptyList()) }
            assertNull(dao.getLocalPlaylistBySyncId("wrong-owner"))
        } finally { database.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun songWriteFailureRollsBackNewPlaylistAndAllItsTracks(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-create-rollback")
        val database = database(directory.resolve("test.db").toString())
        try {
            AccountLibraryStore(database).activate("A", null)
            val dao = database.getDatabaseDao()
            dao.insertSong(song("existing"))
            database.useWriterConnection { connection ->
                connection.execSQL("CREATE TRIGGER audit_fail_song BEFORE INSERT ON song WHEN NEW.videoId = 'fail' BEGIN SELECT RAISE(ABORT, 'audit failure'); END")
            }
            assertFails { dao.createLocalPlaylistWithSongs("A", LocalPlaylistEntity(title = "Retry", syncId = "rollback"), listOf(song("first"), song("fail"))) }
            assertNull(dao.getLocalPlaylistBySyncId("rollback"))
            assertNull(dao.getSong("first"))
            assertNotNull(dao.getSong("existing"))
            val retryId = dao.createLocalPlaylistWithSongs("A", LocalPlaylistEntity(title = "Retry", syncId = "rollback"), listOf(song("first")))
            assertEquals(listOf("first"), dao.getAllPlaylistPairSongByPosition(retryId).map { it.songId })
        } finally { database.close(); directory.toFile().deleteRecursively() }
    }

}
