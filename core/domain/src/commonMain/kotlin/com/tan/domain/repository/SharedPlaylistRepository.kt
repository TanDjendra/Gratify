package com.tan.domain.repository

import com.tan.domain.data.entities.SharedPlaylist
import com.tan.domain.data.entities.SharedPlaylistTrack
import kotlinx.coroutines.flow.Flow

interface SharedPlaylistRepository {
    suspend fun sharePlaylist(
        userId: String,
        localPlaylistId: Long,
        title: String,
        creatorName: String?,
        thumbnailUrl: String?,
        tracks: List<SharedPlaylistTrack>
    ): Result<String> // Returns the shared playlist ID

    suspend fun getSharedPlaylists(): Flow<Result<List<SharedPlaylist>>>
    
    suspend fun getSharedPlaylist(playlistId: String): Result<SharedPlaylist>
    
    suspend fun getSharedPlaylistTracks(playlistId: String): Flow<Result<List<SharedPlaylistTrack>>>
    
    suspend fun deleteSharedPlaylist(playlistId: String): Result<Unit>

    suspend fun deleteSharedPlaylistByLocalId(userId: String, localPlaylistId: Long, fallbackTitle: String): Result<Unit>

    /**
     * Records that [userId] added the shared playlist [playlistId] to their library.
     * Idempotent (unique per playlist+user); the server trigger increments add_count only
     * on the first save. Used for the Spotify-like "ditambahkan X kali" counter.
     */
    suspend fun recordPlaylistSave(playlistId: String, userId: String): Result<Unit>

    /**
     * Removes [userId]'s save of the shared playlist [playlistId] (when they remove the
     * added copy from their library). The server trigger decrements add_count.
     */
    suspend fun removePlaylistSave(playlistId: String, userId: String): Result<Unit>
}
