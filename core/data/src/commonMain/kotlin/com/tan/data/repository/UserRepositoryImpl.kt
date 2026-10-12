package com.tan.data.repository

import com.tan.domain.utils.uploadedProfileAvatar

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.entities.UserFollow
import com.tan.domain.repository.UserRepository
import com.tan.domain.repository.VisibleFollowCounts
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.flow
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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


    override fun getUserProfile(userId: String): Flow<UserProfile?> = fetchUserProfile(userId).map { it.getOrNull() }

    override fun fetchUserProfile(userId: String): Flow<Result<UserProfile?>> = resultFlow {
        // Penting: emit HARUS di luar try/catch. Kalau di dalam, saat consumer memakai
        // first()/firstOrNull() dan membatalkan flow, AbortFlowException dari emit akan
        // tertangkap catch lalu emit(null) → "Emissions from 'catch' blocks are prohibited" → crash.
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
                .decodeSingleOrNull<UserProfile>()?.let { it.copy(avatarUrl = it.avatarUrl.uploadedProfileAvatar()) }
    }

    private fun <T> resultFlow(block: suspend () -> T): Flow<Result<T>> = flow {
        val result = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getVisibleFollowCounts(userId: String): Flow<Result<VisibleFollowCounts>> = resultFlow {
        suspend fun count(column: String): Int = requireNotNull(supabase.postgrest["follows"]
            .select { head = true; count(Count.EXACT); filter { eq(column, userId) } }.countOrNull())
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        VisibleFollowCounts(count("following_id"), count("follower_id"))
    }

    override fun fetchIsFollowing(followerId: String, followingId: String): Flow<Result<Boolean>> = resultFlow {
        requireNotNull(supabase.postgrest["follows"].select {
            head = true; count(Count.EXACT); filter { eq("follower_id", followerId); eq("following_id", followingId) }
        }.countOrNull()) > 0
    }

    override fun fetchFollowers(userId: String, offset: Int, limit: Int): Flow<Result<List<UserProfile>>> =
        relationPage(userId, true, offset, limit)

    override fun fetchFollowing(userId: String, offset: Int, limit: Int): Flow<Result<List<UserProfile>>> =
        relationPage(userId, false, offset, limit)

    private fun relationPage(userId: String, followers: Boolean, offset: Int, limit: Int): Flow<Result<List<UserProfile>>> = resultFlow {
        require(offset >= 0 && limit in 1..100)
        val relation = if (followers) "following_id" else "follower_id"
        val identity = if (followers) "follower_id" else "following_id"
        val links = supabase.postgrest["follows"].select {
            filter { eq(relation, userId) }; order(identity, Order.ASCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<UserFollow>()
        val ids = links.map { if (followers) it.followerId else it.followingId }.filter(String::isNotBlank)
        if (ids.isEmpty()) emptyList() else {
            val profiles = supabase.postgrest["public_profiles"].select {
                filter { isIn("id", ids) }
            }.decodeList<UserProfile>().associateBy { it.id }
            ids.map { id -> profiles[id]?.let { it.copy(avatarUrl = it.avatarUrl.uploadedProfileAvatar()) }
                ?: UserProfile(id, "Pengguna Gratify") }
        }
    }

    override fun searchUsers(query: String): Flow<List<UserProfile>> = matchingUsers(query, 0, null)

    override fun searchUsersPage(query: String, offset: Int, limit: Int): Flow<List<UserProfile>> = matchingUsers(query, offset, limit)

    private fun matchingUsers(query: String, offset: Int, limit: Int?): Flow<List<UserProfile>> = flow {
            require(offset >= 0 && (limit == null || limit in 1..100))
            val q = query.trim()
            if (q.isEmpty()) {
                emit(emptyList())
                return@flow
            }
            
            val results = supabase.postgrest["public_profiles"]
                .select(columns = Columns.list("id", "display_name", "avatar_url")) {
                    filter {
                        ilike("display_name", "%$q%")
                    }
                    order("display_name", Order.ASCENDING); order("id", Order.ASCENDING)
                    if (limit != null) range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<UserProfile>().map { it.copy(avatarUrl = it.avatarUrl.uploadedProfileAvatar()) }
            emit(results)
    }.flowOn(Dispatchers.IO)

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
                    .decodeList<UserProfile>().map { it.copy(avatarUrl = it.avatarUrl.uploadedProfileAvatar()) }
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
                    .decodeList<UserProfile>().map { it.copy(avatarUrl = it.avatarUrl.uploadedProfileAvatar()) }
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
            // Only identity fields belong to this operation; an explicit null removes the old avatar.
            val payload = buildJsonObject {
                put("id", profile.id)
                put("display_name", profile.displayName?.let(::JsonPrimitive) ?: JsonNull)
                put("avatar_url", profile.avatarUrl.uploadedProfileAvatar()?.let(::JsonPrimitive) ?: JsonNull)
            }
            supabase.postgrest["profiles"].upsert(payload)
            emit(Result.success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    override fun updateNowPlaying(userId: String, update: NowPlayingUpdate): Flow<Result<Unit>> = flow {
        try {
            // Playback must never overwrite an edited name/photo with an older local cache.
            // Explicit nulls clear stopped activity even when the client's serializer omits defaults.
            val activity = buildJsonObject {
                put("now_playing_video_id", update.nowPlayingVideoId?.let(::JsonPrimitive) ?: JsonNull)
                put("now_playing_title", update.nowPlayingTitle?.let(::JsonPrimitive) ?: JsonNull)
                put("now_playing_artist", update.nowPlayingArtist?.let(::JsonPrimitive) ?: JsonNull)
                put("last_active_at", update.lastActiveAt?.let(::JsonPrimitive) ?: JsonNull)
            }
            supabase.postgrest["profiles"].update(activity) {
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

