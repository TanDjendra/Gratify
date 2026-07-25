package com.tan.domain.repository

import com.tan.domain.data.model.sync.SyncReport
import kotlinx.coroutines.flow.Flow

interface UserDataSyncRepository {
    suspend fun syncUp(userId: String): Flow<Result<SyncReport>>
    suspend fun syncDown(userId: String): Flow<Result<SyncReport>>

    suspend fun syncUpLikedSongs(userId: String): Result<Int>
    suspend fun syncUpFollowedArtists(userId: String): Result<Int>
    suspend fun syncUpSavedAlbums(userId: String): Result<Int>
    suspend fun syncUpPlayHistory(userId: String): Result<Int>
    suspend fun syncUpQueue(userId: String): Result<Int>
    suspend fun syncUpSettings(userId: String): Result<Unit>

    suspend fun syncDownLikedSongs(userId: String): Result<Int>
    suspend fun syncDownFollowedArtists(userId: String): Result<Int>
    suspend fun syncDownSavedAlbums(userId: String): Result<Int>
    suspend fun syncDownPlayHistory(userId: String): Result<Int>
    suspend fun syncDownQueue(userId: String): Result<Int>
    suspend fun syncDownSettings(userId: String): Result<Unit>

    suspend fun clearCloudData(userId: String): Result<Unit>
    suspend fun clearLocalUserData(): Result<Unit>

    /**
     * Clears ONLY the local per-user database tables (liked songs, followed artists,
     * saved albums, queue, YouTube playlists) without touching the DataStore.
     * Used on account switch, where the login flow has already reset the DataStore
     * and populated it with the new account's info.
     */
    suspend fun clearLocalDatabase(): Result<Unit>
}
