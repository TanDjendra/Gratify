package com.tan.gratify.viewModel.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

sealed interface GoogleLoginState {
    data object Idle : GoogleLoginState
    data object Waiting : GoogleLoginState
    data class Failed(val message: String) : GoogleLoginState
}

/** One browser attempt at a time, with explicit cancellation and a bounded wait. */
class GoogleLoginController(
    private val scope: CoroutineScope,
    private val launchBrowser: suspend () -> Unit,
    private val awaitSession: suspend () -> Unit,
    private val hasSession: () -> Boolean,
    private val waitsForCallback: Boolean,
    private val timeout: Duration = 5.minutes,
) {
    private val attempt = Mutex()
    private var job: Job? = null
    private val _state = MutableStateFlow<GoogleLoginState>(GoogleLoginState.Idle)
    val state = _state.asStateFlow()

    fun start(): Boolean {
        if (!attempt.tryLock()) return false
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                _state.value = GoogleLoginState.Waiting
                withTimeout(timeout) {
                    launchBrowser()
                    if (waitsForCallback && !hasSession()) {
                        _state.value = GoogleLoginState.Failed("Login Google belum selesai. Tutup tab lama, lalu coba lagi.")
                    } else {
                        awaitSession()
                        _state.value = GoogleLoginState.Idle
                    }
                }
            } catch (_: TimeoutCancellationException) {
                _state.value = GoogleLoginState.Failed("Waktu login Google habis. Tutup tab lama, lalu coba lagi.")
            } catch (cancelled: CancellationException) {
                _state.value = GoogleLoginState.Idle
                throw cancelled
            } catch (_: Exception) {
                // Browser URLs and provider errors may contain credentials; never expose them.
                _state.value = GoogleLoginState.Failed("Tidak dapat menyelesaikan login Google. Periksa koneksi, lalu coba lagi.")
            } finally {
                attempt.unlock()
            }
        }
        return true
    }

    fun cancel() { job?.cancel() }
}
