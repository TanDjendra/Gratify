package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.UpdateRepository
import com.tan.domain.utils.Resource
import com.tan.gratify.utils.VersionManager
import com.tan.logger.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

class UpdateHandler(
    private val scope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val updateRepository: UpdateRepository,
    private val makeToast: (String) -> Unit,
    private val log: (String, LogLevel) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeoutMillis: Long = 25_000,
) {
    private val _updateResponse = MutableStateFlow<UpdateData?>(null)
    val updateResponse: StateFlow<UpdateData?> = _updateResponse
    private val _updateDialogRequest = MutableStateFlow(0L)
    val updateDialogRequest: StateFlow<Long> = _updateDialogRequest
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate
    private val checking = Mutex()
    var showedUpdateDialog: Boolean = false

    fun checkForUpdate(isManual: Boolean = false) {
        scope.launch {
            if (!checking.tryLock()) return@launch
            try {
                withTimeout(timeoutMillis) {
                    val lastAttempt = dataStoreManager.getString("UpdateLastAttempt").first()?.toLongOrNull() ?: 0L
                    val lastSuccess = dataStoreManager.getString("CheckForUpdateAt").first()?.toLongOrNull() ?: 0L
                    val elapsed = now() - lastAttempt
                    val interval = if (lastSuccess >= lastAttempt) AUTO_CHECK_INTERVAL else FAILED_CHECK_INTERVAL
                    if (!isManual && lastAttempt > 0 && elapsed in 0 until interval) return@withTimeout
                    _isCheckingUpdate.value = true
                    // A manual check emits again even if the same available release was dismissed.
                    if (isManual) _updateResponse.value = null
                    dataStoreManager.putString("UpdateLastAttempt", now().toString())
                    val response = when (dataStoreManager.updateChannel.first()) {
                        DataStoreManager.GITHUB -> updateRepository.checkForGithubReleaseUpdate().first()
                        DataStoreManager.FDROID -> updateRepository.checkForFdroidUpdate().first()
                        else -> Resource.Error<UpdateData>("Jalur pembaruan tidak tersedia. Gunakan rilis resmi GitHub.")
                    }
                    val data = response.data
                    if (response is Resource.Success && data != null) {
                        dataStoreManager.putString("CheckForUpdateAt", now().toString())
                        showedUpdateDialog = VersionManager.isUpdateAvailable(data)
                        _updateResponse.value = data
                        if (showedUpdateDialog) _updateDialogRequest.update { it + 1 }
                        if (isManual && !showedUpdateDialog) makeToast("Gratify sudah menggunakan versi terbaru")
                        if (isManual && showedUpdateDialog && data.apkUrl.isBlank()) {
                            makeToast("Perbarui Gratify melalui aplikasi F-Droid")
                        }
                    } else {
                        val message = response.message ?: "Informasi pembaruan belum tersedia"
                        log("Check for update: $message", LogLevel.WARN)
                        if (isManual) makeToast(message)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (isManual) makeToast("Pemeriksaan terlalu lama. Periksa koneksi lalu coba lagi.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                log("Update check failed; existing app remains available", LogLevel.WARN)
                if (isManual) makeToast("Belum bisa memeriksa pembaruan. Coba lagi saat online.")
            } finally {
                _isCheckingUpdate.value = false
                checking.unlock()
            }
        }
    }

    companion object {
        const val AUTO_CHECK_INTERVAL = 24L * 60 * 60 * 1000
        const val FAILED_CHECK_INTERVAL = 15L * 60 * 1000
    }
}
