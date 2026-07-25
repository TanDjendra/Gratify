package com.tan.data.repository

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.entities.UserFollow
import com.tan.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.github.jan.supabase.postgrest.query.Columns

class UserRepositoryImpl(
    private val supabase: SupabaseClient
) : UserRepository {

    override fun getUserProfile(userId: String): Flow<UserProfile?> = flow {
        // Penting: emit HARUS di luar try/catch. Kalau di dalam, saat consumer memakai
        // first()/firstOrNull() dan membatalkan flow, AbortFlowException dari emit akan
        // tertangkap catch lalu emit(null) → "Emissions from 'catch' blocks are prohibited" → crash.
        val profile = try {
            supabase.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<UserProfile>()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        emit(profile)
    }

    override fun searchUsers(query: String): Flow<List<UserProfile>> = flow {
        try {
            val q = query.trim()
            if (q.isEmpty()) {
                emit(emptyList())
                return@flow
            }
            
            val results = supabase.postgrest["profiles"]
                .select {
                    filter {
                        ilike("display_name", "%$q%")
                    }
                }
                .decodeList<UserProfile>()
            emit(results)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(emptyList())
        }
    }

    override fun followUser(followerId: String, followingId: String): Flow<Result<Unit>> = flow {
        try {
            val follow = UserFollow(followerId = followerId, followingId = followingId)
            supabase.postgrest["follows"].insert(follow)
            emit(Result.success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    override fun unfollowUser(followerId: String, followingId: String): Flow<Result<Unit>> = flow {
        try {
            supabase.postgrest["follows"].delete {
                filter {
                    eq("follower_id", followerId)
                    eq("following_id", followingId)
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    override fun getFollowers(userId: String): Flow<List<UserProfile>> = flow {
        try {
            val follows = supabase.postgrest["follows"]
                .select {
                    filter {
                        eq("following_id", userId)
                    }
                }
                .decodeList<UserFollow>()

            if (follows.isEmpty()) {
                emit(emptyList())
                return@flow
            }

            val followerIds = follows.map { it.followerId }.filter { it.isNotBlank() }
            val profiles = try {
                supabase.postgrest["profiles"]
                    .select {
                        filter {
                            isIn("id", followerIds)
                        }
                    }
                    .decodeList<UserProfile>()
            } catch (e: Exception) {
                emptyList()
            }
            
            val profileMap = profiles.associateBy { it.id }
            val completeProfiles = followerIds.map { fId ->
                profileMap[fId] ?: UserProfile(id = fId, displayName = "Pengguna Gratify", avatarUrl = null)
            }
            emit(completeProfiles)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(emptyList())
        }
    }

    override fun getFollowing(userId: String): Flow<List<UserProfile>> = flow {
        try {
            val follows = supabase.postgrest["follows"]
                .select {
                    filter {
                        eq("follower_id", userId)
                    }
                }
                .decodeList<UserFollow>()

            if (follows.isEmpty()) {
                emit(emptyList())
                return@flow
            }

            val followingIds = follows.map { it.followingId }.filter { it.isNotBlank() }
            val profiles = try {
                supabase.postgrest["profiles"]
                    .select {
                        filter {
                            isIn("id", followingIds)
                        }
                    }
                    .decodeList<UserProfile>()
            } catch (e: Exception) {
                emptyList()
            }
            
            val profileMap = profiles.associateBy { it.id }
            val completeProfiles = followingIds.map { fId ->
                profileMap[fId] ?: UserProfile(id = fId, displayName = "Pengguna Gratify", avatarUrl = null)
            }
            emit(completeProfiles)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(emptyList())
        }
    }

    override fun checkIsFollowing(followerId: String, followingId: String): Flow<Boolean> = flow {
        try {
            val count = supabase.postgrest["follows"]
                .select {
                    filter {
                        eq("follower_id", followerId)
                        eq("following_id", followingId)
                    }
                }
                .decodeList<UserFollow>()
                .size
            emit(count > 0)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(false)
        }
    }


    override fun upsertUserProfile(profile: UserProfile): Flow<Result<Unit>> = flow {
        try {
            supabase.postgrest["profiles"].upsert(profile)
            emit(Result.success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    override fun updateNowPlaying(userId: String, update: NowPlayingUpdate): Flow<Result<Unit>> = flow {
        try {
            supabase.postgrest["profiles"].update(update) {
                filter {
                    eq("id", userId)
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    override fun updateTopArtists(userId: String, topArtists: List<com.tan.domain.data.entities.TopArtistDto>): Flow<Result<Unit>> = flow {
        try {
            @kotlinx.serialization.Serializable
            data class UpdateTopArtistsRequest(
                @kotlinx.serialization.SerialName("top_artists") val topArtists: List<com.tan.domain.data.entities.TopArtistDto>
            )
            supabase.postgrest["profiles"].update(UpdateTopArtistsRequest(topArtists)) {
                filter {
                    eq("id", userId)
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    override fun updateNote(userId: String, note: String?): Flow<Result<Unit>> = flow {
        try {
            // Tanpa nilai default agar field selalu ikut terkirim (termasuk null untuk menghapus).
            @kotlinx.serialization.Serializable
            data class UpdateNoteRequest(
                @kotlinx.serialization.SerialName("note") val note: String?,
                @kotlinx.serialization.SerialName("note_updated_at") val noteUpdatedAt: String?
            )
            val cleaned = note?.takeIf { it.isNotBlank() }
            // Simpan epoch millis (string) untuk kedaluwarsa 24 jam; null saat catatan dihapus.
            val updatedAt = cleaned?.let { kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString() }
            supabase.postgrest["profiles"].update(UpdateNoteRequest(cleaned, updatedAt)) {
                filter {
                    eq("id", userId)
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }
}

