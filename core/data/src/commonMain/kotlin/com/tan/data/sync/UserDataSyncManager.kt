package com.tan.data.sync

import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.UserDataSyncRepository
import com.tan.logger.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import com.tan.domain.repository.SocialRepository

class UserDataSyncManager(
    private val userDataSyncRepository: UserDataSyncRepository,
    private val socialRepository: SocialRepository,
    private val supabase: SupabaseClient,
    private val dataStoreManager: DataStoreManager,
    private val scope: CoroutineScope,
) {
    private var syncJob: Job? = null
    private val syncMutex = Mutex()

    companion object {
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val TAG = "UserDataSyncManager"
        private const val KEY_LAST_SYNCED_USER_ID = "last_synced_user_id"

        /**
         * Batas atas untuk sync saat login. Layar login MENUNGGU fungsi ini selesai sebelum
         * pindah ke MainScreen, dan satu login sync bisa puluhan request berantai. Kalau
         * jaringan buruk (tiap request bisa sampai 10 detik sebelum timeout), tanpa batas ini
         * layar login terlihat menggantung berlama-lama. Lewat batas: lanjut saja, sync
         * berkala yang menyusul.
         */
        private const val LOGIN_SYNC_TIMEOUT_MS = 60_000L
    }

    fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                performSyncUp()
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun syncNow() {
        scope.launch { performSyncUp() }
    }

    /**
     * Push data lokal ke cloud.
     *
     * @param force bila true, TUNGGU sync lain yang sedang jalan selesai alih-alih menyerah.
     *   Wajib true untuk backup sebelum logout / ganti akun: melewatkan backup di situ
     *   berarti data lokal dihapus tanpa pernah tersimpan di cloud.
     * @return true hanya bila push benar-benar berhasil sampai ke cloud.
     */
    suspend fun performSyncUp(force: Boolean = false): Boolean {
        if (!force && syncMutex.isLocked) return false
        return syncMutex.withLock {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@withLock false
            try {
                val result = userDataSyncRepository.syncUp(userId).first()
                if (result.isSuccess) {
                    Logger.d(TAG, "Sync up completed for user: $userId")
                    true
                } else {
                    Logger.e(TAG, "Sync up failed for $userId: ${result.exceptionOrNull()?.message}")
                    false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Sync up failed: ${e.message}")
                false
            }
        }
    }

    suspend fun performSyncDown() {
        if (syncMutex.isLocked) return
        syncMutex.withLock {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return
            try {
                userDataSyncRepository.syncDown(userId).first()
                socialRepository.syncDownPlaylists(userId).first()
                socialRepository.cleanupDuplicatePlaylists(userId)
                Logger.d(TAG, "Sync down completed for user: $userId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Sync down failed: ${e.message}")
            }
        }
    }

    /**
     * Sync to run right after a successful login/sign-up.
     *
     * Guarantees per-account data isolation: if the account that just logged in is
     * different from the last account that synced on this device (or this is the first
     * login), the previous account's local database (liked songs, followed artists,
     * saved albums, queue, YouTube playlists) is wiped BEFORE pulling the new account's
     * data — so leftover data from another account can never bleed in.
     *
     * If the same account logs in again, no wipe happens (a plain sync-down/merge),
     * preserving any local changes not yet pushed to the cloud.
     *
     * The wipe is ALWAYS conditional on the outgoing account's backup having verifiably
     * reached the cloud. If the backup fails (no network, RLS/permission error on the
     * sync tables, …) the local database is left untouched and the last-synced pointer
     * is NOT advanced, so the next login retries the backup. Showing two accounts' data
     * merged for a while is recoverable; wiping an un-backed-up library is not.
     *
     * Note: only the DB is cleared here, not the DataStore — the login flow already
     * calls [DataStoreManager.clearPerUserData] and repopulates it with the new
     * account's info before this runs.
     */
    suspend fun performLoginSync() {
        val finished = withTimeoutOrNull(LOGIN_SYNC_TIMEOUT_MS) {
            syncMutex.withLock {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@withLock
                val lastUserId = dataStoreManager.getString(KEY_LAST_SYNCED_USER_ID).firstOrNull()
                val isFirstLoginOnDevice = lastUserId.isNullOrEmpty()
                val isDifferentAccount = !lastUserId.isNullOrEmpty() && lastUserId != userId
                var backupFailed = false
                try {
                    if (isFirstLoginOnDevice) {
                        // Login PERTAMA di perangkat ini: data lokal (lagu disukai, artis diikuti,
                        // dll) yang dibuat sebelum login belum punya pemilik di cloud. Data itu
                        // MILIK user yang baru login — jadi PUSH dulu ke cloud, JANGAN dihapus.
                        // Kalau dihapus (perilaku lama), data pra-login hilang permanen karena
                        // belum pernah ke-backup. Ini penyebab "artis/lagu hilang saat login".
                        val pushed = userDataSyncRepository.syncUp(userId).first()
                        backupFailed = pushed.isFailure
                        Logger.d(TAG, "First login on device: local data pushed up for $userId (ok=${pushed.isSuccess}, no wipe)")
                    } else if (isDifferentAccount) {
                        // Ganti ke akun berbeda: data lokal saat ini MILIK akun lama (lastUserId).
                        // BACKUP dulu ke cloud SEBELUM di-wipe — kalau tidak, artis diikuti / lagu
                        // disukai akun lama HILANG permanen begitu dihapus (belum tentu sudah tersync
                        // lewat periodic/logout, apalagi kalau ganti akun langsung via Google).
                        // Policy tabel sync permissif (USING true) → sesi akun baru boleh menulis
                        // baris dengan user_id akun lama.
                        val old = lastUserId!!
                        val backup = userDataSyncRepository.syncUp(old).first()
                        if (backup.isSuccess) {
                            Logger.d(TAG, "Backed up outgoing account $old to cloud before wipe")
                            userDataSyncRepository.clearLocalDatabase()
                            Logger.d(TAG, "Account switch detected ($old -> $userId), local DB cleared")
                        } else {
                            // JANGAN wipe. Backup gagal → pustaka akun lama cuma ada di lokal.
                            // Menghapusnya di sini persis skenario "artis & lagu hilang saat ganti akun".
                            backupFailed = true
                            Logger.e(
                                TAG,
                                "Backup of outgoing account $old FAILED (${backup.exceptionOrNull()?.message}) " +
                                    "— skipping local wipe to avoid permanent data loss",
                            )
                        }
                    }
                    userDataSyncRepository.syncDown(userId).first()
                    socialRepository.syncDownPlaylists(userId).first()
                    socialRepository.cleanupDuplicatePlaylists(userId)
                    if (!backupFailed) {
                        // Pointer hanya maju kalau data akun sebelumnya sudah aman di cloud,
                        // supaya login berikutnya mencoba backup itu lagi.
                        dataStoreManager.putString(KEY_LAST_SYNCED_USER_ID, userId)
                    }
                    Logger.d(TAG, "Login sync completed for user: $userId (firstLogin=$isFirstLoginOnDevice, diffAccount=$isDifferentAccount, backupFailed=$backupFailed)")
                } catch (e: CancellationException) {
                    // Timeout / pembatalan harus lolos, jangan ditelan — kalau ditelan,
                    // withTimeoutOrNull tidak pernah tahu blok ini sudah dibatalkan.
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Login sync failed: ${e.message}")
                }
            }
        }
        if (finished == null) {
            Logger.e(TAG, "Login sync timed out after ${LOGIN_SYNC_TIMEOUT_MS}ms — melanjutkan, sync berkala akan mengulang")
        }
        // Data akun ini sudah siap — jalankan sync berkala supaya perubahan berikutnya
        // (like lagu, follow artis) ikut ter-backup tanpa menunggu logout.
        startPeriodicSync()
    }

    suspend fun performAccountSwitch() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncMutex.withLock {
            // Sama seperti performLoginSync: data lokal milik akun sebelumnya, jadi harus
            // aman di cloud dulu sebelum boleh dihapus.
            val lastUserId = dataStoreManager.getString(KEY_LAST_SYNCED_USER_ID).firstOrNull()
            val safeToWipe = if (!lastUserId.isNullOrEmpty() && lastUserId != userId) {
                userDataSyncRepository.syncUp(lastUserId).first().isSuccess
            } else {
                true
            }
            if (safeToWipe) {
                userDataSyncRepository.clearLocalUserData()
            } else {
                Logger.e(TAG, "Skipping wipe on account switch: backup of $lastUserId failed")
            }
            userDataSyncRepository.syncDown(userId).first()
            socialRepository.syncDownPlaylists(userId).first()
            if (safeToWipe) {
                dataStoreManager.putString(KEY_LAST_SYNCED_USER_ID, userId)
            }
            Logger.d(TAG, "Account switch sync completed for user: $userId (wiped=$safeToWipe)")
        }
    }

    suspend fun resetData() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncMutex.withLock {
            userDataSyncRepository.clearCloudData(userId)
            userDataSyncRepository.clearLocalUserData()
            Logger.d(TAG, "Data reset completed for user: $userId")
        }
    }
}
