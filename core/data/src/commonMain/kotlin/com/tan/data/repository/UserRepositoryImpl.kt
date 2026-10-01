package com.tan.data.repository

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.entities.UserFollow
import com.tan.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.flow
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.auth.auth
import com.tan.domain.manager.DataStoreManager
import com.tan.data.sync.LegacyProfilePrivacyMigration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UserRepositoryImpl(
    private val supabase: SupabaseClient,
    private val preferences: DataStoreManager? = null,
) : UserRepository {
    private val privacyMutex = Mutex()

    private suspend fun publishPrivacy(setting: String, visible: Boolean) {
        supabase.postgrest.rpc("gratify_set_profile_privacy", buildJsonObject {
            put("p_setting", setting); put("p_visible", visible)
        })
    }

    override suspend fun setProfilePrivacy(setting: String, visible: Boolean): Result<Unit> = try {
        privacyMutex.withLock {
            val owner = requireNotNull(supabase.auth.currentUserOrNull()?.id)
            preferences?.let { LegacyProfilePrivacyMigration(it).apply(owner) { key, value ->
                check(supabase.auth.currentUserOrNull()?.id == owner) { "Account ownership changed" }
                publishPrivacy(key, value)
            } }
            check(supabase.auth.currentUserOrNull()?.id == owner) { "Account ownership changed" }
            publishPrivacy(setting, visible)
        }
        Result.success(Unit)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }


    override fun getUserProfile(userId: String): Flow<UserProfile?> = flow {
        // Penting: emit HARUS di luar try/catch. Kalau di dalam, saat consumer memakai
        // first()/firstOrNull() dan membatalkan flow, AbortFlowException dari emit akan
        // tertangkap catch lalu emit(null) → "Emissions from 'catch' blocks are prohibited" → crash.
        val profile = try {
            if (preferences != null && supabase.auth.currentUserOrNull()?.id == userId) {
                privacyMutex.withLock {
                    LegacyProfilePrivacyMigration(preferences).apply(userId) { key, value ->
                        check(supabase.auth.currentUserOrNull()?.id == userId) { "Account ownership changed" }
                        publishPrivacy(key, value)
                    }
                }
            }
            supabase.postgrest["public_profiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<UserProfile>()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            
            val results = supabase.postgrest["public_profiles"]
                .select {
                    filter {
                        ilike("display_name", "%$q%")
                    }
                }
                .decodeList<UserProfile>()
            emit(results)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(emptyList())
        }
    }

    override fun followUser(followerId: String, followingId: String): Flow<Result<Unit>> = flow {
        try {
            val follow = UserFollow(followerId = followerId, followingId = followingId)
            supabase.postgrest["follows"].insert(follow)
            emit(Result.success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
                supabase.postgrest["public_profiles"]
                    .select {
                        filter {
                            isIn("id", followerIds)
                        }
                    }
                    .decodeList<UserProfile>()
            } catch (e: Exception) {
            if (e is CancellationException) throw e
                emptyList()
            }
            
            val profileMap = profiles.associateBy { it.id }
            val completeProfiles = followerIds.map { fId ->
                profileMap[fId] ?: UserProfile(id = fId, displayName = "Pengguna Gratify", avatarUrl = null)
            }
            emit(completeProfiles)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                supabase.postgrest["public_profiles"]
                    .select {
                        filter {
                            isIn("id", followingIds)
                        }
                    }
                    .decodeList<UserProfile>()
            } catch (e: Exception) {
            if (e is CancellationException) throw e
                emptyList()
            }
            
            val profileMap = profiles.associateBy { it.id }
            val completeProfiles = followingIds.map { fId ->
                profileMap[fId] ?: UserProfile(id = fId, displayName = "Pengguna Gratify", avatarUrl = null)
            }
            emit(completeProfiles)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            emit(false)
        }
    }


    override fun upsertUserProfile(profile: UserProfile): Flow<Result<Unit>> = flow {
        try {
            val json = Json.encodeToJsonElement(profile).jsonObject
            val payload = JsonObject(json.filterKeys { it !in setOf("show_followers", "show_playlists", "show_recent_artists") })
            supabase.postgrest["profiles"].upsert(payload)
            emit(Result.success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }
}

