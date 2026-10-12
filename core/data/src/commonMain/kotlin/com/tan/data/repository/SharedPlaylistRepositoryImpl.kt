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
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

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
    private val covers = PlaylistCoverStorage(supabase)

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadPlaylistThumbnail(localPlaylistId: Long, image: ByteArray): Result<String> = try {
        require(image.size in 3..5 * 1024 * 1024 && image[0] == 0xff.toByte() && image[1] == 0xd8.toByte()) { "Invalid JPEG image" }
        val owner = requireNotNull(supabase.auth.currentUserOrNull()?.id) { "Login required" }
        val local = requireNotNull(localDataSource)
        check(local.getLibraryOwner() == owner)
        check(local.getLocalPlaylist(localPlaylistId)?.sourceSharedPlaylistId == null) { "Saved playlists cannot be edited" }
        val syncId = local.ensurePlaylistSyncId(localPlaylistId, owner)
        val path = "$owner/$syncId/${Uuid.random()}.jpg"
        val bucket = supabase.storage.from("playlist-covers")
        bucket.upload(path, image) { contentType = ContentType.Image.JPEG; upsert = false }
        check(supabase.auth.currentUserOrNull()?.id == owner && local.getLibraryOwner() == owner)
        Result.success(bucket.publicUrl(path))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }

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
            val playlists = supabase.postgrest.rpc("gratify_get_public_playlists")
                .decodeList<SharedPlaylist>()
            val automatic=playlistArtwork(supabase,playlists.mapNotNull{it.id})
            val workspaces = if(!com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled)emptyList() else supabase.postgrest.rpc("gratify_workspace", buildJsonObject {
                put("p_action", "public"); put("p_args", buildJsonObject {})
            }).decodeAs<JsonArray>().map { element ->
                val row = element.jsonObject
                SharedPlaylist(id = "workspace:${row.getValue("id").jsonPrimitive.content}",
                    userId = row.getValue("owner_id").jsonPrimitive.content,
                    thumbnailUrl = row["cover_path"]?.jsonPrimitive?.contentOrNull?.let { supabase.storage.from("workspace-covers").publicUrl(it) },
                    title = row.getValue("title").jsonPrimitive.content,
                    creatorName = "Playlist kolaboratif", createdAt = row["created_at"]?.jsonPrimitive?.content,
                    isPublic = true,automaticImages=row["automatic_images"]?.jsonArray?.map{it.jsonPrimitive.content}.orEmpty())
            }

            val cleaned = (playlists.map{it.copy(automaticImages=automatic[it.id].orEmpty())} + workspaces).map {
                if (it.title.contains("|||")) {
                    it.copy(title = it.title.substringBeforeLast("|||"))
                } else it
            }
            emit(Result.success(covers.map(cleaned,{it.thumbnailUrl}) { row,url -> row.copy(thumbnailUrl=url) }))
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
                .decodeList<SharedPlaylist>().firstOrNull()
                ?: run {
                    val cloud = supabase.postgrest["cloud_playlists"].select {
                        filter { eq("id", playlistId) }
                    }.decodeSingle<CloudPlaylistDto>()
                    supabase.postgrest["shared_playlists"].select {
                        filter { eq("user_id", requireNotNull(cloud.userId)); eq("client_sync_id", requireNotNull(cloud.clientSyncId)) }
                    }.decodeSingle<SharedPlaylist>()
                }
            
            val mapped = if (playlist.title.contains("|||")) {
                playlist.copy(title = playlist.title.substringBeforeLast("|||"))
            } else playlist
            
            Result.success(mapped.copy(thumbnailUrl = covers.displayUrl(mapped.thumbnailUrl),
                automaticImages=playlistArtwork(supabase,listOfNotNull(mapped.id))[mapped.id].orEmpty()))
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
            check(supabase.auth.currentUserOrNull()?.id == userId) { "Account ownership changed" }
            supabase.postgrest.rpc("gratify_set_playlist_saved", buildJsonObject {
                put("p_playlist_id", playlistId); put("p_saved", true); put("p_expected_owner", userId)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun removePlaylistSave(playlistId: String, userId: String): Result<Unit> {
        return try {
            check(supabase.auth.currentUserOrNull()?.id == userId) { "Account ownership changed" }
            supabase.postgrest.rpc("gratify_set_playlist_saved", buildJsonObject {
                put("p_playlist_id", playlistId); put("p_saved", false); put("p_expected_owner", userId)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
