package com.tan.data.sync

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import androidx.sqlite.execSQL
import kotlinx.serialization.json.*
import kotlin.test.*

class AccountLibraryStoreTest {
    @Test fun exportContainsOnlyActiveAccountAndLeavesPrivateArchivesIntact(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-export-test")
        val db = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(db); val dao = db.getDatabaseDao()
            store.activate("A", null); dao.insertSong(song("private-A"))
            store.activate("B", "A"); dao.insertSong(song("active-B"))
            val exported = directory.resolve("export.db").toString()
            store.exportActiveAccount(exported)
            BundledSQLiteDriver().open(exported).use { copy ->
                copy.prepare("SELECT COUNT(*) FROM account_library_snapshot").use { it.step(); assertEquals(0L, it.getLong(0)) }
                copy.prepare("SELECT videoId FROM song").use { it.step(); assertEquals("active-B", it.getText(0)); assertFalse(it.step()) }
            }
            store.activate("A", "B"); assertNotNull(dao.getSong("private-A"))
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }

    private fun database(path: String) = Room.databaseBuilder<MusicDatabase>(name = path)
        .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO).build()

    private fun song(id: String) = SongEntity(videoId = id, title = id, duration = "1:00", durationSeconds = 60,
        isAvailable = true, isExplicit = false, likeStatus = "LIKE", videoType = "MUSIC_VIDEO_TYPE_ATV",
        category = null, resultType = null, liked = true)

    @Test fun switchingAccountsWithoutNetworkArchivesAndRestoresCompleteLibraries(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-account-test")
        val db = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(db)
            val dao = db.getDatabaseDao()
            store.activate("A", null)
            dao.insertSong(song("A-song"))
            dao.insertLocalPlaylist(LocalPlaylistEntity(title = "A private playlist"))
            store.activate("B", "A")
            assertNull(dao.getSong("A-song"))
            assertTrue(dao.getAllLocalPlaylists("", 100, 0).isNullOrEmpty())
            dao.insertSong(song("B-song"))
            store.activate("A", "B")
            assertNotNull(dao.getSong("A-song"))
            assertNull(dao.getSong("B-song"))
            assertEquals("A private playlist", dao.getAllLocalPlaylists("", 100, 0)?.single()?.title)
            store.activate("", "A") // offline logout must expose no private rows
            assertNull(dao.getSong("A-song"))
            store.activate("A", "")
            assertNotNull(dao.getSong("A-song"))
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun corruptIncomingArchiveRollsBackClearAndOwnerChange(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-rollback-test")
        val db = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(db); val dao = db.getDatabaseDao()
            store.activate("A", null); dao.insertSong(song("keep-me"))
            db.useWriterConnection { connection ->
                connection.execSQL("INSERT INTO account_library_snapshot(ownerId,contents) VALUES ('B','invalid-json')")
            }
            assertFails { store.activate("B", "A") }
            assertEquals("A", dao.getLibraryOwner())
            assertNotNull(dao.getSong("keep-me"))
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun deletionIntentSurvivesAccountSwitchAndOlderAcknowledgements(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-outbox-test")
        val db = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(db); val dao = db.getDatabaseDao()
            store.activate("A", null); dao.insertSong(song("remove-me"))
            dao.setLibraryFlag("user_liked_songs", "remove-me", 0, null, "one")
            dao.setLibraryFlag("user_liked_songs", "remove-me", 0, null, "two")
            dao.acknowledgeLibraryRemoval("A", "user_liked_songs", "remove-me", "one")
            assertEquals("two", dao.getLibraryRemovals("A", "user_liked_songs").single().revision)
            store.activate("B", "A")
            assertTrue(dao.getLibraryRemovals("B", "user_liked_songs").isEmpty())
            store.activate("A", "B")
            assertFalse(dao.getSong("remove-me")!!.liked)
            assertEquals(1, dao.getLibraryRemovals("A", "user_liked_songs").size)
            dao.setLibraryFlag("user_liked_songs", "remove-me", 1, null, "three")
            assertTrue(dao.getLibraryRemovals("A", "user_liked_songs").isEmpty())
            dao.acknowledgeLibraryRemoval("A", "user_liked_songs", "remove-me", "two")
            assertEquals(1, dao.getLibraryChanges("A", "user_liked_songs").single().enabled)
            assertEquals("three", dao.getLibraryChanges("A", "user_liked_songs").single().revision)
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }
    @Test fun migration25To26PreservesExistingPlaylist(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-migration-test")
        val file = directory.resolve("test.db")
        val schema = Json.parseToJsonElement(java.io.File("schemas/com.tan.data.db.MusicDatabase/25.json").readText()).jsonObject["database"]!!.jsonObject
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            schema["entities"]!!.jsonArray.forEach { entry ->
                val entity = entry.jsonObject
                connection.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("${'$'}{TABLE_NAME}", entity["tableName"]!!.jsonPrimitive.content))
                entity["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("${'$'}{TABLE_NAME}", entity["tableName"]!!.jsonPrimitive.content))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = 25")
            connection.execSQL("INSERT INTO local_playlist(title,inLibrary,downloadState) VALUES ('Preserved',0,0)")
        }
        val db = database(file.toString())
        try {
            assertEquals("Preserved", db.getDatabaseDao().getAllLocalPlaylists("",100,0).single().title)
            AccountLibraryStore(db).activate("A", null)
            assertEquals("A",db.getDatabaseDao().getLibraryOwner())
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun newLocalLikesAreQueuedAndRemoteRestoresAreNotRepublished(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-addition-test")
        val db = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(db); val dao = db.getDatabaseDao(); store.activate("A", null)
            dao.insertOwnedSong(song("new-like"), "add-one")
            dao.insertSong(song("remote-like"))
            assertEquals("new-like", dao.getLibraryChanges("A", "user_liked_songs").single().itemId)
            dao.insertOwnedSong(song("new-like"), "duplicate")
            assertEquals("add-one", dao.getLibraryChanges("A", "user_liked_songs").single().revision)
            val playlist = dao.insertLocalPlaylist(LocalPlaylistEntity(title = "Saved", sourceSharedPlaylistId = "source", syncId = "stable-saved-playlist"))
            dao.deleteOwnedLocalPlaylist(playlist, "delete-one")
            assertNull(dao.getLocalPlaylist(playlist))
            assertEquals("stable-saved-playlist", dao.getLibraryRemovals("A", "cloud_playlists").single().itemId)
            assertEquals("source", dao.getLibraryRemovals("A", "shared_playlist_saves").single().itemId)
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun deletingOneAccountPreservesTheOtherAccountArchive(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-delete-test"); val db = database(directory.resolve("test.db").toString())
        try {
            val store = AccountLibraryStore(db); val dao = db.getDatabaseDao()
            store.activate("A", null); dao.insertSong(song("A")); store.activate("B", "A"); dao.insertSong(song("B"))
            store.removeAccount("B"); assertNull(dao.getSong("B")); assertEquals("", dao.getLibraryOwner())
            store.activate("A", ""); assertNotNull(dao.getSong("A")); assertNull(dao.getSong("B"))
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }
}
