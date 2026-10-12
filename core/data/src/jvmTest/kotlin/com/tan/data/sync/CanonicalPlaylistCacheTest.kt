package com.tan.data.sync

import DatabaseDao
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class CanonicalPlaylistCacheTest {
    @Test fun migration28To29PreservesExistingPersonalIdentity():Unit=runBlocking {
        val folder=Files.createTempDirectory("gratify-canonical-upgrade");val file=folder.resolve("test.db")
        val schema=Json.parseToJsonElement(java.io.File("schemas/com.tan.data.db.MusicDatabase/28.json").readText()).jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            schema.getValue("entities").jsonArray.forEach { entry ->
                val entity=entry.jsonObject;val table=entity.getValue("tableName").jsonPrimitive.content
                connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("${'$'}{TABLE_NAME}",table))
                entity["indices"]?.jsonArray?.forEach{connection.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("${'$'}{TABLE_NAME}",table))}
            }
            schema.getValue("setupQueries").jsonArray.forEach{connection.execSQL(it.jsonPrimitive.content)}
            connection.execSQL("PRAGMA user_version=28")
            connection.execSQL("INSERT INTO local_playlist(id,title,inLibrary,downloadState,sync_id) VALUES (7,'Preserved',0,3,'stable')")
        }
        val db=Room.databaseBuilder<MusicDatabase>(name=file.toString()).addTypeConverter(Converters())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try{val playlist=db.getDatabaseDao().getLocalPlaylist(7)!!
            assertEquals("Preserved",playlist.title);assertEquals("stable",playlist.syncId)
            assertEquals(3,playlist.downloadState);assertNull(playlist.canonicalPlaylistId)
        }finally{db.close();folder.toFile().deleteRecursively()}
    }
    private fun song(id:String)=SongEntity(videoId=id,title=id,duration="1:00",durationSeconds=60,
        isAvailable=true,isExplicit=false,likeStatus="LIKE",videoType="MUSIC_VIDEO_TYPE_ATV",category=null,resultType=null)
    private suspend fun fixture(block:suspend (DatabaseDao)->Unit) {
        val folder=Files.createTempDirectory("gratify-canonical-cache")
        val db=Room.databaseBuilder<MusicDatabase>(name=folder.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try{AccountLibraryStore(db).activate("owner",null);block(db.getDatabaseDao())}
        finally{db.close();folder.toFile().deleteRecursively()}
    }
    @Test fun handoffRetainsLocalIdDownloadsAndDoesNotDuplicate():Unit=runBlocking {
        fixture { dao ->
            val id=dao.createLocalPlaylistWithSongs("owner",LocalPlaylistEntity(title="Mine",syncId="same-stable-id",downloadState=3),listOf(song("one"),song("two")))
            val canonical=LocalPlaylistEntity(title="Mine",syncId="same-stable-id",canonicalPlaylistId="server-id",thumbnail="same-cover.jpg")
            assertEquals(id,dao.cacheCanonicalPlaylist("owner",canonical,listOf(song("one"),song("two"))))
            assertEquals(id,dao.cacheCanonicalPlaylist("owner",canonical,listOf(song("one"),song("two"))))
            assertEquals(1,dao.getAllLocalPlaylists("",100,0).size)
            assertEquals(3,dao.getLocalPlaylist(id)!!.downloadState)
            assertEquals("same-stable-id",dao.getLocalPlaylist(id)!!.syncId)
            assertTrue(dao.getLibraryRemovals("owner","playlist_metadata").isEmpty())
        }
    }
    @Test fun memberUpdatesReplaceCacheAndPreserveStoredOrder():Unit=runBlocking {
        fixture { dao ->
            val p=LocalPlaylistEntity(title="Same",syncId="sync",canonicalPlaylistId="canonical")
            val id=dao.cacheCanonicalPlaylist("owner",p,listOf(song("a"),song("b")))
            val addedAt=dao.getAllPlaylistPairSongByPosition(id).associate{it.songId to it.inPlaylist}
            dao.cacheCanonicalPlaylist("owner",p,listOf(song("b"),song("member"),song("a")))
            assertEquals(listOf("b","member","a"),dao.getAllPlaylistPairSongByPosition(id).map{it.songId})
            assertEquals(addedAt["a"],dao.getAllPlaylistPairSongByPosition(id).last().inPlaylist)
            assertTrue(dao.getLibraryRemovals("owner","playlist_metadata").isEmpty())
        }
    }
    @Test fun newMemberSongClearsCompletedDownloadFlagButReorderRetainsIt():Unit=runBlocking {
        fixture { dao ->
            val p=LocalPlaylistEntity(title="Same",syncId="sync",canonicalPlaylistId="canonical")
            val id=dao.cacheCanonicalPlaylist("owner",p,listOf(song("a"),song("b")))
            dao.updateLocalPlaylistDownloadState(3,id)
            dao.cacheCanonicalPlaylist("owner",p,listOf(song("b"),song("a")))
            assertEquals(3,dao.getLocalPlaylist(id)!!.downloadState)
            dao.cacheCanonicalPlaylist("owner",p,listOf(song("b"),song("a"),song("new")))
            assertEquals(0,dao.getLocalPlaylist(id)!!.downloadState)
        }
    }
    @Test fun cacheCannotBeEditedByLegacySnapshotOrWrongAccount():Unit=runBlocking {
        fixture { dao ->
            val p=LocalPlaylistEntity(title="Same",syncId="sync",canonicalPlaylistId="canonical")
            val id=dao.cacheCanonicalPlaylist("owner",p,listOf(song("a")))
            assertFails{dao.appendOwnedPlaylistSongs("owner",id,listOf(song("bad")),"revision")}
            assertFails{dao.setOwnedPlaylistDetails("owner",id,"Stale","bad.jpg","revision")}
            assertFails{dao.removeOwnedPlaylistSong("owner",id,"a","revision")}
            assertFails{dao.cacheCanonicalPlaylist("wrong-owner",p,listOf(song("bad")))}
            assertEquals("Same",dao.getLocalPlaylist(id)!!.title)
            assertEquals(listOf("a"),dao.getAllPlaylistPairSongByPosition(id).map{it.songId})
        }
    }
    @Test fun losingMembershipRemovesCacheWithoutDeletingPersonalPlaylistsOrQueuingServerDelete():Unit=runBlocking {
        fixture { dao ->
            val personal=dao.createLocalPlaylistWithSongs("owner",LocalPlaylistEntity(title="Local",syncId="personal"),emptyList())
            val cache=dao.cacheCanonicalPlaylist("owner",LocalPlaylistEntity(title="Shared",syncId="linked",canonicalPlaylistId="canonical"),listOf(song("a")))
            dao.reconcileCanonicalPlaylists("owner",listOf("canonical"));assertNotNull(dao.getLocalPlaylist(cache))
            dao.reconcileCanonicalPlaylists("owner",emptyList());assertNull(dao.getLocalPlaylist(cache))
            assertNotNull(dao.getLocalPlaylist(personal));assertTrue(dao.getLibraryRemovals("owner","cloud_playlists").isEmpty())
        }
    }
    @Test fun joiningPreviouslySavedPlaylistReusesCacheAndNeverRestoresStalePublicSnapshot():Unit=runBlocking {
        fixture { dao ->
            val id=dao.createLocalPlaylistWithSongs("owner",LocalPlaylistEntity(title="Saved",syncId="bookmark",sourceSharedPlaylistId="shared-id"),listOf(song("a")))
            val incoming=LocalPlaylistEntity(title="Same",syncId="source-owner-sync",canonicalPlaylistId="canonical",sourceSharedPlaylistId="shared-id")
            assertEquals(id,dao.cacheCanonicalPlaylist("owner",incoming,listOf(song("a"),song("member-added"))))
            dao.restoreSavedPlaylist("owner",LocalPlaylistEntity(title="Stale public",sourceSharedPlaylistId="shared-id"),listOf(song("a")))
            assertEquals(1,dao.getAllLocalPlaylists("",100,0).size)
            assertEquals("Same",dao.getLocalPlaylist(id)!!.title)
            assertEquals(listOf("a","member-added"),dao.getLocalPlaylist(id)!!.tracks)
        }
    }
}
