package com.tan.data.paging

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.paging.*
import com.tan.data.db.*
import com.tan.data.db.datasource.LocalDataSource
import com.tan.data.dataStore.DataStoreManagerImpl
import com.tan.data.sync.MemoryPreferences
import com.tan.domain.data.entities.*
import com.tan.domain.utils.FilterState
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.*

class PlaylistRegressionTest {
    private fun song(id: String) = SongEntity(videoId=id,title=id,duration="1:00",durationSeconds=60,
        isAvailable=true,isExplicit=false,likeStatus="INDIFFERENT",videoType="MUSIC_VIDEO_TYPE_ATV",category=null,resultType=null)

    @Test fun equalTimestampPagesAndRefreshDoNotSkipTracksAndMovesKeepUniquePositions(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-paging-test")
        val db = Room.databaseBuilder<MusicDatabase>(name=directory.resolve("test.db").toString()).addTypeConverter(Converters())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val dao = db.getDatabaseDao(); val local = LocalDataSource(dao, DataStoreManagerImpl(MemoryPreferences()))
            val playlist = dao.insertLocalPlaylist(LocalPlaylistEntity(title="Large")); val time = LocalDateTime.parse("2026-10-01T00:00:00")
            val ids = (0 until 120).map { "song-${it.toString().padStart(3,'0')}" }
            ids.forEachIndexed { i, id -> dao.insertSong(song(id)); dao.insertPairSongLocalPlaylist(PairSongLocalPlaylist(playlistId=playlist,songId=id,position=i,inPlaylist=time)) }
            for (filter in listOf(FilterState.OlderFirst, FilterState.NewerFirst)) {
                val source = LocalPlaylistTimeBasedPagingSource(playlist,filter,local)
                var key: PlaylistTimeCursor? = null; val collected = mutableListOf<String>()
                do {
                    val page = source.load(PagingSource.LoadParams.Refresh(key,50,false)) as PagingSource.LoadResult.Page
                    collected += page.data.map { it.first.videoId }; key = page.nextKey
                } while (key != null)
                assertEquals(if (filter==FilterState.OlderFirst) ids else ids.reversed(), collected)
            }
            val source = LocalPlaylistPagingSource(playlist,FilterState.CustomOrder,local)
            val first = source.load(PagingSource.LoadParams.Refresh(0,50,false)) as PagingSource.LoadResult.Page
            val second = source.load(PagingSource.LoadParams.Append(1,50,false)) as PagingSource.LoadResult.Page
            assertEquals(1, source.getRefreshKey(PagingState(listOf(first,second),75,PagingConfig(50),0)))
            dao.movePlaylistSong(playlist,ids[5],1)
            val ordered = dao.getAllPlaylistPairSongByPosition(playlist)
            assertEquals(ids[5],ordered[1].songId); assertEquals((0 until 120).toList(),ordered.map { it.position })
            assertEquals(ids.toSet(),ordered.map { it.songId }.toSet()); assertEquals(ordered.map { it.songId },dao.getLocalPlaylist(playlist)!!.tracks)
            assertFails { dao.movePlaylistSong(playlist,ids[0],999) }
            assertEquals(ordered,dao.getAllPlaylistPairSongByPosition(playlist))
            dao.insertSetVideoId(SetVideoIdEntity("same-song","set-A","playlist-A")); dao.insertSetVideoId(SetVideoIdEntity("same-song","set-B","playlist-B"))
            assertEquals("set-A",dao.getSetVideoId("same-song","playlist-A")!!.setVideoId)
            assertEquals("set-B",dao.getSetVideoId("same-song","playlist-B")!!.setVideoId)
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }
}
