package com.tan.domain.repository

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.entities.UserFollow
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(userId: String): Flow<UserProfile?>
    fun searchUsers(query: String): Flow<List<UserProfile>>
    fun followUser(followerId: String, followingId: String): Flow<Result<Unit>>
    fun unfollowUser(followerId: String, followingId: String): Flow<Result<Unit>>
    fun getFollowers(userId: String): Flow<List<UserProfile>>
    fun getFollowing(userId: String): Flow<List<UserProfile>>
    fun checkIsFollowing(followerId: String, followingId: String): Flow<Boolean>
    fun upsertUserProfile(profile: UserProfile): Flow<Result<Unit>>
    fun updateNowPlaying(userId: String, update: NowPlayingUpdate): Flow<Result<Unit>>
    fun updateTopArtists(userId: String, topArtists: List<com.tan.domain.data.entities.TopArtistDto>): Flow<Result<Unit>>

    /** Memperbarui catatan ("Notes IG") milik user; kirim null untuk menghapus. */
    fun updateNote(userId: String, note: String?): Flow<Result<Unit>>
}

