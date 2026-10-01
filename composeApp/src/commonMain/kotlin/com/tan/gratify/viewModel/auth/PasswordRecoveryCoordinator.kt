package com.tan.gratify.viewModel.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A callback URI requests the screen; only the SDK's successful session callback enables the form. */
object PasswordRecoveryCoordinator {
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()
    private val _verifiedUserId = MutableStateFlow<String?>(null)
    val verifiedUserId = _verifiedUserId.asStateFlow()

    fun request() { _verifiedUserId.value = null; _pending.value = true }
    fun verify(userId: String) { if (_pending.value) _verifiedUserId.value = userId }
    fun clear() { _pending.value = false; _verifiedUserId.value = null }
}
