package com.tan.data.sync

import DatabaseDao

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class SavedPlaylistRestoreTest {
    @Test fun nameAndCoverCommitTogetherAndWrongOwnerCannotSave(): Unit = runBlocking {
        fixture { dao ->
            val id = dao.createLocalPlaylistWithSongs("owner", LocalPlaylistEntity(title="Original",thumbnail="old.jpg",syncId="owned-details-stable-id"),emptyList())
            dao.setOwnedPlaylistDetails("owner",id,"Renamed","crop.jpg","details-revision")
            assertEquals("Renamed",dao.getLocalPlaylist(id)!!.title)
            assertEquals("crop.jpg",dao.getLocalPlaylist(id)!!.thumbnail)
            assertEquals("details-revision",dao.getLibraryRemovals("owner","playlist_metadata").single().revision)
            assertFailsWith<IllegalStateException> { dao.setOwnedPlaylistDetails("another",id,"Forgery","bad.jpg","bad") }
            assertEquals("Renamed",dao.getLocalPlaylist(id)!!.title)
            assertEquals("crop.jpg",dao.getLocalPlaylist(id)!!.thumbnail)
        }
    }
    @Test fun rejectedCoverRollsBackNameAndDoesNotQueuePartialDetails(): Unit = runBlocking {
        val directory=Files.createTempDirectory("gratify-details-rollback")
        val db=Room.databaseBuilder<MusicDatabase>(name=directory.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            AccountLibraryStore(db).activate("owner",null)
            val dao=db.getDatabaseDao()
            val id=dao.createLocalPlaylistWithSongs("owner",LocalPlaylistEntity(title="Original",thumbnail="old.jpg",syncId="owned-details-stable-id"),emptyList())
            db.useWriterConnection { it.execSQL("CREATE TRIGGER reject_cover BEFORE UPDATE OF thumbnail ON local_playlist BEGIN SELECT RAISE(ABORT,'test failure'); END") }
            assertFails { dao.setOwnedPlaylistDetails("owner",id,"Renamed","new.jpg","revision") }
            assertEquals("Original",dao.getLocalPlaylist(id)!!.title)
            assertEquals("old.jpg",dao.getLocalPlaylist(id)!!.thumbnail)
            assertTrue(dao.getLibraryRemovals("owner","playlist_metadata").isEmpty())
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }
    private fun song(id:String)=SongEntity(videoId=id,title=id,duration="1:00",durationSeconds=60,
        isAvailable=true,isExplicit=false,likeStatus="LIKE",videoType="MUSIC_VIDEO_TYPE_ATV",category=null,resultType=null,liked=true)
    private suspend fun fixture(block:suspend (DatabaseDao)->Unit) {
        val directory=Files.createTempDirectory("gratify-saved-playlist")
        val db=Room.databaseBuilder<MusicDatabase>(name=directory.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try { AccountLibraryStore(db).activate("owner",null); block(db.getDatabaseDao()) }
        finally { db.close(); directory.toFile().deleteRecursively() }
    }
    @Test fun repeatedRestoreUpdatesSourceWithoutDuplicatingOrLosingSongLibraryState():Unit=runBlocking {
        fixture { dao ->
            val initial=LocalPlaylistEntity(title="First",thumbnail="first.jpg",sourceSharedPlaylistId="source",syncId="local-identity")
            dao.restoreSavedPlaylist("owner",initial,listOf(song("a")))
            val id=dao.getLocalPlaylistBySourceSharedId("source")!!.id
            dao.updateLocalPlaylistDownloadState(downloadState=DownloadState.STATE_DOWNLOADED,id=id)
            dao.restoreSavedPlaylist("owner",initial.copy(title="Updated",thumbnail="new.jpg",creatorName="Creator"),listOf(song("b"),song("a")))
            val updated=dao.getLocalPlaylistBySourceSharedId("source")!!
            assertEquals(id,updated.id); assertEquals("Updated",updated.title); assertEquals("new.jpg",updated.thumbnail)
            assertEquals(listOf("b","a"),dao.getAllPlaylistPairSongByPosition(id).map { it.songId })
            assertTrue(dao.getSong("a")!!.liked)
            assertEquals(DownloadState.STATE_NOT_DOWNLOADED,updated.downloadState)
        }
    }
    @Test fun pendingOfflineRemovalPreventsRestoreResurrection():Unit=runBlocking {
        fixture { dao ->
            val item=LocalPlaylistEntity(title="Saved",sourceSharedPlaylistId="source",syncId="local-identity")
            dao.restoreSavedPlaylist("owner",item,listOf(song("a")))
            dao.deleteOwnedLocalPlaylist(dao.getLocalPlaylistBySourceSharedId("source")!!.id,"removal")
            dao.restoreSavedPlaylist("owner",item,listOf(song("a")))
            assertNull(dao.getLocalPlaylistBySourceSharedId("source"))
        }
    }
    @Test fun explicitResaveCancelsOldRemovalIntent():Unit=runBlocking {
        fixture { dao ->
            val item=LocalPlaylistEntity(title="Saved",sourceSharedPlaylistId="source",syncId="local-identity")
            val id=dao.createLocalPlaylistWithSongs("owner",item,listOf(song("a")))
            dao.deleteOwnedLocalPlaylist(id,"removal")
            val replacement=dao.createLocalPlaylistWithSongs("owner",item,listOf(song("a")))
            assertTrue(replacement>0); assertTrue(dao.getLibraryRemovals("owner","shared_playlist_saves").isEmpty())
        }
    }
    @Test fun accountMismatchCannotImportAnotherAccountsSaves():Unit=runBlocking {
        fixture { dao ->
            assertFailsWith<IllegalStateException> { dao.restoreSavedPlaylist("different",LocalPlaylistEntity(title="Private",sourceSharedPlaylistId="source"),listOf(song("private"))) }
            assertNull(dao.getSong("private")); assertNull(dao.getLocalPlaylistBySourceSharedId("source"))
        }
    }
    @Test fun metadataChangesRemainQueuedUntilExactVersionIsAcknowledged():Unit=runBlocking {
        fixture { dao ->
            val id=dao.createLocalPlaylistWithSongs("owner",LocalPlaylistEntity(title="Original",syncId="owned-stable-identity"),listOf(song("a")))
            dao.setOwnedPlaylistTitle("owner",id,"Changed","title-revision")
            assertEquals("title-revision",dao.getLibraryRemovals("owner","playlist_metadata").single().revision)
            dao.setOwnedPlaylistThumbnail("owner",id,"updated.jpg","cover-revision")
            dao.acknowledgeLibraryRemoval("owner","playlist_metadata","owned-stable-identity","title-revision")
            assertEquals("cover-revision",dao.getLibraryRemovals("owner","playlist_metadata").single().revision)
            dao.acknowledgeLibraryRemoval("owner","playlist_metadata","owned-stable-identity","cover-revision")
            assertTrue(dao.getLibraryRemovals("owner","playlist_metadata").isEmpty())
        }
    }
    @Test fun savedSourceMetadataCannotBeEditedOrQueuedAsOwned():Unit=runBlocking {
        fixture { dao ->
            dao.restoreSavedPlaylist("owner",LocalPlaylistEntity(title="Source",sourceSharedPlaylistId="source"),listOf(song("a")))
            val id=dao.getLocalPlaylistBySourceSharedId("source")!!.id
            assertFailsWith<IllegalStateException> { dao.setOwnedPlaylistTitle("owner",id,"Forgery","revision") }
            assertTrue(dao.getLibraryRemovals("owner","playlist_metadata").isEmpty())
            assertEquals("Source",dao.getLocalPlaylist(id)!!.title)
        }
    }
}
