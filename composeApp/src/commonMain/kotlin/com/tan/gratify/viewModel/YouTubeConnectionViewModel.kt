package com.tan.gratify.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tan.domain.repository.YouTubeLibraryRepository
import com.tan.domain.repository.YouTubeConnectionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class YouTubeConnectionViewModel(private val repository: YouTubeLibraryRepository) : ViewModel() {
    val connection = repository.connection
    private val _url = MutableStateFlow<String?>(null)
    val url = _url.asStateFlow()
    private val _waiting = MutableStateFlow(false)
    val waiting = _waiting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var attempt: Job? = null

    fun connect() {
        if (attempt?.isActive == true) return
        attempt = viewModelScope.launch {
            _waiting.value = true; _error.value = null
            try {
                _url.value = repository.beginConnection()
                withTimeout(300000) {
                    while (true) {
                        delay(2500)
                        repository.refreshConnection()
                        if (connection.value.status == YouTubeConnectionStatus.CONNECTED) break
                    }
                }
            } catch (_: TimeoutCancellationException) { _error.value = "Izin Google belum selesai. Anda bisa mencoba kembali." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = "Koneksi belum dapat diselesaikan. Periksa koneksi dan coba kembali." }
            finally { _waiting.value = false }
        }
    }
    fun browserUnavailable() { cancel(); _error.value = "Browser tidak dapat dibuka. Coba kembali." }
    fun urlOpened() { _url.value = null }
    fun cancel() {
        val pending = attempt?.isActive == true
        attempt?.cancel(); _url.value = null
        if (pending) repository.cancelConnection()
    }
    fun refresh() { viewModelScope.launch { repository.refreshConnection() } }
    fun disconnect() { viewModelScope.launch {
        try { repository.disconnect() } catch (error: Exception) { _error.value = "Akses YouTube belum dapat diputuskan. Coba kembali." }
    } }
}
