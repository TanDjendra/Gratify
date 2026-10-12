package com.tan.data.sync

import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.repository.SocialRepository
import com.tan.domain.repository.UserDataSyncRepository
import com.tan.logger.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class UserDataSyncManager(
    private val userDataSyncRepository: UserDataSyncRepository,
    private val socialRepository: SocialRepository,
    private val supabase: SupabaseClient,
    private val dataStoreManager: DataStoreManager,
    private val scope: CoroutineScope,
    private val mediaPlayerHandler: MediaPlayerHandler,
    private val userRepository: com.tan.domain.repository.UserRepository,
) {
    private var syncJob: Job? = null
    private var restoreJob: Job? = null
    private val syncMutex = Mutex()
    private var activeOwner: String? = null
    private var cloudResumed = false

    companion object {
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L
        private const val TAG = "UserDataSyncManager"
        private const val KEY_LAST_SYNCED_USER_ID = "last_synced_user_id"
        private const val LOGIN_SYNC_TIMEOUT_MS = 60_000L
    }

    fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) { delay(SYNC_INTERVAL_MS); performSyncUp() }
        }
    }

    fun stopSync() {
        cloudResumed = false
        syncJob?.cancel(); syncJob = null
        restoreJob?.cancel(); restoreJob = null
    }
    fun syncNow() { scope.launch { performSyncUp() } }

    /** Offline access never imports expired credentials or activates a different library. */
    suspend fun resumeOfflineAccount(userId: String) = syncMutex.withLock {
        check(userId.isNotBlank())
        check(supabase.auth.sessionStatus.value is io.github.jan.supabase.auth.status.SessionStatus.RefreshFailure)
        check(supabase.auth.sessionManager.loadSession()?.user?.id == userId)
        check(dataStoreManager.getString(KEY_LAST_SYNCED_USER_ID).first() == userId)
        check(userDataSyncRepository.getLocalAccountOwner() == userId)
        stopSync()
        activeOwner = userId
        // Repair older builds' false flag only after all persisted ownership checks pass.
        if (dataStoreManager.loggedIn.first() != DataStoreManager.TRUE) dataStoreManager.setLoggedIn(true)
    }

    suspend fun resumeAuthenticatedAccount(userId: String) {
        check(supabase.auth.currentUserOrNull()?.id == userId)
        if (activeOwner != userId || userDataSyncRepository.getLocalAccountOwner() != userId) {
            performLoginSync(waitForCloud = false)
        } else if (!cloudResumed) {
            resumeCloudJobs()
        }
    }

    private fun resumeCloudJobs() {
        cloudResumed = true
        startPeriodicSync()
        restoreJob = scope.launch {
            try { performSyncDown() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Logger.w(TAG, "Background cloud restore unavailable") }
        }
    }

    /** A revoked/missing session hides local account data without deleting its archive. */
    suspend fun invalidateSessionAccess() = syncMutex.withLock {
        if (supabase.auth.sessionStatus.value !is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated) return@withLock
        stopSync()
        val previous = dataStoreManager.getString(KEY_LAST_SYNCED_USER_ID).firstOrNull()
        if (!previous.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.player.pause()
                mediaPlayerHandler.resetSongAndQueue()
            }
            userDataSyncRepository.activateLocalAccount("", previous)
            dataStoreManager.clearPerUserData()
        }
        activeOwner = null
        dataStoreManager.putString(KEY_LAST_SYNCED_USER_ID, "")
        dataStoreManager.setLoggedIn(false)
        dataStoreManager.setCookie("", null)
    }

    private fun requireActiveOwner(): String {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("No authenticated account")
        check(userId == activeOwner) { "Local account activation is required before sync" }
        return userId
    }

    suspend fun performSyncUp(force: Boolean = false): Boolean {
        if (!force && syncMutex.isLocked) return false
        return syncMutex.withLock {
            try {
                val userId = requireActiveOwner()
                userRepository.getUserProfile(userId).first()
                socialRepository.flushPlaylistRemovals(userId)
                userDataSyncRepository.syncUp(userId).first().isSuccess
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Logger.w(TAG, "Cloud backup failed; local account data retained"); false }
        }
    }

    suspend fun performSyncDown() {
        syncMutex.withLock { syncDown(requireActiveOwner()) }
    }

    private suspend fun syncDown(userId: String) {
        userRepository.getUserProfile(userId).first()
        // A failed library request must not prevent independent playlist recovery.
        restoreLibraryAndPlaylists(
            library = { userDataSyncRepository.syncDown(userId).first().getOrThrow(); Unit },
            playlists = { socialRepository.syncDownPlaylists(userId).first().getOrThrow(); Unit },
        )
    }

    /** Local ownership commits before any cloud request or navigation to account screens. */
    suspend fun performLoginSync(waitForCloud: Boolean = true) {
        stopSync()
        syncMutex.withLock {
            val userId = supabase.auth.currentUserOrNull()?.id ?: error("No authenticated account")
            val previous = dataStoreManager.getString(KEY_LAST_SYNCED_USER_ID).firstOrNull()
            previous?.takeIf { it.isNotBlank() }?.let { dataStoreManager.captureLegacyProfilePrivacy(it) }
            if (previous != null && previous != userId) withContext(Dispatchers.Main) {
                mediaPlayerHandler.player.pause()
                mediaPlayerHandler.resetSongAndQueue()
            }
            // Failure rolls back the entire archive/restore transaction; never continue with another owner's DB.
            userDataSyncRepository.activateLocalAccount(userId, previous)
            if (previous != userId) dataStoreManager.clearPerUserData()
            if (!previous.isNullOrBlank() && previous != userId) dataStoreManager.setCookie("", null)
            activeOwner = userId
            dataStoreManager.putString(KEY_LAST_SYNCED_USER_ID, userId)
            // Playlist filtering must have this account's identity before the restore job starts.
            dataStoreManager.putString("AccountEmail", supabase.auth.currentUserOrNull()?.email.orEmpty())
            if (waitForCloud) try {
                withTimeoutOrNull(LOGIN_SYNC_TIMEOUT_MS) {
                    syncDown(userId)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Logger.w(TAG, "Cloud restore unavailable; using this account's local library") }
        }
        cloudResumed = true
        if (waitForCloud) startPeriodicSync() else resumeCloudJobs()
    }

    suspend fun performAccountSwitch() = performLoginSync()

    /** Archive first, so even an offline logout cannot expose or destroy the outgoing library. */
    suspend fun performLogout() {
        stopSync()
        syncMutex.withLock {
            val previous = dataStoreManager.getString(KEY_LAST_SYNCED_USER_ID).firstOrNull()
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.player.pause()
                mediaPlayerHandler.resetSongAndQueue()
            }
            userDataSyncRepository.activateLocalAccount("", previous)
            try {
                supabase.auth.signOut()
            } catch (e: Exception) {
                try {
                    if (e is CancellationException) throw e
                    // Explicit logout must also work without reaching the auth server.
                    Logger.w(TAG, "Remote logout unavailable; ending the local session")
                    supabase.auth.clearSession()
                } catch (localFailure: Exception) {
                    if (!previous.isNullOrEmpty() && supabase.auth.currentUserOrNull()?.id == previous) {
                        withContext(NonCancellable) { userDataSyncRepository.activateLocalAccount(previous, "") }
                        startPeriodicSync()
                    }
                    throw localFailure
                }
            }
            activeOwner = null
            dataStoreManager.clearPerUserData()
            dataStoreManager.putString(KEY_LAST_SYNCED_USER_ID, "")
            dataStoreManager.setCookie("", null)
        }
    }

    suspend fun resetData() {
        syncMutex.withLock {
            val userId = requireActiveOwner()
            userDataSyncRepository.clearCloudData(userId).getOrThrow()
            userDataSyncRepository.clearLocalUserData().getOrThrow()
        }
    }

    suspend fun deleteAccount() {
        stopSync()
        syncMutex.withLock {
            val userId = requireActiveOwner()
            withContext(Dispatchers.Main) { mediaPlayerHandler.player.pause(); mediaPlayerHandler.resetSongAndQueue() }
            userDataSyncRepository.deleteAccount(userId)
            dataStoreManager.clearLegacyProfilePrivacy(userId)
            supabase.auth.clearSession()
            activeOwner = null
            dataStoreManager.putString(KEY_LAST_SYNCED_USER_ID, "")
            dataStoreManager.clearPerUserData()
            dataStoreManager.setLoggedIn(false)
            dataStoreManager.setCookie("", null)
        }
    }
}
