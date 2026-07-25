package com.tan.domain.repository

import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.data.model.social.CloudPlaylistItemDto
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    /**
     * Mengunggah (Push) playlist lokal ke Supabase agar bisa diakses di cloud atau dibagikan ke publik.
     */
    suspend fun syncUpPlaylist(localPlaylistId: Long, userId: String, isPublic: Boolean? = null): Flow<Result<String>>

    /**
     * Menyembunyikan atau menghapus playlist dari publik di cloud_playlists.
     */
    suspend fun unshareOrHideCloudPlaylist(localPlaylistId: Long, userId: String): Flow<Result<Unit>>

    /**
     * Menghapus playlist sepenuhnya dari cloud_playlists dan cloud_playlist_items.
     */
    suspend fun deleteCloudPlaylist(localPlaylistId: Long, userId: String, title: String? = null): Flow<Result<Unit>>

    /**
     * Mengunduh (Pull) seluruh playlist cloud milik user saat login dan menyimpannya kembali ke Room Database lokal.
     * Mengembalikan jumlah playlist yang berhasil di-restore.
     */
    suspend fun syncDownPlaylists(userId: String): Flow<Result<Int>>

    /**
     * Mengambil daftar playlist publik milik pengguna lain (misal teman / mutualan).
     */
    suspend fun getUserPublicPlaylists(userId: String): Flow<Result<List<CloudPlaylistDto>>>

    /**
     * Mengambil daftar lagu dari playlist cloud tertentu.
     */
    suspend fun getCloudPlaylistItems(playlistId: String): Flow<Result<List<CloudPlaylistItemDto>>>

    /**
     * Membersihkan playlist duplikat milik [userId] — di lokal maupun di server.
     *
     * Aturan konservatif:
     * - Lokal: hapus playlist berjudul sama yang KOSONG (0 lagu) selama masih ada kembaran
     *   berjudul sama yang berisi. Kalau semua kosong, tidak menghapus apa pun.
     * - Server (cloud_playlists & shared_playlists): untuk judul yang sama milik user,
     *   simpan baris dengan lagu terbanyak dan hapus sisanya beserta item/track-nya.
     *
     * Mengembalikan jumlah entri (lokal + server) yang dihapus.
     */
    suspend fun cleanupDuplicatePlaylists(userId: String): Result<Int>
}
