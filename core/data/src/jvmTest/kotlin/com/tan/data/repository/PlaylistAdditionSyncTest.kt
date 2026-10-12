package com.tan.data.repository

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sun.net.httpserver.HttpServer
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.data.db.datasource.LocalDataSource
import com.tan.data.dataStore.DataStoreManagerImpl
import com.tan.data.sync.AccountLibraryStore
import com.tan.data.sync.MemoryPreferences
import com.tan.domain.data.entities.*
import com.tan.domain.utils.LocalResource
import com.tan.kotlinytmusicscraper.YouTube
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class PlaylistAdditionSyncTest {
    private val owner="11111111-1111-4111-8111-111111111111"
    private fun song(id: String)=SongEntity(videoId=id,title=id,duration="1:00",durationSeconds=60,
        isAvailable=true,isExplicit=false,likeStatus="INDIFFERENT",videoType="MUSIC_VIDEO_TYPE_ATV",category=null,resultType=null)
    private class Remote {
        @Volatile var exists=true
        @Volatile var fail=false
        val uploads=CopyOnWriteArrayList<JsonObject>()
    }
    private suspend fun await(condition: suspend ()->Boolean)=withTimeout(10_000) { while(!condition()) delay(20) }
    private suspend fun fixture(block: suspend (MusicDatabase,LocalPlaylistRepositoryImpl,SocialRepositoryImpl,Remote,Long)->Unit) {
        val remote=Remote()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { exchange ->
            val path=exchange.requestURI.path
            var status=200
            val body=when {
                path.endsWith("/cloud_playlists") -> if(remote.exists) """[{"id":"cloud-1","title":"1","is_public":true}]""" else "[]"
                path.endsWith("/rpc/gratify_replace_cloud_playlist_v2") -> {
                    remote.uploads.add(Json.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject)
                    if(remote.fail) { status=503; """{"message":"offline","code":"503"}""" }
                    else """[{"playlist_id":"cloud-1"}]"""
                }
                else -> "[]"
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type","application/json")
            exchange.sendResponseHeaders(status,body.size.toLong()); exchange.responseBody.use { it.write(body) }; exchange.close()
        }
        server.start()
        val client=createSupabaseClient("http://127.0.0.1:${server.address.port}","synthetic-test-key") {
            install(Auth) { autoLoadFromStorage=false; autoSaveToStorage=false; alwaysAutoRefresh=false }
            install(Postgrest); install(Storage)
        }
        val folder=Files.createTempDirectory("gratify-add-playlist")
        val db=Room.databaseBuilder<MusicDatabase>(name=folder.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            client.auth.importSession(Json { ignoreUnknownKeys=true }.decodeFromString<UserSession>(
                """{"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated","created_at":"2026-10-09T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""),autoRefresh=false)
            AccountLibraryStore(db).activate(owner,null)
            val dao=db.getDatabaseDao()
            val id=dao.createLocalPlaylistWithSongs(owner,LocalPlaylistEntity(title="1",syncId="stable-playlist-identity",thumbnail="https://example.com/cover.jpg"),(1..6).map { song("old-$it") })
            val source=LocalDataSource(dao,DataStoreManagerImpl(MemoryPreferences()))
            val social=SocialRepositoryImpl(client,source)
            val repository=LocalPlaylistRepositoryImpl(source,YouTube(),social,SharedPlaylistRepositoryImpl(client),client,scope)
            block(db,repository,social,remote,id)
        } finally { scope.cancel(); scope.coroutineContext[Job]?.join(); db.close(); client.close(); server.stop(0); folder.toFile().deleteRecursively() }
    }
    @Test fun bothSongMenuAndPlaylistPickerAppendAndAutomaticallyUploadSamePublicIdentity(): Unit=runBlocking {
        fixture { db,repo,_,remote,id ->
            assertTrue(repo.addTrackToLocalPlaylist(id,song("new-1"),"added","yt","error").toList().last() is LocalResource.Success)
            await { db.getDatabaseDao().getLibraryRemovals(owner,"playlist_metadata").isEmpty() && remote.uploads.isNotEmpty() }
            assertEquals(2,repo.addTracksToLocalPlaylistForOwner(id,listOf(song("old-1"),song("new-2"),song("new-2"),song("new-3")),owner))
            await { remote.uploads.size==2 && db.getDatabaseDao().getLibraryRemovals(owner,"playlist_metadata").isEmpty() }
            val playlist=db.getDatabaseDao().getLocalPlaylist(id)!!
            assertEquals("1",playlist.title); assertEquals("https://example.com/cover.jpg",playlist.thumbnail)
            assertEquals("stable-playlist-identity",playlist.syncId); assertEquals(9,playlist.tracks!!.size)
            assertEquals((0..8).toList(),db.getDatabaseDao().getAllPlaylistPairSongByPosition(id).map { it.position })
            assertEquals(playlist.tracks,remote.uploads.last()["p_tracks"]!!.jsonArray.map { it.jsonObject["video_id"]!!.jsonPrimitive.content })
            assertTrue(remote.uploads.all { it["p_is_public"] == JsonNull && it["p_sync_id"]!!.jsonPrimitive.content==playlist.syncId })
        }
    }
    @Test fun failedCloudUploadKeepsLocalAdditionQueuedAndLaterRetryUploadsLatestList(): Unit=runBlocking {
        fixture { db,repo,social,remote,id ->
            remote.fail=true
            repo.addTracksToLocalPlaylistForOwner(id,listOf(song("new")),owner)
            await { remote.uploads.isNotEmpty() }
            assertEquals(7,db.getDatabaseDao().getLocalPlaylist(id)!!.tracks!!.size)
            assertTrue(db.getDatabaseDao().getLibraryRemovals(owner,"playlist_metadata").isNotEmpty())
            delay(100); remote.fail=false; social.flushPlaylistRemovals(owner)
            assertTrue(db.getDatabaseDao().getLibraryRemovals(owner,"playlist_metadata").isEmpty())
            assertEquals(7,remote.uploads.last()["p_tracks"]!!.jsonArray.size)
        }
    }
    @Test fun localDraftIsNotPublishedAndDuplicateOrWrongOwnerCannotChangePlaylist(): Unit=runBlocking {
        fixture { db,repo,_,remote,id ->
            remote.exists=false
            repo.addTracksToLocalPlaylistForOwner(id,listOf(song("new")),owner)
            await { db.getDatabaseDao().getLibraryRemovals(owner,"playlist_metadata").isEmpty() }
            assertTrue(remote.uploads.isEmpty())
            assertEquals(0,repo.addTracksToLocalPlaylistForOwner(id,listOf(song("new")),owner))
            assertFailsWith<IllegalStateException> { repo.addTracksToLocalPlaylistForOwner(id,listOf(song("forbidden")),"another") }
            assertEquals(7,db.getDatabaseDao().getLocalPlaylist(id)!!.tracks!!.size)
        }
    }
    @Test fun savedPlaylistRejectsAppendAndFailedTransactionRollsBackSongsPairsAndOutbox(): Unit=runBlocking {
        fixture { db,repo,_,_,id ->
            val dao=db.getDatabaseDao()
            val saved=dao.insertLocalPlaylist(LocalPlaylistEntity(title="Saved",sourceSharedPlaylistId="source",tracks=emptyList()))
            assertFailsWith<IllegalStateException> { repo.addTracksToLocalPlaylistForOwner(saved,listOf(song("no")),owner) }
            db.useWriterConnection { it.execSQL("CREATE TRIGGER reject_new BEFORE INSERT ON pair_song_local_playlist WHEN NEW.songId='bad' BEGIN SELECT RAISE(ABORT,'test failure'); END") }
            assertFails { dao.appendOwnedPlaylistSongs(owner,id,listOf(song("good"),song("bad")),"revision") }
            assertEquals(6,dao.getLocalPlaylist(id)!!.tracks!!.size); assertEquals(6,dao.getAllPlaylistPairSongByPosition(id).size)
            assertNull(dao.getSong("good")); assertNull(dao.getSong("bad")); assertTrue(dao.getLibraryRemovals(owner,"playlist_metadata").isEmpty())
        }
    }
    @Test fun removalUpdatesPairsAndPublicOutboxAtomicallyAndRetriesLatestContents(): Unit=runBlocking {
        fixture { db,repo,social,remote,id ->
            remote.fail=true
            val result=repo.removeTrackFromLocalPlaylist(id,song("old-3"),"removed","yt","error").toList()
            assertTrue(result.last() is LocalResource.Success)
            val dao=db.getDatabaseDao()
            assertEquals(listOf("old-1","old-2","old-4","old-5","old-6"),dao.getLocalPlaylist(id)!!.tracks)
            assertEquals((0..4).toList(),dao.getAllPlaylistPairSongByPosition(id).map { it.position })
            await { remote.uploads.isNotEmpty() }
            assertTrue(dao.getLibraryRemovals(owner,"playlist_metadata").isNotEmpty())
            remote.fail=false;social.flushPlaylistRemovals(owner)
            assertTrue(dao.getLibraryRemovals(owner,"playlist_metadata").isEmpty())
            assertEquals(dao.getLocalPlaylist(id)!!.tracks,remote.uploads.last()["p_tracks"]!!.jsonArray.map { it.jsonObject["video_id"]!!.jsonPrimitive.content })
            assertEquals(JsonNull,remote.uploads.last()["p_is_public"])
        }
    }
    @Test fun pickerUndoUsesInitiatingOwnerAndPublicSyncRetriesFinalMembership(): Unit=runBlocking {
        fixture { db,repo,social,remote,id ->
            remote.fail=true
            assertEquals(1,repo.addTracksToLocalPlaylistForOwner(id,listOf(song("new")),owner))
            assertTrue(repo.removeTrackFromLocalPlaylistForOwner(id,song("new"),owner))
            assertFalse(repo.removeTrackFromLocalPlaylistForOwner(id,song("new"),owner))
            assertEquals((1..6).map { "old-$it" },db.getDatabaseDao().getLocalPlaylist(id)!!.tracks)
            assertEquals((0..5).toList(),db.getDatabaseDao().getAllPlaylistPairSongByPosition(id).map { it.position })
            assertFailsWith<IllegalStateException> { repo.removeTrackFromLocalPlaylistForOwner(id,song("old-1"),"another") }
            await { remote.uploads.isNotEmpty() }
            delay(100);remote.fail=false;social.flushPlaylistRemovals(owner)
            assertTrue(db.getDatabaseDao().getLibraryRemovals(owner,"playlist_metadata").isEmpty())
            assertEquals((1..6).map { "old-$it" },remote.uploads.last()["p_tracks"]!!.jsonArray.map { it.jsonObject["video_id"]!!.jsonPrimitive.content })
            assertEquals(JsonNull,remote.uploads.last()["p_is_public"])
            assertEquals("stable-playlist-identity",remote.uploads.last()["p_sync_id"]!!.jsonPrimitive.content)
        }
    }
    @Test fun simultaneousAdditionAndRemovalCannotLoseSongsOrBreakPositions(): Unit=runBlocking {
        fixture { db,repo,_,_,id ->
            coroutineScope {
                val adding=async { repo.addTracksToLocalPlaylistForOwner(id,listOf(song("new")),owner) }
                val removing=async { repo.removeTrackFromLocalPlaylist(id,song("old-2"),"removed","yt","error").toList() }
                assertEquals(1,adding.await());assertTrue(removing.await().last() is LocalResource.Success)
            }
            val dao=db.getDatabaseDao();val playlist=dao.getLocalPlaylist(id)!!;val pairs=dao.getAllPlaylistPairSongByPosition(id)
            assertEquals(setOf("old-1","old-3","old-4","old-5","old-6","new"),playlist.tracks!!.toSet())
            assertEquals(playlist.tracks,pairs.map { it.songId });assertEquals((0..5).toList(),pairs.map { it.position })
        }
    }
    @Test fun rejectedRemovalRollsBackAndSavedOrWrongOwnerCannotRemoveSongs(): Unit=runBlocking {
        fixture { db,repo,_,_,id ->
            val dao=db.getDatabaseDao()
            db.useWriterConnection { it.execSQL("CREATE TRIGGER reject_remove BEFORE DELETE ON pair_song_local_playlist WHEN OLD.songId='old-3' BEGIN SELECT RAISE(ABORT,'test failure'); END") }
            assertTrue(repo.removeTrackFromLocalPlaylist(id,song("old-3"),"removed","yt","error").toList().last() is LocalResource.Error)
            assertEquals(6,dao.getLocalPlaylist(id)!!.tracks!!.size);assertEquals(6,dao.getAllPlaylistPairSongByPosition(id).size)
            assertTrue(dao.getLibraryRemovals(owner,"playlist_metadata").isEmpty())
            assertFailsWith<IllegalStateException> { dao.removeOwnedPlaylistSong("another",id,"old-1","revision") }
            val saved=dao.insertLocalPlaylist(LocalPlaylistEntity(title="Saved",sourceSharedPlaylistId="source",tracks=listOf("old-1")))
            assertFailsWith<IllegalStateException> { dao.removeOwnedPlaylistSong(owner,saved,"old-1","revision") }
            assertEquals(listOf("old-1"),dao.getLocalPlaylist(saved)!!.tracks)
        }
    }

}
