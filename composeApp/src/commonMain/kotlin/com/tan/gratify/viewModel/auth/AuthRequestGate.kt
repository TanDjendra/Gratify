package com.tan.gratify.viewModel.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

/** Acquire before launching so rapid taps and IME actions cannot start competing requests. */
internal class AuthRequestGate(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private var job: Job? = null
    val busy get() = mutex.isLocked
    fun launch(block: suspend () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { block() } finally { mutex.unlock() }
        }
        return true
    }
    fun cancel() { job?.cancel() }
}

internal class AuthResendCooldown(private val scope: CoroutineScope, private val tick: suspend () -> Unit = { delay(1_000) }, private val update: (Int) -> Unit) {
    private var job: Job? = null
    fun start() {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            for (seconds in 60 downTo 0) {
                update(seconds)
                if (seconds > 0) tick()
            }
        }
    }
}

internal fun normalizeAuthEmail(email: String) = email.trim()
internal fun normalizeOtpCode(code: String) = code.filter { it in '0'..'9' }.take(6)
internal fun isValidNewPassword(password: String) = password.length >= 8
