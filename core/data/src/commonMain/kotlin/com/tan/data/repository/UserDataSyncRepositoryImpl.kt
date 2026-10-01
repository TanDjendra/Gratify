package com.tan.data.repository

import com.tan.data.sync.fromCloudTimestamp
import com.tan.data.sync.toCloudTimestamp
import com.tan.data.sync.cloudSettings
import com.tan.data.sync.restoreCloudSettings
import kotlinx.coroutines.CancellationException
import com.tan.domain.data.entities.QueueEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import kotlinx.serialization.json.*
import com.tan.data.sync.AccountLibraryStore
import com.tan.data.db.MusicDatabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import com.tan.data.db.datasource.LocalDataSource
import com.tan.domain.data.entities.AlbumEntity
import com.tan.domain.data.entities.ArtistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.sync.CloudFollowedArtistDto
import com.tan.domain.data.model.sync.CloudLikedSongDto
import com.tan.domain.data.model.sync.CloudPlayHistoryDto
import com.tan.domain.data.model.sync.CloudQueueItemDto
import com.tan.domain.data.model.sync.CloudSavedAlbumDto
import com.tan.domain.data.model.sync.CloudUserSettingsDto
import com.tan.domain.data.model.sync.SyncReport
import com.tan.domain.extension.now
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.UserDataSyncRepository
import com.tan.logger.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class UserDataSyncRepositoryImpl(
    private val supabase: SupabaseClient,
    private val localDataSource: LocalDataSource,
    private val dataStoreManager: DataStoreManager,
    database: MusicDatabase,
) : UserDataSyncRepository {

    companion object {
        private const val TABLE_LIKED_SONGS = "user_liked_songs"
        private const val TABLE_FOLLOWED_ARTISTS = "user_followed_artists"
        private const val TABLE_SAVED_ALBUMS = "user_saved_albums"
        private const val TABLE_PLAY_HISTORY = "user_play_history"
        private const val TABLE_QUEUE = "user_queue"
        private const val TABLE_SETTINGS = "user_settings"
        private const val BATCH_SIZE = 100
    }

    private suspend inline fun <T> cancellableResult(block: () -> T): Result<T> = try { Result.success(block()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }

    private val accountLibraryStore = AccountLibraryStore(database)

    override suspend fun activateLocalAccount(userId: String, legacyOwner: String?) =
        accountLibraryStore.activate(userId, legacyOwner)

    override suspend fun deleteAccount(userId: String) {
        requireOwner(userId)
        supabase.postgrest.rpc("gratify_prepare_account_deletion")
        // Storage API removes the file as well as its metadata; SQL alone cannot do that.
        supabase.storage.from("avatars").delete(listOf("$userId.jpg"))
        supabase.postgrest.rpc("gratify_delete_account")
        accountLibraryStore.removeAccount(userId)
    }

    private suspend fun requireOwner(userId: String) {
        check(supabase.auth.currentUserOrNull()?.id == userId && localDataSource.getLibraryOwner() == userId) {
            "Account does not own the active local library"
        }
    }

    @kotlinx.serialization.Serializable
    private data class RemoteLibraryState(@kotlinx.serialization.SerialName("item_id") val itemId: String, val enabled: Boolean)

    /** Publish explicit edits only; an unchanged snapshot can never resurrect a remote deletion. */
    private suspend fun uploadLibraryChanges(table: String, userId: String): Int {
        requireOwner(userId)
        val changes = localDataSource.getLibraryChanges(userId, table)
        for (batch in changes.chunked(BATCH_SIZE)) {
            val payload = batch.map { change ->
                val item = if (change.enabled == 0) JsonNull else when (table) {
                    TABLE_LIKED_SONGS -> Json.encodeToJsonElement(requireNotNull(localDataSource.getSong(change.itemId)).toCloudLikedSongDto(userId))
                    TABLE_FOLLOWED_ARTISTS -> Json.encodeToJsonElement(requireNotNull(localDataSource.getArtist(change.itemId)).toCloudFollowedArtistDto(userId))
                    TABLE_SAVED_ALBUMS -> Json.encodeToJsonElement(requireNotNull(localDataSource.getAlbum(change.itemId)).toCloudSavedAlbumDto(userId))
                    else -> error("Unsupported library kind")
                }
                buildJsonObject { put("kind", table); put("item_id", change.itemId); put("enabled", change.enabled != 0); put("payload", item) }
            }
            supabase.postgrest.rpc("gratify_apply_library_changes", buildJsonObject { put("p_changes", JsonArray(payload)) })
            batch.forEach { localDataSource.acknowledgeLibraryRemoval(userId, table, it.itemId, it.revision) }
        }
        return changes.size
    }

    private suspend fun applyRemoteLibraryChanges(table: String, userId: String): Set<String> {
        val pending = localDataSource.getLibraryChanges(userId, table).map { it.itemId }.toSet()
        val states = supabase.postgrest["user_library_state"].select { filter { eq("user_id", userId); eq("kind", table) } }.decodeList<RemoteLibraryState>()
        states.filter { !it.enabled && it.itemId !in pending }.forEach { localDataSource.applyRemoteLibraryRemoval(table, it.itemId) }
        return pending + states.filter { !it.enabled }.map { it.itemId }
    }

    private fun String?.toLocalDateTimeOrNull(): LocalDateTime? =
        this.fromCloudTimestamp()

    override suspend fun syncUp(userId: String): Flow<Result<SyncReport>> = flow {
        try {
            requireOwner(userId)
            val liked = syncUpLikedSongs(userId)
            val artists = syncUpFollowedArtists(userId)
            val albums = syncUpSavedAlbums(userId)
            val historyResult = syncUpPlayHistory(userId)
            val queueResult = syncUpQueue(userId)
            val settingsResult = syncUpSettings(userId)

            // Lagu disukai / artis diikuti / album disimpan adalah data PUSTAKA user.
            // Kalau salah satu gagal di-push, syncUp TIDAK boleh dianggap sukses: pemanggil
            // (performLoginSync, logout) memakai hasil ini untuk memutuskan boleh-tidaknya
            // menghapus DB lokal. Melaporkan sukses palsu = data user hilang permanen.
            val libraryFailure = listOf(liked, artists, albums, historyResult, queueResult, settingsResult).firstNotNullOfOrNull { it.exceptionOrNull() }
            if (libraryFailure != null) {
                Logger.e("UserDataSync", "syncUp failed for $userId: [details omitted]")
                emit(Result.failure(libraryFailure))
                return@flow
            }

            emit(
                Result.success(
                    SyncReport(
                        likedSongs = liked.getOrDefault(0),
                        artists = artists.getOrDefault(0),
                        albums = albums.getOrDefault(0),
                        history = historyResult.getOrThrow(),
                        queue = queueResult.getOrThrow(),
                        settingsSynced = settingsResult.isSuccess,
                    )
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e("UserDataSync", "syncUp failed: [details omitted]")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun syncDown(userId: String): Flow<Result<SyncReport>> = flow {
        try {
            requireOwner(userId)
            val liked = syncDownLikedSongs(userId)
            val artists = syncDownFollowedArtists(userId)
            val albums = syncDownSavedAlbums(userId)
            val historyResult = syncDownPlayHistory(userId)
            val queueResult = syncDownQueue(userId)
            val settingsResult = syncDownSettings(userId)

            val libraryFailure = listOf(liked, artists, albums, historyResult, queueResult, settingsResult).firstNotNullOfOrNull { it.exceptionOrNull() }
            if (libraryFailure != null) {
                Logger.e("UserDataSync", "syncDown failed for $userId: [details omitted]")
                emit(Result.failure(libraryFailure))
                return@flow
            }

            emit(
                Result.success(
                    SyncReport(
                        likedSongs = liked.getOrDefault(0),
                        artists = artists.getOrDefault(0),
                        albums = albums.getOrDefault(0),
                        history = historyResult.getOrThrow(),
                        queue = queueResult.getOrThrow(),
                        settingsSynced = settingsResult.isSuccess,
                    )
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e("UserDataSync", "syncDown failed: [details omitted]")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    // ===================== SYNC UP =====================

    override suspend fun syncUpLikedSongs(userId: String): Result<Int> = cancellableResult { uploadLibraryChanges(TABLE_LIKED_SONGS, userId) }
    override suspend fun syncUpFollowedArtists(userId: String): Result<Int> = cancellableResult { uploadLibraryChanges(TABLE_FOLLOWED_ARTISTS, userId) }
    override suspend fun syncUpSavedAlbums(userId: String): Result<Int> = cancellableResult { uploadLibraryChanges(TABLE_SAVED_ALBUMS, userId) }

    override suspend fun syncUpPlayHistory(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        // Sync most played songs as play history (top 200)
        val songs = buildList {
            var offset = 0
            while (true) {
                val page = localDataSource.getPlayHistory(BATCH_SIZE, offset)
                addAll(page)
                if (page.size < BATCH_SIZE) break
                offset += BATCH_SIZE
            }
        }
        val historyDtos = songs.filter { it.totalPlayTime > 0 }.map { song ->
            CloudPlayHistoryDto(
                id = "${userId}_${song.videoId}",
                userId = userId,
                videoId = song.videoId,
                title = song.title,
                artistName = song.artistName?.joinToString(", "),
                duration = song.durationSeconds,
                thumbnailUrl = song.thumbnails,
                playedAt = song.inLibrary.toCloudTimestamp(),
                listenCount = song.totalPlayTime,
            )
        }
        if (historyDtos.isNotEmpty()) {
            historyDtos.chunked(BATCH_SIZE).forEach { supabase.postgrest[TABLE_PLAY_HISTORY].upsert(it) }
        }
        historyDtos.size
    }

    override suspend fun syncUpQueue(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        val queue = localDataSource.getQueue().firstOrNull() ?: QueueEntity(listTrack = emptyList())
        val dtos = queue.listTrack.mapIndexed { index, track ->
            CloudQueueItemDto(
                id = "${userId}_queue_$index",
                userId = userId,
                videoId = track.videoId,
                title = track.title,
                artistName = track.artists?.joinToString(", ") { it.name },
                duration = track.durationSeconds,
                thumbnailUrl = track.thumbnails?.lastOrNull()?.url,
                position = index,
            )
        }
        supabase.postgrest.rpc("gratify_replace_queue", buildJsonObject {
            put("p_items", Json.encodeToJsonElement(dtos))
        })
        dtos.size
    }

    override suspend fun syncUpSettings(userId: String): Result<Unit> = cancellableResult {
        requireOwner(userId)
        val settingsMap = dataStoreManager.cloudSettings()
        val json = Json.encodeToString(settingsMap)
        val dto = CloudUserSettingsDto(userId = userId, settingsJson = json)
        supabase.postgrest[TABLE_SETTINGS].upsert(dto)
    }

    // ===================== SYNC DOWN =====================

    override suspend fun syncDownLikedSongs(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        val cloudSongs = supabase.postgrest[TABLE_LIKED_SONGS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudLikedSongDto>()

        val removed = applyRemoteLibraryChanges(TABLE_LIKED_SONGS, userId)
        var restored = 0
        for (dto in cloudSongs) {
            if (dto.videoId in removed) continue
            val existing = localDataSource.getSong(dto.videoId)
            if (existing == null) {
                localDataSource.insertRemoteSong(dto.toSongEntity())
            } else if (!existing.liked) {
                // Pakai tanggal like ASLI dari cloud, bukan now(), supaya urutan
                // "Recently added" di pustaka tidak teracak tiap kali ganti akun.
                localDataSource.updateLiked(1, dto.videoId, dto.favoriteAt.toLocalDateTimeOrNull() ?: now())
            }
            restored++
        }
        restored
    }

    override suspend fun syncDownFollowedArtists(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        val cloudArtists = supabase.postgrest[TABLE_FOLLOWED_ARTISTS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudFollowedArtistDto>()

        val removed = applyRemoteLibraryChanges(TABLE_FOLLOWED_ARTISTS, userId)
        var restored = 0
        for (dto in cloudArtists) {
            if (dto.channelId in removed) continue
            val existing = localDataSource.getArtist(dto.channelId)
            if (existing == null) {
                localDataSource.insertRemoteArtist(dto.toArtistEntity())
            } else if (!existing.followed) {
                localDataSource.updateFollowed(1, dto.channelId, dto.followedAt.toLocalDateTimeOrNull() ?: now())
            }
            restored++
        }
        restored
    }

    override suspend fun syncDownSavedAlbums(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        val cloudAlbums = supabase.postgrest[TABLE_SAVED_ALBUMS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudSavedAlbumDto>()

        val removed = applyRemoteLibraryChanges(TABLE_SAVED_ALBUMS, userId)
        var restored = 0
        for (dto in cloudAlbums) {
            if (dto.browseId in removed) continue
            val existing = localDataSource.getAlbum(dto.browseId)
            if (existing == null) {
                localDataSource.insertRemoteAlbum(dto.toAlbumEntity())
            } else if (!existing.liked) {
                localDataSource.updateAlbumLiked(1, dto.browseId, dto.favoriteAt.toLocalDateTimeOrNull() ?: now())
            }
            restored++
        }
        restored
    }

    override suspend fun syncDownPlayHistory(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        val cloudHistory = supabase.postgrest[TABLE_PLAY_HISTORY]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudPlayHistoryDto>()

        var restored = 0
        for (dto in cloudHistory) {
            val existing = localDataSource.getSong(dto.videoId)
            if (existing == null) {
                localDataSource.insertRemoteSong(
                    SongEntity(
                        videoId = dto.videoId,
                        title = dto.title ?: "",
                        artistName = dto.artistName?.split(", ")?.takeIf { it.isNotEmpty() },
                        duration = "${(dto.duration ?: 0) / 60}:${((dto.duration ?: 0) % 60).toString().padStart(2, '0')}",
                        durationSeconds = dto.duration ?: 0,
                        isAvailable = true,
                        isExplicit = false,
                        likeStatus = "INDIFFERENT",
                        thumbnails = dto.thumbnailUrl,
                        videoType = "MUSIC_VIDEO_TYPE_ATV",
                        category = null,
                        resultType = null,
                    )
                )
            }
            localDataSource.restorePlayHistory(dto.videoId, dto.listenCount.coerceAtLeast(1), dto.playedAt.toLocalDateTimeOrNull() ?: now())
            restored++
        }
        restored
    }

    override suspend fun syncDownQueue(userId: String): Result<Int> = cancellableResult {
        requireOwner(userId)
        val cloudQueue = supabase.postgrest[TABLE_QUEUE]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudQueueItemDto>()
        val tracks = cloudQueue.sortedBy { it.position }.map { dto ->
            Track(album = null, artists = dto.artistName?.let { listOf(Artist(name = it, id = null)) },
                duration = null, durationSeconds = dto.duration, isAvailable = true, isExplicit = false,
                likeStatus = null, thumbnails = dto.thumbnailUrl?.let { listOf(Thumbnail(url = it, width = 0, height = 0)) },
                title = dto.title.orEmpty(), videoId = dto.videoId, videoType = null, category = null,
                feedbackTokens = null, resultType = null)
        }
        localDataSource.recoverQueue(QueueEntity(listTrack = tracks))
        tracks.size
    }

    override suspend fun syncDownSettings(userId: String): Result<Unit> = cancellableResult {
        requireOwner(userId)
        val result = supabase.postgrest[TABLE_SETTINGS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudUserSettingsDto>()

        if (result.isNotEmpty()) {
            val settingsJson = result.first().settingsJson
            val settingsMap: Map<String, String?> = Json.decodeFromString(settingsJson)
            dataStoreManager.restoreCloudSettings(settingsMap)
        }
    }

    // ===================== CLEAR =====================

    override suspend fun clearCloudData(userId: String): Result<Unit> = cancellableResult {
        requireOwner(userId)
        supabase.postgrest[TABLE_LIKED_SONGS].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_FOLLOWED_ARTISTS].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_SAVED_ALBUMS].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_PLAY_HISTORY].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_QUEUE].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_SETTINGS].delete { filter { eq("user_id", userId) } }
    }

    override suspend fun clearLocalUserData(): Result<Unit> = cancellableResult {
        localDataSource.clearUserData()
        dataStoreManager.clearPerUserData()
    }

    override suspend fun clearLocalDatabase(): Result<Unit> = cancellableResult {
        localDataSource.clearUserData()
    }

    // ===================== MAPPERS =====================

    private fun SongEntity.toCloudLikedSongDto(userId: String) = CloudLikedSongDto(
        id = "${userId}_$videoId",
        userId = userId,
        videoId = videoId,
        title = title,
        artistName = artistName?.joinToString(", "),
        artistId = artistId?.firstOrNull(),
        albumName = albumName,
        albumId = albumId,
        duration = durationSeconds,
        thumbnailUrl = thumbnails,
        favoriteAt = favoriteAt?.toCloudTimestamp(),
    )

    private fun ArtistEntity.toCloudFollowedArtistDto(userId: String) = CloudFollowedArtistDto(
        id = "${userId}_$channelId",
        userId = userId,
        channelId = channelId,
        name = name,
        thumbnailUrl = thumbnails,
        followedAt = followedAt?.toCloudTimestamp(),
    )

    private fun AlbumEntity.toCloudSavedAlbumDto(userId: String) = CloudSavedAlbumDto(
        id = "${userId}_$browseId",
        userId = userId,
        browseId = browseId,
        title = title,
        artistName = artistName?.joinToString(", "),
        artistId = artistId?.firstOrNull(),
        thumbnailUrl = thumbnails,
        trackCount = trackCount,
        favoriteAt = favoriteAt?.toCloudTimestamp(),
    )

    private fun CloudLikedSongDto.toSongEntity() = SongEntity(
        videoId = videoId,
        title = title ?: "",
        artistName = artistName?.split(", ")?.takeIf { it.isNotEmpty() },
        artistId = artistId?.let { listOf(it) },
        albumName = albumName,
        albumId = albumId,
        duration = "${(duration ?: 0) / 60}:${((duration ?: 0) % 60).toString().padStart(2, '0')}",
        durationSeconds = duration ?: 0,
        isAvailable = true,
        isExplicit = false,
        likeStatus = "LIKE",
        thumbnails = thumbnailUrl,
        videoType = "MUSIC_VIDEO_TYPE_ATV",
        category = null,
        resultType = null,
        liked = true,
        favoriteAt = favoriteAt.toLocalDateTimeOrNull() ?: now(),
    )

    private fun CloudFollowedArtistDto.toArtistEntity() = ArtistEntity(
        channelId = channelId,
        name = name ?: "",
        thumbnails = thumbnailUrl,
        followed = true,
        followedAt = followedAt.toLocalDateTimeOrNull() ?: now(),
    )

    private fun CloudSavedAlbumDto.toAlbumEntity() = AlbumEntity(
        browseId = browseId,
        title = title ?: "",
        artistName = artistName?.split(", ")?.takeIf { it.isNotEmpty() },
        artistId = artistId?.let { listOf(it) },
        audioPlaylistId = "",
        description = "",
        duration = null,
        durationSeconds = 0,
        thumbnails = thumbnailUrl,
        trackCount = trackCount ?: 0,
        type = "Album",
        year = null,
        liked = true,
        favoriteAt = favoriteAt.toLocalDateTimeOrNull() ?: now(),
    )
}
