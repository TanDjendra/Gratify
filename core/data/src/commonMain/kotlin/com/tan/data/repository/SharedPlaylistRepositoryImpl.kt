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
import kotlin.coroutines.cancellation.CancellationException

import com.tan.domain.data.model.social.CloudPlaylistDto

@Serializable
private data class SharedPlaylistSaveDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("user_id") val userId: String,
)

// Hanya untuk mengambil kolom playlist_id dari shared_playlist_tracks (cek playlist mana yang punya lagu).
@Serializable
private data class SharedPlaylistTrackPlaylistId(
    @SerialName("playlist_id") val playlistId: String,
)

class SharedPlaylistRepositoryImpl(
    private val supabase: SupabaseClient
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
            val existingList = try {
                supabase.postgrest["shared_playlists"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }.decodeList<SharedPlaylist>()
            } catch (e: Exception) {
                emptyList()
            }
            // Cari playlist yang cocok: prioritas yang punya suffix |||localPlaylistId,
            // fallback ke title matching jika data lama belum punya suffix
            val existing = existingList.firstOrNull { 
                it.title.endsWith("|||$localPlaylistId") 
            } ?: existingList.firstOrNull { 
                val displayTitle = if (it.title.contains("|||")) it.title.substringBeforeLast("|||") else it.title
                displayTitle.trim().equals(title.trim(), ignoreCase = true) 
            }

            val dbTitle = "$title|||$localPlaylistId"
            var playlistId: String

            if (existing != null && existing.id != null) {
                playlistId = existing.id!!
                try {
                    val playlist = SharedPlaylist(
                        id = playlistId,
                        userId = userId,
                        title = dbTitle,
                        thumbnailUrl = thumbnailUrl,
                        creatorName = creatorName ?: existing.creatorName,
                        createdAt = existing.createdAt
                    )
                    supabase.postgrest["shared_playlists"].update(playlist) {
                        filter { eq("id", playlistId) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    supabase.postgrest["shared_playlist_tracks"].delete {
                        filter { eq("playlist_id", playlistId) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val playlist = SharedPlaylist(
                    userId = userId,
                    title = dbTitle,
                    creatorName = creatorName,
                    thumbnailUrl = thumbnailUrl
                )
                val insertedPlaylist = try {
                    supabase.postgrest["shared_playlists"]
                        .insert(playlist) {
                            select()
                        }.decodeSingle<SharedPlaylist>()
                } catch (e: Exception) {
                    val fallbackPlaylist = playlist.copy(creatorName = null)
                    supabase.postgrest["shared_playlists"]
                        .insert(fallbackPlaylist) {
                            select()
                        }.decodeSingle<SharedPlaylist>()
                }
                playlistId = insertedPlaylist.id ?: throw Exception("Failed to get playlist ID")
            }

            if (tracks.isNotEmpty()) {
                val tracksWithId = tracks.map { it.copy(playlistId = playlistId) }
                try {
                    supabase.postgrest["shared_playlist_tracks"].insert(tracksWithId)
                } catch (trackErr: Exception) {
                    trackErr.printStackTrace()
                }
            }

            Result.success(playlistId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun getSharedPlaylists(): Flow<Result<List<SharedPlaylist>>> = flow {
        try {
            val playlists = supabase.postgrest["shared_playlists"]
                .select()
                .decodeList<SharedPlaylist>()

            // Buang playlist "dummy"/kosong: hanya tampilkan yang benar-benar punya lagu.
            // Ambil daftar playlist_id yang muncul di shared_playlist_tracks lalu filter.
            // Kalau langkah cek trek gagal (mis. error jaringan), jangan sembunyikan apa pun —
            // tampilkan semua apa adanya supaya fitur tetap jalan.
            val withTracks = try {
                val idsWithTracks = supabase.postgrest["shared_playlist_tracks"]
                    .select(Columns.list("playlist_id"))
                    .decodeList<SharedPlaylistTrackPlaylistId>()
                    .map { it.playlistId }
                    .toSet()
                playlists.filter { it.id != null && it.id in idsWithTracks }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                playlists
            }

            val cleaned = withTracks.map {
                if (it.title.contains("|||")) {
                    it.copy(title = it.title.substringBeforeLast("|||"))
                } else it
            }
            emit(Result.success(cleaned))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
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
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun getSharedPlaylistTracks(playlistId: String): Flow<Result<List<SharedPlaylistTrack>>> = flow {
        try {
            val tracks = supabase.postgrest["shared_playlist_tracks"]
                .select {
                    filter {
                        eq("playlist_id", playlistId)
                    }
                }
                .decodeList<SharedPlaylistTrack>()
            emit(Result.success(tracks))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    override suspend fun deleteSharedPlaylist(playlistId: String): Result<Unit> {
        return try {
            var targetTitle: String? = null
            var targetUserId: String? = null
            try {
                val sp = supabase.postgrest["shared_playlists"]
                    .select { filter { eq("id", playlistId) } }
                    .decodeSingleOrNull<SharedPlaylist>()
                if (sp != null) {
                    targetTitle = sp.title
                    targetUserId = sp.userId
                }
            } catch (e: Exception) { e.printStackTrace() }

            try {
                supabase.postgrest["shared_playlist_tracks"].delete {
                    filter { eq("playlist_id", playlistId) }
                }
                supabase.postgrest["shared_playlists"].delete {
                    filter { eq("id", playlistId) }
                }
            } catch (e: Exception) { e.printStackTrace() }

            try {
                val cloudPlaylists = supabase.postgrest["cloud_playlists"]
                    .select {
                        filter { eq("id", playlistId) }
                    }.decodeList<CloudPlaylistDto>()
                val cloudMatched = if (cloudPlaylists.isNotEmpty()) cloudPlaylists else {
                    if (targetUserId != null && targetTitle != null) {
                        supabase.postgrest["cloud_playlists"].select {
                            filter { eq("user_id", targetUserId!!) }
                        }.decodeList<CloudPlaylistDto>().filter { it.title.trim().equals(targetTitle!!.trim(), ignoreCase = true) }
                    } else emptyList()
                }
                for (cp in cloudMatched) {
                    val cId = cp.id ?: continue
                    supabase.postgrest["cloud_playlists"].update(cp.copy(isPublic = false)) {
                        filter { eq("id", cId) }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun deleteSharedPlaylistByLocalId(userId: String, localPlaylistId: Long, fallbackTitle: String): Result<Unit> {
        return try {
            val existingList = try {
                supabase.postgrest["shared_playlists"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }.decodeList<SharedPlaylist>()
            } catch (e: Exception) {
                emptyList()
            }
            
            val target = existingList.firstOrNull {
                it.title.endsWith("|||$localPlaylistId")
            } ?: existingList.firstOrNull {
                !it.title.contains("|||") && it.title.trim().equals(fallbackTitle.trim(), ignoreCase = true)
            }

            val targetId = target?.id
            if (targetId != null) {
                deleteSharedPlaylist(targetId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

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
            e.printStackTrace()
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
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
