package com.tan.data.repository

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
) : UserDataSyncRepository {

    companion object {
        private const val TABLE_LIKED_SONGS = "user_liked_songs"
        private const val TABLE_FOLLOWED_ARTISTS = "user_followed_artists"
        private const val TABLE_SAVED_ALBUMS = "user_saved_albums"
        private const val TABLE_PLAY_HISTORY = "user_play_history"
        private const val TABLE_QUEUE = "user_queue"
        private const val TABLE_SETTINGS = "user_settings"
        private const val BATCH_SIZE = 100
        private const val PRUNE_CHUNK = 50
        private const val KEY_SYNC_DOWN_DONE = "sync_down_done_"
    }

    /**
     * Boleh menghapus baris cloud yang sudah tidak ada di lokal HANYA kalau syncDown untuk
     * user ini pernah berhasil di instalasi ini.
     *
     * Tanpa penjaga ini, instalasi baru yang login ke akun lama (DB lokal masih kosong,
     * syncUp jalan lebih dulu daripada syncDown di performLoginSync) akan menganggap
     * seluruh isi cloud "sudah dihapus user" dan memusnahkan seluruh pustaka.
     */
    private suspend fun canPrune(userId: String): Boolean =
        dataStoreManager.getString(KEY_SYNC_DOWN_DONE + userId).first() == "true"

    /**
     * Hapus baris milik [userId] di [table] yang key-nya tidak ada lagi di [localKeys].
     * Ini yang membuat unlike / unfollow ikut terkirim — tanpa ini baris cloud jadi zombie
     * dan sync berikutnya menghidupkan lagi lagu yang sudah dibatalkan like-nya.
     */
    private suspend fun pruneRemoved(
        table: String,
        userId: String,
        keyColumn: String,
        localKeys: Set<String>,
        cloudKeys: suspend () -> List<String>,
    ) {
        if (!canPrune(userId)) return
        val stale = cloudKeys().toSet() - localKeys
        if (stale.isEmpty()) return
        stale.chunked(PRUNE_CHUNK).forEach { batch ->
            supabase.postgrest[table].delete {
                filter {
                    eq("user_id", userId)
                    isIn(keyColumn, batch)
                }
            }
        }
        Logger.d("UserDataSync", "Pruned ${stale.size} removed row(s) from $table for $userId")
    }

    private fun String?.toLocalDateTimeOrNull(): LocalDateTime? =
        this?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    override suspend fun syncUp(userId: String): Flow<Result<SyncReport>> = flow {
        try {
            val liked = syncUpLikedSongs(userId)
            val artists = syncUpFollowedArtists(userId)
            val albums = syncUpSavedAlbums(userId)
            val historyResult = syncUpPlayHistory(userId).getOrDefault(0)
            val queueResult = syncUpQueue(userId).getOrDefault(0)
            val settingsResult = syncUpSettings(userId).isSuccess

            // Lagu disukai / artis diikuti / album disimpan adalah data PUSTAKA user.
            // Kalau salah satu gagal di-push, syncUp TIDAK boleh dianggap sukses: pemanggil
            // (performLoginSync, logout) memakai hasil ini untuk memutuskan boleh-tidaknya
            // menghapus DB lokal. Melaporkan sukses palsu = data user hilang permanen.
            val libraryFailure = listOf(liked, artists, albums).firstNotNullOfOrNull { it.exceptionOrNull() }
            if (libraryFailure != null) {
                Logger.e("UserDataSync", "syncUp failed for $userId: ${libraryFailure.message}")
                emit(Result.failure(libraryFailure))
                return@flow
            }

            emit(
                Result.success(
                    SyncReport(
                        likedSongs = liked.getOrDefault(0),
                        artists = artists.getOrDefault(0),
                        albums = albums.getOrDefault(0),
                        history = historyResult,
                        queue = queueResult,
                        settingsSynced = settingsResult,
                    )
                )
            )
        } catch (e: Exception) {
            Logger.e("UserDataSync", "syncUp failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun syncDown(userId: String): Flow<Result<SyncReport>> = flow {
        try {
            val liked = syncDownLikedSongs(userId)
            val artists = syncDownFollowedArtists(userId)
            val albums = syncDownSavedAlbums(userId)
            val historyResult = syncDownPlayHistory(userId).getOrDefault(0)
            val queueResult = syncDownQueue(userId).getOrDefault(0)
            val settingsResult = syncDownSettings(userId).isSuccess

            val libraryFailure = listOf(liked, artists, albums).firstNotNullOfOrNull { it.exceptionOrNull() }
            if (libraryFailure != null) {
                Logger.e("UserDataSync", "syncDown failed for $userId: ${libraryFailure.message}")
                emit(Result.failure(libraryFailure))
                return@flow
            }
            // Sejak titik ini DB lokal sudah mencerminkan isi cloud untuk user ini, jadi
            // syncUp berikutnya boleh menghapus baris cloud yang hilang dari lokal.
            dataStoreManager.putString(KEY_SYNC_DOWN_DONE + userId, "true")

            emit(
                Result.success(
                    SyncReport(
                        likedSongs = liked.getOrDefault(0),
                        artists = artists.getOrDefault(0),
                        albums = albums.getOrDefault(0),
                        history = historyResult,
                        queue = queueResult,
                        settingsSynced = settingsResult,
                    )
                )
            )
        } catch (e: Exception) {
            Logger.e("UserDataSync", "syncDown failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    // ===================== SYNC UP =====================

    override suspend fun syncUpLikedSongs(userId: String): Result<Int> = runCatching {
        var offset = 0
        var totalSynced = 0
        val localIds = mutableSetOf<String>()
        while (true) {
            val songs = localDataSource.getLikedSongs(BATCH_SIZE, offset)
            if (songs.isEmpty()) break

            val dtos = songs.map { it.toCloudLikedSongDto(userId) }
            supabase.postgrest[TABLE_LIKED_SONGS].upsert(dtos)
            localIds += songs.map { it.videoId }
            totalSynced += songs.size
            offset += BATCH_SIZE
        }
        pruneRemoved(
            table = TABLE_LIKED_SONGS,
            userId = userId,
            keyColumn = "video_id",
            localKeys = localIds,
            cloudKeys = {
                supabase.postgrest[TABLE_LIKED_SONGS]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<CloudLikedSongDto>()
                    .map { it.videoId }
            },
        )
        totalSynced
    }

    override suspend fun syncUpFollowedArtists(userId: String): Result<Int> = runCatching {
        var offset = 0
        var totalSynced = 0
        val localIds = mutableSetOf<String>()
        while (true) {
            val artists = localDataSource.getFollowedArtists(BATCH_SIZE, offset)
            if (artists.isEmpty()) break

            val dtos = artists.map { it.toCloudFollowedArtistDto(userId) }
            supabase.postgrest[TABLE_FOLLOWED_ARTISTS].upsert(dtos)
            localIds += artists.map { it.channelId }
            totalSynced += artists.size
            offset += BATCH_SIZE
        }
        pruneRemoved(
            table = TABLE_FOLLOWED_ARTISTS,
            userId = userId,
            keyColumn = "channel_id",
            localKeys = localIds,
            cloudKeys = {
                supabase.postgrest[TABLE_FOLLOWED_ARTISTS]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<CloudFollowedArtistDto>()
                    .map { it.channelId }
            },
        )
        totalSynced
    }

    override suspend fun syncUpSavedAlbums(userId: String): Result<Int> = runCatching {
        var offset = 0
        var totalSynced = 0
        val localIds = mutableSetOf<String>()
        while (true) {
            val albums = localDataSource.getLikedAlbums(BATCH_SIZE, offset)
            if (albums.isEmpty()) break

            val dtos = albums.map { it.toCloudSavedAlbumDto(userId) }
            supabase.postgrest[TABLE_SAVED_ALBUMS].upsert(dtos)
            localIds += albums.map { it.browseId }
            totalSynced += albums.size
            offset += BATCH_SIZE
        }
        pruneRemoved(
            table = TABLE_SAVED_ALBUMS,
            userId = userId,
            keyColumn = "browse_id",
            localKeys = localIds,
            cloudKeys = {
                supabase.postgrest[TABLE_SAVED_ALBUMS]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<CloudSavedAlbumDto>()
                    .map { it.browseId }
            },
        )
        totalSynced
    }

    override suspend fun syncUpPlayHistory(userId: String): Result<Int> = runCatching {
        // Sync most played songs as play history (top 200)
        val songs = localDataSource.getLikedSongs(200, 0)
        val historyDtos = songs.filter { it.totalPlayTime > 0 }.map { song ->
            CloudPlayHistoryDto(
                id = "${userId}_${song.videoId}",
                userId = userId,
                videoId = song.videoId,
                title = song.title,
                artistName = song.artistName?.joinToString(", "),
                duration = song.durationSeconds,
                thumbnailUrl = song.thumbnails,
            )
        }
        if (historyDtos.isNotEmpty()) {
            supabase.postgrest[TABLE_PLAY_HISTORY].upsert(historyDtos)
        }
        historyDtos.size
    }

    override suspend fun syncUpQueue(userId: String): Result<Int> = runCatching {
        // Clear existing cloud queue for this user
        supabase.postgrest[TABLE_QUEUE].delete {
            filter { eq("user_id", userId) }
        }
        val queueEntities = localDataSource.getQueue()
        if (queueEntities.isEmpty()) return@runCatching 0

        val queue = queueEntities.first()
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
        if (dtos.isNotEmpty()) {
            dtos.chunked(BATCH_SIZE).forEach { batch ->
                supabase.postgrest[TABLE_QUEUE].insert(batch)
            }
        }
        dtos.size
    }

    override suspend fun syncUpSettings(userId: String): Result<Unit> = runCatching {
        val settingsMap = buildMap {
            put("quality", dataStoreManager.quality.first())
            put("downloadQuality", dataStoreManager.downloadQuality.first())
            put("normalizeVolume", dataStoreManager.normalizeVolume.first())
            put("skipSilent", dataStoreManager.skipSilent.first())
            put("saveStateOfPlayback", dataStoreManager.saveStateOfPlayback.first())
            put("shuffleKey", dataStoreManager.shuffleKey.first())
            put("repeatKey", dataStoreManager.repeatKey.first())
            put("language", dataStoreManager.language.first())
            put("lyricsProvider", dataStoreManager.getString("lyricsProvider").first())
            put("enableTranslateLyric", dataStoreManager.getString("enableTranslateLyric").first())
            put("translationLanguage", dataStoreManager.getString("translationLanguage").first())
            put("playerVolume", dataStoreManager.getString("playerVolume").first())
            put("playbackSpeed", dataStoreManager.getString("playbackSpeed").first())
            put("pitch", dataStoreManager.getString("pitch").first())
        }
        val json = Json.encodeToString(settingsMap)
        val dto = CloudUserSettingsDto(userId = userId, settingsJson = json)
        supabase.postgrest[TABLE_SETTINGS].upsert(dto)
    }

    // ===================== SYNC DOWN =====================

    override suspend fun syncDownLikedSongs(userId: String): Result<Int> = runCatching {
        val cloudSongs = supabase.postgrest[TABLE_LIKED_SONGS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudLikedSongDto>()

        var restored = 0
        for (dto in cloudSongs) {
            val existing = localDataSource.getSong(dto.videoId)
            if (existing == null) {
                localDataSource.insertSong(dto.toSongEntity())
            } else if (!existing.liked) {
                // Pakai tanggal like ASLI dari cloud, bukan now(), supaya urutan
                // "Recently added" di pustaka tidak teracak tiap kali ganti akun.
                localDataSource.updateLiked(1, dto.videoId, dto.favoriteAt.toLocalDateTimeOrNull() ?: now())
            }
            restored++
        }
        restored
    }

    override suspend fun syncDownFollowedArtists(userId: String): Result<Int> = runCatching {
        val cloudArtists = supabase.postgrest[TABLE_FOLLOWED_ARTISTS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudFollowedArtistDto>()

        var restored = 0
        for (dto in cloudArtists) {
            val existing = localDataSource.getArtist(dto.channelId)
            if (existing == null) {
                localDataSource.insertArtist(dto.toArtistEntity())
            } else if (!existing.followed) {
                localDataSource.updateFollowed(1, dto.channelId, dto.followedAt.toLocalDateTimeOrNull() ?: now())
            }
            restored++
        }
        restored
    }

    override suspend fun syncDownSavedAlbums(userId: String): Result<Int> = runCatching {
        val cloudAlbums = supabase.postgrest[TABLE_SAVED_ALBUMS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudSavedAlbumDto>()

        var restored = 0
        for (dto in cloudAlbums) {
            val existing = localDataSource.getAlbum(dto.browseId)
            if (existing == null) {
                localDataSource.insertAlbum(dto.toAlbumEntity())
            } else if (!existing.liked) {
                localDataSource.updateAlbumLiked(1, dto.browseId, dto.favoriteAt.toLocalDateTimeOrNull() ?: now())
            }
            restored++
        }
        restored
    }

    override suspend fun syncDownPlayHistory(userId: String): Result<Int> = runCatching {
        val cloudHistory = supabase.postgrest[TABLE_PLAY_HISTORY]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudPlayHistoryDto>()

        var restored = 0
        for (dto in cloudHistory) {
            val existing = localDataSource.getSong(dto.videoId)
            if (existing == null) {
                localDataSource.insertSong(
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
            restored++
        }
        restored
    }

    override suspend fun syncDownQueue(userId: String): Result<Int> = runCatching {
        val cloudQueue = supabase.postgrest[TABLE_QUEUE]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudQueueItemDto>()
        // Queue restore is complex (needs Track objects with full data)
        // For now just return the count - queue will rebuild from play state
        cloudQueue.size
    }

    override suspend fun syncDownSettings(userId: String): Result<Unit> = runCatching {
        val result = supabase.postgrest[TABLE_SETTINGS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<CloudUserSettingsDto>()

        if (result.isNotEmpty()) {
            val settingsJson = result.first().settingsJson
            val settingsMap: Map<String, String?> = Json.decodeFromString(settingsJson)
            settingsMap.forEach { (key, value) ->
                if (value != null) {
                    dataStoreManager.putString(key, value)
                }
            }
        }
    }

    // ===================== CLEAR =====================

    override suspend fun clearCloudData(userId: String): Result<Unit> = runCatching {
        supabase.postgrest[TABLE_LIKED_SONGS].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_FOLLOWED_ARTISTS].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_SAVED_ALBUMS].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_PLAY_HISTORY].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_QUEUE].delete { filter { eq("user_id", userId) } }
        supabase.postgrest[TABLE_SETTINGS].delete { filter { eq("user_id", userId) } }
    }

    override suspend fun clearLocalUserData(): Result<Unit> = runCatching {
        localDataSource.clearUserData()
        dataStoreManager.clearPerUserData()
    }

    override suspend fun clearLocalDatabase(): Result<Unit> = runCatching {
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
        favoriteAt = favoriteAt?.toString(),
    )

    private fun ArtistEntity.toCloudFollowedArtistDto(userId: String) = CloudFollowedArtistDto(
        id = "${userId}_$channelId",
        userId = userId,
        channelId = channelId,
        name = name,
        thumbnailUrl = thumbnails,
        followedAt = followedAt?.toString(),
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
        favoriteAt = favoriteAt?.toString(),
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
