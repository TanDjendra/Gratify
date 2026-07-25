package com.tan.data.repository

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
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal class SocialRepositoryImpl(
    private val supabase: SupabaseClient,
    private val localDataSource: LocalDataSource
) : SocialRepository {

    override suspend fun syncUpPlaylist(localPlaylistId: Long, userId: String, isPublic: Boolean?): Flow<Result<String>> = flow {
        try {
            val localPlaylist = localDataSource.getLocalPlaylist(localPlaylistId)
                ?: throw IllegalArgumentException("Playlist lokal tidak ditemukan.")

            // 1. Cek apakah cloud_playlists sudah punya entri untuk (user_id, local_playlist_id)
            val existingList = try {
                supabase.postgrest["cloud_playlists"]
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("local_playlist_id", localPlaylistId)
                        }
                    }.decodeList<CloudPlaylistDto>()
            } catch (e: Exception) {
                emptyList()
            }

            // Ambil SEMUA lagu di dalam playlist lokal terlebih dahulu untuk menentukan thumbnail efektif
            val pairs = localDataSource.getAllPlaylistPairSongByPosition(localPlaylistId) ?: emptyList()
            val songs = if (pairs.isNotEmpty()) {
                localDataSource.getSongByListVideoIdFull(pairs.map { it.songId })
            } else {
                emptyList()
            }
            val songMap = songs.associateBy { it.videoId }

            val effectiveThumbnail = if (!localPlaylist.thumbnail.isNullOrBlank()) {
                localPlaylist.thumbnail
            } else {
                songs.firstOrNull()?.thumbnails
            }

            if (localPlaylist.thumbnail.isNullOrBlank() && !effectiveThumbnail.isNullOrBlank()) {
                try {
                    localDataSource.updateLocalPlaylistThumbnail(thumbnail = effectiveThumbnail, id = localPlaylistId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val effectiveIsPublic = if (existingList.isNotEmpty()) {
                isPublic ?: existingList.first().isPublic
            } else {
                isPublic ?: false
            }

            val cloudPlaylistId: String
            if (existingList.isNotEmpty()) {
                val existing = existingList.first()
                cloudPlaylistId = existing.id ?: throw IllegalStateException("Cloud ID null")

                // Bersihkan duplikat yang berlebih jika sebelumnya sempat ter-duplicate
                if (existingList.size > 1) {
                    val duplicateIds = existingList.drop(1).mapNotNull { it.id }
                    for (dupId in duplicateIds) {
                        try {
                            supabase.postgrest["cloud_playlist_items"].delete { filter { eq("playlist_id", dupId) } }
                            supabase.postgrest["cloud_playlists"].delete { filter { eq("id", dupId) } }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Update data playlist cloud yang sudah ada
                val updateDto = CloudPlaylistDto(
                    id = cloudPlaylistId,
                    userId = userId,
                    localPlaylistId = localPlaylistId,
                    title = localPlaylist.title,
                    thumbnailUrl = effectiveThumbnail,
                    isPublic = effectiveIsPublic
                )
                supabase.postgrest["cloud_playlists"].update(updateDto) {
                    filter { eq("id", cloudPlaylistId) }
                }

                // Hapus item lagu lama di cloud_playlist_items sebelum insert yang baru
                try {
                    supabase.postgrest["cloud_playlist_items"].delete {
                        filter { eq("playlist_id", cloudPlaylistId) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // Buat baru jika belum ada
                val cloudPlaylistDto = CloudPlaylistDto(
                    userId = userId,
                    localPlaylistId = localPlaylistId,
                    title = localPlaylist.title,
                    thumbnailUrl = effectiveThumbnail,
                    isPublic = effectiveIsPublic
                )
                val insertedPlaylist = supabase.postgrest["cloud_playlists"]
                    .insert(cloudPlaylistDto) {
                        select()
                    }.decodeSingle<CloudPlaylistDto>()
                cloudPlaylistId = insertedPlaylist.id
                    ?: throw IllegalStateException("Gagal mendapatkan ID playlist cloud.")
            }

            if (pairs.isNotEmpty()) {
                val cloudItems = pairs.mapIndexedNotNull { index, pair ->
                    val song = songMap[pair.songId] ?: return@mapIndexedNotNull null
                    CloudPlaylistItemDto(
                        playlistId = cloudPlaylistId,
                        videoId = song.videoId,
                        title = song.title,
                        artist = song.artistName?.firstOrNull() ?: "Unknown Artist",
                        duration = song.durationSeconds,
                        thumbnailUrl = song.thumbnails,
                        position = index
                    )
                }

                if (cloudItems.isNotEmpty()) {
                    supabase.postgrest["cloud_playlist_items"].insert(cloudItems)
                }
            }

            // Jika playlist ini publik, sinkronkan otomatis juga ke shared_playlists agar perubahan langsung ter-update di server
            if (effectiveIsPublic) {
                try {
                    val sharedList = supabase.postgrest["shared_playlists"]
                        .select { filter { eq("user_id", userId) } }
                        .decodeList<com.tan.domain.data.entities.SharedPlaylist>()
                    val existingShared = sharedList.firstOrNull { it.title.trim().equals(localPlaylist.title.trim(), ignoreCase = true) }
                    if (existingShared != null && existingShared.id != null) {
                        val sharedId = existingShared.id!!
                        supabase.postgrest["shared_playlists"].update(existingShared.copy(title = localPlaylist.title, thumbnailUrl = effectiveThumbnail)) {
                            filter { eq("id", sharedId) }
                        }
                        supabase.postgrest["shared_playlist_tracks"].delete { filter { eq("playlist_id", sharedId) } }
                        if (pairs.isNotEmpty()) {
                            val sharedTracks = pairs.mapNotNull { pair ->
                                val song = songMap[pair.songId] ?: return@mapNotNull null
                                com.tan.domain.data.entities.SharedPlaylistTrack(
                                    playlistId = sharedId,
                                    videoId = song.videoId,
                                    title = song.title,
                                    artists = song.artistName?.joinToString(", ") ?: "Unknown Artist",
                                    durationSeconds = song.durationSeconds
                                )
                            }
                            if (sharedTracks.isNotEmpty()) {
                                supabase.postgrest["shared_playlist_tracks"].insert(sharedTracks)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            emit(Result.success(cloudPlaylistId))
        } catch (e: Exception) {
            Logger.e("SocialRepositoryImpl", "syncUpPlaylist error: ${e.message}")
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun unshareOrHideCloudPlaylist(localPlaylistId: Long, userId: String): Flow<Result<Unit>> = flow {
        try {
            val existingList = supabase.postgrest["cloud_playlists"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("local_playlist_id", localPlaylistId)
                    }
                }.decodeList<CloudPlaylistDto>()

            for (existing in existingList) {
                val id = existing.id ?: continue
                supabase.postgrest["cloud_playlists"].update(existing.copy(isPublic = false)) {
                    filter { eq("id", id) }
                }
                try {
                    val sharedList = supabase.postgrest["shared_playlists"]
                        .select { filter { eq("user_id", userId) } }
                        .decodeList<com.tan.domain.data.entities.SharedPlaylist>()
                    val existingShared = sharedList.firstOrNull { it.title.trim().equals(existing.title.trim(), ignoreCase = true) }
                    if (existingShared != null && existingShared.id != null) {
                        val sharedId = existingShared.id!!
                        supabase.postgrest["shared_playlist_tracks"].delete { filter { eq("playlist_id", sharedId) } }
                        supabase.postgrest["shared_playlists"].delete { filter { eq("id", sharedId) } }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            Logger.e("SocialRepositoryImpl", "unshareOrHideCloudPlaylist error: ${e.message}")
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteCloudPlaylist(localPlaylistId: Long, userId: String, title: String?): Flow<Result<Unit>> = flow {
        try {
            val allUserCloudPlaylists = supabase.postgrest["cloud_playlists"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<CloudPlaylistDto>()

            val existingList = allUserCloudPlaylists.filter {
                it.localPlaylistId == localPlaylistId || (title != null && it.title.trim().equals(title.trim(), ignoreCase = true))
            }

            for (existing in existingList) {
                val id = existing.id ?: continue
                // cloud_playlist_items di-cascade delete otomatis via FK
                supabase.postgrest["cloud_playlists"].delete {
                    filter { eq("id", id) }
                }

                // Hapus shared_playlists milik owner (title bisa punya suffix "|||localId")
                try {
                    val sharedList = supabase.postgrest["shared_playlists"]
                        .select { filter { eq("user_id", userId) } }
                        .decodeList<com.tan.domain.data.entities.SharedPlaylist>()
                    val matchingShared = sharedList.filter {
                        it.title.trim().equals(existing.title.trim(), ignoreCase = true) ||
                            it.title.substringBeforeLast("|||").trim().equals(existing.title.trim(), ignoreCase = true)
                    }
                    for (shared in matchingShared) {
                        val sharedId = shared.id ?: continue
                        supabase.postgrest["shared_playlist_tracks"].delete { filter { eq("playlist_id", sharedId) } }
                        supabase.postgrest["shared_playlists"].delete { filter { eq("id", sharedId) } }
                    }
                } catch (e: Exception) {
                    Logger.e("SocialRepositoryImpl", "Failed to delete shared_playlists: ${e.message}")
                    e.printStackTrace()
                }

                // Hapus salinan di cloud_playlists milik user lain
                // Match: title exact + thumbnail sama = pasti salinan dari playlist ini
                try {
                    val copies = supabase.postgrest["cloud_playlists"]
                        .select {
                            filter {
                                neq("user_id", userId)
                                eq("title", existing.title)
                            }
                        }.decodeList<CloudPlaylistDto>()
                        .filter { it.thumbnailUrl == existing.thumbnailUrl }

                    for (copy in copies) {
                        val copyId = copy.id ?: continue
                        supabase.postgrest["cloud_playlists"].delete {
                            filter { eq("id", copyId) }
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("SocialRepositoryImpl", "Failed to remove copies: ${e.message}")
                    e.printStackTrace()
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            Logger.e("SocialRepositoryImpl", "deleteCloudPlaylist error: ${e.message}")
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun syncDownPlaylists(userId: String): Flow<Result<Int>> = flow {
        try {
            Logger.d("SocialRepositoryImpl", "Memulai sinkronisasi playlist untuk user: $userId")
            val cloudPlaylists = supabase.postgrest["cloud_playlists"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<CloudPlaylistDto>()

            var restoreCount = 0
            // Snapshot playlist lokal milik user aktif untuk mencegah DUPLIKASI.
            // syncDown hanya boleh me-restore playlist yang belum ada di lokal (mis. device baru).
            // Tanpa ini, setiap login ulang / ganti akun membuat salinan baru dari playlist
            // publik milik sendiri (sering muncul sebagai duplikat kosong).
            val existingLocals = (localDataSource.getAllLocalPlaylists(1000, 0) ?: emptyList()).toMutableList()
            for (cloudPlaylist in cloudPlaylists) {
                val cloudId = cloudPlaylist.id ?: continue

                // Lewati kalau playlist ini sudah ada di lokal (cocok via id mapping atau judul).
                val alreadyLocal = existingLocals.firstOrNull { local ->
                    (cloudPlaylist.localPlaylistId != null && local.id == cloudPlaylist.localPlaylistId) ||
                        local.title.trim().equals(cloudPlaylist.title.trim(), ignoreCase = true)
                }
                if (alreadyLocal != null) {
                    // Pastikan mapping local_playlist_id di cloud tetap menunjuk ke playlist yang ada.
                    if (cloudPlaylist.localPlaylistId != alreadyLocal.id) {
                        try {
                            supabase.postgrest["cloud_playlists"].update(
                                cloudPlaylist.copy(localPlaylistId = alreadyLocal.id)
                            ) { filter { eq("id", cloudId) } }
                        } catch (e: Exception) {
                            Logger.e("SocialRepositoryImpl", "Gagal update mapping saat dedup: ${e.message}")
                        }
                    }
                    continue
                }

                // Unduh item lagu untuk playlist ini
                val cloudItems = supabase.postgrest["cloud_playlist_items"]
                    .select {
                        filter {
                            eq("playlist_id", cloudId)
                        }
                    }
                    .decodeList<CloudPlaylistItemDto>()

                val trackVideoIds = cloudItems.sortedBy { it.position }.map { it.videoId }

                val localPlaylistEntity = LocalPlaylistEntity(
                    title = cloudPlaylist.title,
                    thumbnail = cloudPlaylist.thumbnailUrl,
                    tracks = trackVideoIds
                )

                withContext(Dispatchers.IO) {
                    localDataSource.insertLocalPlaylist(localPlaylistEntity)
                }

                // Ambil ID lokal dari playlist yang baru saja di-insert
                val allPlaylists = localDataSource.getAllLocalPlaylists(100, 0) ?: emptyList()
                val targetLocalPlaylist = allPlaylists.maxByOrNull { it.id }

                if (targetLocalPlaylist != null) {
                    // Daftarkan agar cloud playlist berikutnya tidak menduplikasi playlist ini.
                    existingLocals.add(targetLocalPlaylist)
                    // Update local_playlist_id di cloud agar mapping tetap sinkron setelah re-insert
                    if (targetLocalPlaylist.id != cloudPlaylist.localPlaylistId) {
                        try {
                            supabase.postgrest["cloud_playlists"].update(
                                CloudPlaylistDto(
                                    id = cloudId,
                                    userId = userId,
                                    localPlaylistId = targetLocalPlaylist.id,
                                    title = cloudPlaylist.title,
                                    thumbnailUrl = cloudPlaylist.thumbnailUrl,
                                    isPublic = cloudPlaylist.isPublic
                                )
                            ) {
                                filter { eq("id", cloudId) }
                            }
                        } catch (e: Exception) {
                            Logger.e("SocialRepositoryImpl", "Failed to update local_playlist_id mapping: ${e.message}")
                        }
                    }

                    cloudItems.forEach { item ->
                        withContext(Dispatchers.IO) {
                            // Cek apakah song sudah ada di DB lokal
                            val existingSong = localDataSource.getSong(item.videoId)
                            if (existingSong == null) {
                                val dummySong = SongEntity(
                                    videoId = item.videoId,
                                    title = item.title,
                                    artistName = listOf(item.artist),
                                    duration = "${item.duration ?: 0}s",
                                    durationSeconds = item.duration ?: 0,
                                    thumbnails = item.thumbnailUrl,
                                    isAvailable = true,
                                    isExplicit = false,
                                    likeStatus = "INDIFFERENT",
                                    videoType = "MUSIC_VIDEO_TYPE_ATV",
                                    category = null,
                                    resultType = null
                                )
                                localDataSource.insertSong(dummySong)
                            }

                            localDataSource.insertPairSongLocalPlaylist(
                                PairSongLocalPlaylist(
                                    playlistId = targetLocalPlaylist.id,
                                    songId = item.videoId,
                                    position = item.position,
                                    inPlaylist = now()
                                )
                            )
                        }
                    }
                    restoreCount++
                }
            }

            Logger.i("SocialRepositoryImpl", "Berhasil memulihkan $restoreCount playlist dari cloud.")
            emit(Result.success(restoreCount))
        } catch (e: Exception) {
            Logger.e("SocialRepositoryImpl", "syncDownPlaylists error: ${e.message}")
            e.printStackTrace()
            emit(Result.failure(e))
        }
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
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getCloudPlaylistItems(playlistId: String): Flow<Result<List<CloudPlaylistItemDto>>> = flow {
        try {
            val items = supabase.postgrest["cloud_playlist_items"]
                .select {
                    filter {
                        eq("playlist_id", playlistId)
                    }
                }
                .decodeList<CloudPlaylistItemDto>()
            emit(Result.success(items.sortedBy { it.position }))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cleanupDuplicatePlaylists(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var removed = 0

            // ---------- LOKAL: hapus duplikat KOSONG yang punya kembaran berisi ----------
            val locals = localDataSource.getAllLocalPlaylists(1000, 0) ?: emptyList()
            val localByTitle = locals.groupBy { it.title.trim().lowercase() }
            for ((_, group) in localByTitle) {
                if (group.size < 2) continue
                val counted = group.map { pl ->
                    pl to (localDataSource.getAllPlaylistPairSongByPosition(pl.id)?.size ?: 0)
                }
                // Hanya bersihkan bila ada kembaran yang berisi (biar tidak menghapus playlist kosong yang sah).
                if (counted.none { it.second > 0 }) continue
                counted.filter { it.second == 0 }.forEach { (pl, _) ->
                    try {
                        localDataSource.deleteLocalPlaylist(pl.id)
                        removed++
                    } catch (e: Exception) {
                        Logger.e("SocialRepositoryImpl", "cleanup lokal gagal: ${e.message}")
                    }
                }
            }
            val survivingLocals = localDataSource.getAllLocalPlaylists(1000, 0) ?: emptyList()

            // ---------- SERVER: cloud_playlists ----------
            val cloudPlaylists = supabase.postgrest["cloud_playlists"]
                .select { filter { eq("user_id", userId) } }
                .decodeList<CloudPlaylistDto>()
            for ((_, group) in cloudPlaylists.groupBy { it.title.trim().lowercase() }) {
                if (group.size < 2) continue
                val counted = group.map { cp ->
                    val cnt = cp.id?.let { id ->
                        supabase.postgrest["cloud_playlist_items"]
                            .select { filter { eq("playlist_id", id) } }
                            .decodeList<CloudPlaylistItemDto>().size
                    } ?: 0
                    cp to cnt
                }
                val keeper = counted.maxByOrNull { it.second }?.first ?: continue
                counted.filter { it.first.id != keeper.id }.forEach { (dup, _) ->
                    val dupId = dup.id ?: return@forEach
                    try {
                        supabase.postgrest["cloud_playlist_items"].delete { filter { eq("playlist_id", dupId) } }
                        supabase.postgrest["cloud_playlists"].delete { filter { eq("id", dupId) } }
                        removed++
                    } catch (e: Exception) {
                        Logger.e("SocialRepositoryImpl", "cleanup cloud gagal: ${e.message}")
                    }
                }
                // Pastikan keeper menunjuk ke playlist lokal berjudul sama yang masih tersisa.
                val localMatch = survivingLocals.firstOrNull { it.title.trim().equals(keeper.title.trim(), ignoreCase = true) }
                if (localMatch != null && keeper.id != null && keeper.localPlaylistId != localMatch.id) {
                    try {
                        supabase.postgrest["cloud_playlists"].update(keeper.copy(localPlaylistId = localMatch.id)) {
                            filter { eq("id", keeper.id!!) }
                        }
                    } catch (_: Exception) {}
                }
            }

            // ---------- SERVER: shared_playlists (judul punya suffix "|||localId") ----------
            val sharedList = supabase.postgrest["shared_playlists"]
                .select { filter { eq("user_id", userId) } }
                .decodeList<com.tan.domain.data.entities.SharedPlaylist>()
            for ((_, group) in sharedList.groupBy { it.title.substringBeforeLast("|||").trim().lowercase() }) {
                if (group.size < 2) continue
                val counted = group.map { sp ->
                    val cnt = sp.id?.let { id ->
                        supabase.postgrest["shared_playlist_tracks"]
                            .select { filter { eq("playlist_id", id) } }
                            .decodeList<com.tan.domain.data.entities.SharedPlaylistTrack>().size
                    } ?: 0
                    sp to cnt
                }
                val keeper = counted.maxByOrNull { it.second }?.first ?: continue
                counted.filter { it.first.id != keeper.id }.forEach { (dup, _) ->
                    val dupId = dup.id ?: return@forEach
                    try {
                        supabase.postgrest["shared_playlist_tracks"].delete { filter { eq("playlist_id", dupId) } }
                        supabase.postgrest["shared_playlists"].delete { filter { eq("id", dupId) } }
                        removed++
                    } catch (e: Exception) {
                        Logger.e("SocialRepositoryImpl", "cleanup shared gagal: ${e.message}")
                    }
                }
            }

            Logger.i("SocialRepositoryImpl", "cleanupDuplicatePlaylists: $removed entri dihapus untuk user $userId")
            removed
        }.onFailure {
            Logger.e("SocialRepositoryImpl", "cleanupDuplicatePlaylists error: ${it.message}")
        }
    }
}
