package com.tan.data.repository

import com.tan.domain.data.entities.SharedPlaylist
import com.tan.domain.data.entities.SharedPlaylistTrack
import com.tan.domain.repository.SharedPlaylistRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import io.github.jan.supabase.auth.auth
import kotlin.coroutines.cancellation.CancellationException

import com.tan.domain.data.model.social.CloudPlaylistDto

@Serializable
private data class SharedPlaylistSaveDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class NewSharedPlaylistDto(
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String?,
    @SerialName("creator_name") val creatorName: String?,
)

@Serializable
internal data class NewSharedPlaylistTrackDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artists: String,
    @SerialName("duration_seconds") val durationSeconds: Int?,
)

internal fun SharedPlaylistTrack.toNewSharedPlaylistTrackDto() = NewSharedPlaylistTrackDto(
    playlistId = playlistId,
    videoId = videoId,
    title = title,
    artists = artists,
    durationSeconds = durationSeconds,
)

// Hanya untuk mengambil kolom playlist_id dari shared_playlist_tracks (cek playlist mana yang punya lagu).
@Serializable
private data class SharedPlaylistTrackPlaylistId(
    @SerialName("playlist_id") val playlistId: String,
)

internal class SharedPlaylistRepositoryImpl(
    private val supabase: SupabaseClient,
    private val localDataSource: com.tan.data.db.datasource.LocalDataSource? = null
) : SharedPlaylistRepository {

    override suspend fun sharePlaylist(
        userId: String,
        localPlaylistId: Long,
        title: String,
        creatorName: String?,
        thumbnailUrl: String?,
        tracks: List<SharedPlaylistTrack>
    ): Result<String> {
        return try {
            check(supabase.auth.currentUserOrNull()?.id == userId) { "Account ownership mismatch" }
            val syncId = requireNotNull(localDataSource).ensurePlaylistSyncId(localPlaylistId, userId)
            val result = supabase.postgrest.rpc("gratify_replace_shared_playlist_v2", buildJsonObject {
                put("p_sync_id", syncId)
                put("p_title", title)
                put("p_creator_name", creatorName?.let(::JsonPrimitive) ?: JsonNull)
                put("p_thumbnail_url", thumbnailUrl?.let(::JsonPrimitive) ?: JsonNull)
                put("p_tracks", Json.encodeToJsonElement(tracks.map { it.toNewSharedPlaylistTrackDto() }))
            }).decodeSingle<PlaylistRpcResult>()
            Result.success(result.playlistId)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e) }
    }

    override suspend fun getSharedPlaylists(): Flow<Result<List<SharedPlaylist>>> = flow {
        try {
            val playlists = supabase.postgrest["shared_playlists"]
                .select()
                .decodeList<SharedPlaylist>()

            val cleaned = playlists.map {
                if (it.title.contains("|||")) {
                    it.copy(title = it.title.substringBeforeLast("|||"))
                } else it
            }
            emit(Result.success(cleaned))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    override suspend fun getSharedPlaylist(playlistId: String): Result<SharedPlaylist> {
        return try {
            val playlist = supabase.postgrest["shared_playlists"]
                .select {
                    filter {
                        eq("id", playlistId)
                    }
                }
                .decodeSingle<SharedPlaylist>()
            
            val mapped = if (playlist.title.contains("|||")) {
                playlist.copy(title = playlist.title.substringBeforeLast("|||"))
            } else playlist
            
            Result.success(mapped)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getSharedPlaylistTracks(playlistId: String): Flow<Result<List<SharedPlaylistTrack>>> = flow {
        try {
            val tracks = supabase.postgrest.rpc("gratify_get_shared_playlist_tracks", buildJsonObject {
                put("p_playlist_id", playlistId)
            }).decodeList<SharedPlaylistTrack>()
            emit(Result.success(tracks))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    override suspend fun deleteSharedPlaylist(playlistId: String): Result<Unit> = try {
        val ownerId = requireNotNull(supabase.auth.currentUserOrNull()?.id) { "Authentication required" }
        val shared = supabase.postgrest["shared_playlists"]
            .select { filter { eq("id", playlistId); eq("user_id", ownerId) } }.decodeSingleOrNull<SharedPlaylist>()
        val syncId = if (shared != null) shared.clientSyncId else {
            supabase.postgrest["cloud_playlists"]
                .select { filter { eq("id", playlistId); eq("user_id", ownerId) } }
                .decodeSingleOrNull<CloudPlaylistDto>()?.clientSyncId
        }
        requireNotNull(syncId) { "Owned playlist identity not found" }
        check(supabase.auth.currentUserOrNull()?.id == ownerId) { "Account ownership changed" }
        supabase.postgrest.rpc("gratify_hide_owned_playlist_v2", buildJsonObject { put("p_sync_id", syncId) })
        Result.success(Unit)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    override suspend fun deleteSharedPlaylistByLocalId(userId: String, localPlaylistId: Long, fallbackTitle: String): Result<Unit> = try {
        check(supabase.auth.currentUserOrNull()?.id == userId)
        val syncId = requireNotNull(localDataSource).ensurePlaylistSyncId(localPlaylistId, userId)
        supabase.postgrest.rpc("gratify_hide_owned_playlist_v2", buildJsonObject { put("p_sync_id", syncId) })
        Result.success(Unit)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    override suspend fun recordPlaylistSave(playlistId: String, userId: String): Result<Unit> {
        return try {
            // Upsert dengan ignoreDuplicates: bila (playlist_id, user_id) sudah ada,
            // tidak ada baris baru yang di-insert → trigger tidak menaikkan add_count lagi.
            supabase.postgrest["shared_playlist_saves"].upsert(
                SharedPlaylistSaveDto(playlistId = playlistId, userId = userId)
            ) {
                onConflict = "playlist_id,user_id"
                ignoreDuplicates = true
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun removePlaylistSave(playlistId: String, userId: String): Result<Unit> {
        return try {
            supabase.postgrest["shared_playlist_saves"].delete {
                filter {
                    eq("playlist_id", playlistId)
                    eq("user_id", userId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
