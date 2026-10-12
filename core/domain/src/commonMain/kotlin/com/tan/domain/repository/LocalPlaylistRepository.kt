package com.tan.domain.repository

import androidx.paging.PagingData
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.PairSongLocalPlaylist
import com.tan.domain.data.entities.SetVideoIdEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.browse.playlist.PlaylistState
import com.tan.domain.utils.FilterState
import com.tan.domain.utils.LocalResource
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface LocalPlaylistRepository {
    /** Materialize/refresh a member's cache without allocating a second playlist identity. */
    suspend fun cacheCanonicalPlaylist(owner: String, canonicalId: String, syncId: String?, title: String,
        thumbnail: String?, songs: List<SongEntity>, sharedId:String?=null): Long = error("Canonical cache unavailable")
    suspend fun reconcileCanonicalPlaylists(owner: String, memberIds: List<String>) { }
    suspend fun ensureOwnedPlaylistSyncId(id: Long, owner: String): String = error("Playlist identity unavailable")
    /** Save the name and optional new cover together, for the initiating account only. */
    suspend fun updatePlaylistDetailsForOwner(id: Long, title: String, thumbnail: String?, owner: String?)

    /** Append atomically and queue updates without changing public visibility. */
    suspend fun addTracksToLocalPlaylistForOwner(id: Long, songs: List<SongEntity>, owner: String?): Int

    /** Remove atomically for the initiating owner and queue the same public playlist update. */
    suspend fun removeTrackFromLocalPlaylistForOwner(id: Long, song: SongEntity, owner: String?): Boolean

    fun getLocalPlaylist(id: Long): Flow<LocalResource<LocalPlaylistEntity?>>

    fun getAllLocalPlaylists(): Flow<List<LocalPlaylistEntity>>

    suspend fun updateLocalPlaylistTracks(
        tracks: List<String>,
        id: Long,
    )

    suspend fun updateLocalPlaylistDownloadState(
        downloadState: Int,
        id: Long,
    )

    suspend fun updateLocalPlaylistYouTubePlaylistSyncState(
        id: Long,
        syncState: Int,
    )

    suspend fun insertPairSongLocalPlaylist(pairSongLocalPlaylist: PairSongLocalPlaylist)

    fun getPlaylistPairSongByListPosition(
        playlistId: Long,
        listPosition: List<Int>,
    ): Flow<List<PairSongLocalPlaylist>?>

    fun getPlaylistPairSongByOffset(
        playlistId: Long,
        offset: Int,
        filterState: FilterState,
    ): Flow<List<PairSongLocalPlaylist>?>

    fun getPlaylistPairSongByTime(
        playlistId: Long,
        filterState: FilterState,
        localDateTime: LocalDateTime,
    ): Flow<List<PairSongLocalPlaylist>?>

    fun getPlaylistPairOfSong(
        playlistId: Long,
        videoId: String,
    ): Flow<PairSongLocalPlaylist?>

    fun changePositionOfSongInPlaylist(
        playlistId: Long,
        videoId: String,
        newPosition: Int,
    ): Flow<String>

    /**
     * Move a song within a synced playlist: updates both YouTube (via API) and local DB positions.
     * @param playlistId Local playlist ID
     * @param fromIndex The current index of the item being moved (0-based, in CustomOrder)
     * @param toIndex The target index to move the item to (0-based, in CustomOrder)
     * @return Flow<LocalResource<String>> success/error message
     */
    fun moveItemInSyncedPlaylist(
        playlistId: Long,
        fromIndex: Int,
        toIndex: Int,
    ): Flow<LocalResource<String>>

    fun downloadStateFlow(id: Long): Flow<Int>

    fun getAllDownloadingLocalPlaylists(): Flow<List<LocalPlaylistEntity>>

    fun listTrackFlow(id: Long): Flow<List<String>>

    fun getTracksPaging(
        id: Long,
        filter: FilterState,
    ): Flow<PagingData<Pair<SongEntity, PairSongLocalPlaylist>>>

    suspend fun getFullPlaylistTracks(id: Long): List<SongEntity>

    suspend fun getListTrackVideoId(id: Long): List<String>

    suspend fun insertLocalPlaylistAndGetId(localPlaylist: LocalPlaylistEntity): Long

    suspend fun createLocalPlaylistWithSongs(localPlaylist: LocalPlaylistEntity, songs: List<SongEntity>): Long

    /** Check the draft's captured library owner inside the creation transaction. */
    suspend fun createLocalPlaylistWithSongsForOwner(localPlaylist: LocalPlaylistEntity, songs: List<SongEntity>, ownerId: String?): Long

    fun insertLocalPlaylist(
        localPlaylist: LocalPlaylistEntity,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun deleteLocalPlaylist(
        id: Long,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun updateTitleLocalPlaylist(
        id: Long,
        newTitle: String,
        updatedMessage: String,
        updatedYtMessage: String,
        errorMessage: String,
    ): Flow<LocalResource<String>>

    fun updateThumbnailLocalPlaylist(
        id: Long,
        newThumbnail: String,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun updateDownloadState(
        id: Long,
        downloadState: Int,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun syncYouTubePlaylistToLocalPlaylist(
        playlist: PlaylistState,
        tracks: List<Track>,
        successMessage: String,
        errorMessage: String,
    ): Flow<LocalResource<String>>

    fun syncLocalPlaylistToYouTubePlaylist(
        playlistId: Long,
        successMessage: String,
        errorMessage: String,
    ): Flow<LocalResource<String>>

    fun unsyncLocalPlaylist(
        id: Long,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun updateSyncState(
        id: Long,
        syncState: Int,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun updateYouTubePlaylistId(
        id: Long,
        youtubePlaylistId: String,
        successMessage: String,
    ): Flow<LocalResource<String>>

    fun updateListTrackSynced(id: Long): Flow<Boolean>

    fun addTrackToLocalPlaylist(
        id: Long,
        song: SongEntity,
        successMessage: String,
        updatedYtMessage: String,
        errorMessage: String,
    ): Flow<LocalResource<String>>

    suspend fun addTracksToLocalPlaylist(
        id: Long,
        songs: List<SongEntity>,
    )

    fun removeTrackFromLocalPlaylist(
        id: Long,
        song: SongEntity,
        successMessage: String,
        updatedYtMessage: String,
        errorMessage: String,
    ): Flow<LocalResource<String>>

    fun getSuggestionsTrackForPlaylist(id: Long): Flow<LocalResource<Pair<String?, List<Track>>>>

    fun reloadSuggestionPlaylist(reloadParams: String): Flow<LocalResource<Pair<String?, List<Track>>>>

    fun getYouTubeSetVideoId(youtubePlaylistId: String): Flow<List<SetVideoIdEntity>>

    fun addYouTubePlaylistItem(
        youtubePlaylistId: String,
        videoId: String,
    ): Flow<LocalResource<String>>

    suspend fun saveSharedPlaylistToLibrary(
        sharedPlaylistId: String,
        title: String,
        thumbnail: String?,
        tracks: List<Track>,
        creatorName: String? = null,
    ): Long

    suspend fun updateSourceAvailability(sharedPlaylistId: String, availability: Int)

    suspend fun getLocalPlaylistBySourceSharedId(sharedPlaylistId: String): LocalPlaylistEntity?
}
