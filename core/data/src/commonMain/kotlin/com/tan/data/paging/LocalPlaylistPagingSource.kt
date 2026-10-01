package com.tan.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tan.data.db.Converters
import com.tan.data.db.datasource.LocalDataSource
import com.tan.domain.data.entities.PairSongLocalPlaylist
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.utils.FilterState
import com.tan.logger.Logger

internal class LocalPlaylistPagingSource(
    private val playlistId: Long,
    private val filter: FilterState,
    private val localDataSource: LocalDataSource,
) : PagingSource<Int, Pair<SongEntity, PairSongLocalPlaylist>>() {
    override fun getRefreshKey(state: PagingState<Int, Pair<SongEntity, PairSongLocalPlaylist>>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page -> page.prevKey?.plus(1) ?: page.nextKey?.minus(1) }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pair<SongEntity, PairSongLocalPlaylist>> {
        return try {
            val currentPage = params.key ?: 0
            val pairs =
                localDataSource.getPlaylistPairSongByOffset(
                    playlistId = playlistId,
                    filterState = filter,
                    offset = currentPage,
                )
            Logger.d("LocalPlaylistPagingSource", "load: $pairs")
            val songs =
                localDataSource
                    .getSongByListVideoIdFull(
                        pairs?.map { it.songId } ?: emptyList(),
                    )
            val idValue = songs.associateBy { it.videoId }
            val sorted =
                (pairs ?: mutableListOf<PairSongLocalPlaylist>()).mapNotNull {
                    idValue[it.songId]?.let { songEntity ->
                        Pair(songEntity, it)
                    }
                }
            Logger.d("LocalPlaylistPagingSource", "load: $songs")
            return LoadResult.Page(
                data = sorted,
                prevKey = if (currentPage == 0) null else currentPage - 1,
                nextKey = if (songs.isEmpty()) null else currentPage + 1,
            )
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            Logger.e("LocalPlaylistPagingSource", "load: ${e.printStackTrace()}")
            LoadResult.Error(e)
        }
    }
}

internal data class PlaylistTimeCursor(val timestamp: kotlinx.datetime.LocalDateTime, val songId: String)

internal class LocalPlaylistTimeBasedPagingSource(
    private val playlistId: Long,
    private val filter: FilterState,
    private val localDataSource: LocalDataSource,
) : PagingSource<PlaylistTimeCursor, Pair<SongEntity, PairSongLocalPlaylist>>() {
    // Reload from the beginning; timestamp alone cannot identify a position among equal timestamps.
    override fun getRefreshKey(state: PagingState<PlaylistTimeCursor, Pair<SongEntity, PairSongLocalPlaylist>>): PlaylistTimeCursor? = null

    override suspend fun load(params: LoadParams<PlaylistTimeCursor>): LoadResult<PlaylistTimeCursor, Pair<SongEntity, PairSongLocalPlaylist>> {
        return try {
            val cursor = params.key
            val pairs = localDataSource.getPlaylistTimePage(playlistId, filter, cursor?.timestamp, cursor?.songId.orEmpty())
            val songs = localDataSource.getSongByListVideoIdFull(pairs.map { it.songId }).associateBy { it.videoId }
            LoadResult.Page(
                data = pairs.mapNotNull { pair -> songs[pair.songId]?.let { it to pair } },
                prevKey = null,
                nextKey = if (pairs.size < 50) null else pairs.last().let { PlaylistTimeCursor(it.inPlaylist, it.songId) },
            )
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { LoadResult.Error(e) }
    }
}
