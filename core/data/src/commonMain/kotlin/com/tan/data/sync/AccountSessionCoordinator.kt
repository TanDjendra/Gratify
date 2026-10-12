package com.tan.data.sync

import com.tan.domain.manager.DataStoreManager
import com.tan.logger.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AccountAccess {
    data object Loading : AccountAccess
    data object SignedOut : AccountAccess
    data class Ready(val owner: String, val offline: Boolean, val needsProfile: Boolean) : AccountAccess
    data object RecoveryRequired : AccountAccess
}

/** Local access and online authorization remain separate; the SDK owns refresh/retry. */
class AccountSessionCoordinator(
    private val supabase: SupabaseClient,
    private val preferences: DataStoreManager,
    private val sync: UserDataSyncManager,
    scope: CoroutineScope,
    private val recoveryPending: () -> Boolean = { false },
) {
    private val _access = MutableStateFlow<AccountAccess>(AccountAccess.Loading)
    val access = _access.asStateFlow()
    private val retries = MutableStateFlow(0)
    fun retry() { retries.value++ }

    init {
        scope.launch {
            // Sequential collection avoids cancelling a local ownership transaction when a
            // preference is written by that same transaction.
            combine(supabase.auth.sessionStatus, preferences.loggedIn, retries) { status, flag, _ -> status to flag }
                .collect { (status, flag) ->
                    if (recoveryPending()) return@collect
                    if (supabase.auth.sessionStatus.value != status) return@collect
                    try {
                        val previous = preferences.getString("last_synced_user_id").first()
                        when (status) {
                            is SessionStatus.Initializing -> Unit
                            is SessionStatus.Authenticated -> {
                                val owner = status.session.user?.id
                                // New sign-ins are activated by their existing login flow first.
                                if (owner.isNullOrBlank() || flag != DataStoreManager.TRUE && previous != owner) {
                                    _access.value = AccountAccess.SignedOut
                                    return@collect
                                }
                                if ((_access.value as? AccountAccess.Ready)?.owner != owner) _access.value = AccountAccess.Loading
                                sync.resumeAuthenticatedAccount(owner)
                                if (supabase.auth.currentUserOrNull()?.id != owner) return@collect
                                if (preferences.loggedIn.first() != DataStoreManager.TRUE) preferences.setLoggedIn(true)
                                _access.value = AccountAccess.Ready(owner, false,
                                    preferences.getString("AppProfileName").first().isNullOrBlank())
                            }
                            is SessionStatus.RefreshFailure -> {
                                val storedOwner = supabase.auth.sessionManager.loadSession()?.user?.id
                                if (!previous.isNullOrBlank() && storedOwner == previous) {
                                    sync.resumeOfflineAccount(previous)
                                    if (supabase.auth.sessionStatus.value != status) return@collect
                                    _access.value = AccountAccess.Ready(previous, true,
                                        preferences.getString("AppProfileName").first().isNullOrBlank())
                                } else {
                                    // Failure is not logout. Keep preferences and allow retry.
                                    _access.value = if (flag == DataStoreManager.TRUE) AccountAccess.RecoveryRequired else AccountAccess.SignedOut
                                }
                            }
                            is SessionStatus.NotAuthenticated -> {
                                if (flag == DataStoreManager.TRUE || !previous.isNullOrBlank()) {
                                    _access.value = AccountAccess.Loading
                                    sync.invalidateSessionAccess()
                                }
                                if (supabase.auth.sessionStatus.value != status) return@collect
                                _access.value = AccountAccess.SignedOut
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        Logger.w("AccountSession", "Local account recovery unavailable; session retained")
                        _access.value = AccountAccess.RecoveryRequired
                    }
                }
        }
    }
}
