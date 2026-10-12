package com.tan.domain.repository

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.entities.UserFollow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class VisibleFollowCounts(val followers: Int, val following: Int)

interface UserRepository {
    suspend fun setProfilePrivacy(setting: String, visible: Boolean): Result<Unit>

    fun getUserProfile(userId: String): Flow<UserProfile?>
    fun fetchUserProfile(userId: String): Flow<Result<UserProfile?>> = getUserProfile(userId).map { Result.success(it) }
    fun fetchFollowers(userId: String, offset: Int = 0, limit: Int = 40): Flow<Result<List<UserProfile>>> =
        getFollowers(userId).map { Result.success(it.drop(offset).take(limit)) }
    fun fetchFollowing(userId: String, offset: Int = 0, limit: Int = 40): Flow<Result<List<UserProfile>>> =
        getFollowing(userId).map { Result.success(it.drop(offset).take(limit)) }
    fun getVisibleFollowCounts(userId: String): Flow<Result<VisibleFollowCounts>>
    fun searchUsersPage(query: String, offset: Int = 0, limit: Int = 40): Flow<List<UserProfile>> =
        searchUsers(query).map { it.drop(offset).take(limit) }
    fun searchUsers(query: String): Flow<List<UserProfile>>
    fun followUser(followerId: String, followingId: String): Flow<Result<Unit>>
    fun unfollowUser(followerId: String, followingId: String): Flow<Result<Unit>>
    fun getFollowers(userId: String): Flow<List<UserProfile>>
    fun getFollowing(userId: String): Flow<List<UserProfile>>
    fun checkIsFollowing(followerId: String, followingId: String): Flow<Boolean>
    fun fetchIsFollowing(followerId: String, followingId: String): Flow<Result<Boolean>> =
        checkIsFollowing(followerId, followingId).map { Result.success(it) }
    fun upsertUserProfile(profile: UserProfile): Flow<Result<Unit>>
    fun updateNowPlaying(userId: String, update: NowPlayingUpdate): Flow<Result<Unit>>
    fun updateTopArtists(userId: String, topArtists: List<com.tan.domain.data.entities.TopArtistDto>): Flow<Result<Unit>>

    /** Memperbarui catatan ("Notes IG") milik user; kirim null untuk menghapus. */
    fun updateNote(userId: String, note: String?): Flow<Result<Unit>>
}

