package com.tan.data.repository
import com.tan.data.sync.retryCloudRead

import com.tan.data.db.datasource.LocalDataSource
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.PairSongLocalPlaylist
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.data.model.social.CloudPlaylistItemDto
import com.tan.domain.extension.now
import com.tan.domain.repository.SocialRepository
import com.tan.logger.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.github.jan.supabase.auth.auth
import kotlin.coroutines.cancellation.CancellationException

@Serializable
private data class NewCloudPlaylistItemDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artist: String,
    val duration: Int,
    @SerialName("thumbnail_url") val thumbnailUrl: String?,
    val position: Int,
)

internal class SocialRepositoryImpl(
    private val supabase: SupabaseClient,
    private val localDataSource: LocalDataSource
) : SocialRepository {
    @Serializable
    private data class PlaylistTombstone(@SerialName("client_sync_id") val syncId: String)
    override suspend fun flushPlaylistRemovals(userId: String) {
        check(supabase.auth.currentUserOrNull()?.id == userId && localDataSource.getLibraryOwner() == userId)
        for (removal in localDataSource.getLibraryRemovals(userId, "cloud_playlists")) {
            // Old numeric deletion intents cannot identify a playlist across devices.
            // Retain them for explicit review instead of deleting an unrelated row.
            if (removal.itemId.toLongOrNull() != null) continue
            supabase.postgrest.rpc("gratify_delete_owned_playlist_v2", buildJsonObject {
                put("p_sync_id", removal.itemId)
            })
            localDataSource.acknowledgeLibraryRemoval(userId, "cloud_playlists", removal.itemId, removal.revision)
        }
        for (removal in localDataSource.getLibraryRemovals(userId, "shared_playlist_saves")) {
            supabase.postgrest["shared_playlist_saves"].delete {
                filter { eq("user_id", userId); eq("playlist_id", removal.itemId) }
            }
            localDataSource.acknowledgeLibraryRemoval(userId, "shared_playlist_saves", removal.itemId, removal.revision)
        }
    }


    override suspend fun syncUpPlaylist(localPlaylistId: Long, userId: String, isPublic: Boolean?): Flow<Result<String>> = flow {
        val result = try {
            check(supabase.auth.currentUserOrNull()?.id == userId && localDataSource.getLibraryOwner() == userId)
            val syncId = localDataSource.ensurePlaylistSyncId(localPlaylistId, userId)
            val playlist = requireNotNull(localDataSource.getLocalPlaylist(localPlaylistId)) { "Playlist not found" }
            val pairs = localDataSource.getAllPlaylistPairSongByPosition(localPlaylistId).orEmpty()
            val songs = localDataSource.getSongByListVideoIdFull(pairs.map { it.songId }).associateBy { it.videoId }
            // Missing local metadata must fail before replacement, never silently drop tracks.
            val items = pairs.mapIndexed { index, pair ->
                val song = requireNotNull(songs[pair.songId]) { "Playlist song metadata missing" }
                NewCloudPlaylistItemDto("", song.videoId, song.title, song.artistName?.joinToString(", ").orEmpty(),
                    song.durationSeconds, song.thumbnails, index)
            }
            check(playlist.syncId == syncId && localDataSource.getLibraryOwner() == userId && supabase.auth.currentUserOrNull()?.id == userId) {
                "Account ownership changed"
            }
            val saved = supabase.postgrest.rpc("gratify_replace_cloud_playlist_v2", buildJsonObject {
                put("p_sync_id", syncId)
                put("p_title", playlist.title)
                put("p_thumbnail_url", (playlist.thumbnail ?: songs.values.firstOrNull()?.thumbnails)?.let(::JsonPrimitive) ?: JsonNull)
                put("p_is_public", isPublic?.let(::JsonPrimitive) ?: JsonNull)
                put("p_tracks", Json.encodeToJsonElement(items))
            }).decodeSingle<PlaylistRpcResult>()
            Result.success(saved.playlistId)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e) }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override suspend fun unshareOrHideCloudPlaylist(localPlaylistId: Long, userId: String): Flow<Result<Unit>> = flow {
        val result = try {
            val syncId = localDataSource.ensurePlaylistSyncId(localPlaylistId, userId)
            check(supabase.auth.currentUserOrNull()?.id == userId)
            supabase.postgrest.rpc("gratify_hide_owned_playlist_v2", buildJsonObject { put("p_sync_id", syncId) })
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteCloudPlaylist(localPlaylistId: Long, userId: String, title: String?): Flow<Result<Unit>> = flow {
        val result = try {
            val syncId = localDataSource.ensurePlaylistSyncId(localPlaylistId, userId)
            check(supabase.auth.currentUserOrNull()?.id == userId)
            supabase.postgrest.rpc("gratify_delete_owned_playlist_v2", buildJsonObject { put("p_sync_id", syncId) })
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override suspend fun syncDownPlaylists(userId: String): Flow<Result<Int>> = flow {
        val result = try {
            check(supabase.auth.currentUserOrNull()?.id == userId && localDataSource.getLibraryOwner() == userId)
            try { flushPlaylistRemovals(userId) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Logger.w("SocialRepositoryImpl", "Playlist deletion will retry") }
            val pending = localDataSource.getLibraryRemovals(userId, "cloud_playlists").map { it.itemId }.toSet()
            val tombstones = retryCloudRead {
                check(supabase.auth.currentUserOrNull()?.id == userId)
                supabase.postgrest["playlist_sync_tombstones"]
                    .select { filter { eq("user_id", userId) } }.decodeList<PlaylistTombstone>().map { it.syncId }
            }
            check(supabase.auth.currentUserOrNull()?.id == userId)
            localDataSource.applyPlaylistTombstones(userId, tombstones)
            val playlists = retryCloudRead {
                check(supabase.auth.currentUserOrNull()?.id == userId)
                supabase.postgrest["cloud_playlists"]
                    .select { filter { eq("user_id", userId) } }.decodeList<CloudPlaylistDto>()
            }
            var restored = 0
            for (playlist in playlists) {
                val cloudId = playlist.id ?: continue
                // Legacy server rows receive their own ID in migration 007. Never guess by title/local ID.
                val syncId = requireNotNull(playlist.clientSyncId) { "Playlist identity migration is required" }
                if (syncId in pending || localDataSource.getLocalPlaylistBySyncId(syncId) != null) continue
                val items = retryCloudRead {
                    check(supabase.auth.currentUserOrNull()?.id == userId)
                    supabase.postgrest.rpc("gratify_get_cloud_playlist_items", buildJsonObject {
                        put("p_playlist_id", cloudId)
                    }).decodeList<CloudPlaylistItemDto>()
                }
                val songs = items.map { item ->
                    SongEntity(videoId = item.videoId, title = item.title, artistName = listOf(item.artist),
                        duration = "${item.duration / 60}:${(item.duration % 60).toString().padStart(2, '0')}",
                        durationSeconds = item.duration, thumbnails = item.thumbnailUrl,
                        isAvailable = true, isExplicit = false, likeStatus = "INDIFFERENT",
                        videoType = "MUSIC_VIDEO_TYPE_ATV", category = null, resultType = null)
                }
                check(supabase.auth.currentUserOrNull()?.id == userId)
                if (localDataSource.restoreCloudPlaylist(userId,
                    LocalPlaylistEntity(title = playlist.title, thumbnail = playlist.thumbnailUrl,
                        tracks = songs.map { it.videoId }, syncId = syncId), songs)) restored++
            }
            Result.success(restored)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override suspend fun getUserPublicPlaylists(userId: String): Flow<Result<List<CloudPlaylistDto>>> = flow {
        try {
            val playlists = supabase.postgrest["cloud_playlists"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_public", true)
                    }
                }
                .decodeList<CloudPlaylistDto>()
            emit(Result.success(playlists))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getCloudPlaylistItems(playlistId: String): Flow<Result<List<CloudPlaylistItemDto>>> = flow {
        try {
            val items = supabase.postgrest.rpc("gratify_get_cloud_playlist_items", buildJsonObject {
                put("p_playlist_id", playlistId)
            }).decodeList<CloudPlaylistItemDto>()
            emit(Result.success(items.sortedBy { it.position }))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cleanupDuplicatePlaylists(userId: String): Result<Int> {
        // Equal titles are valid. Stable server identities enforce uniqueness for new writes.
        // Historical rows need an explicit review before any merge or removal.
        return Result.success(0)
    }
}
