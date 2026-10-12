package com.tan.gratify.expect

import com.tan.domain.data.model.update.UpdateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class UpdateStage { IDLE, PREPARING, DOWNLOADING, APPLYING, VERIFYING, READY, NEED_PERMISSION, INSTALLING, ERROR }

data class UpdateInstallState(
    val stage: UpdateStage = UpdateStage.IDLE,
    val update: UpdateData? = null,
    val downloadId: Long = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null,
    val canRetry: Boolean = true,
    val delta: Boolean = false,
    val canDownloadFull: Boolean = false,
) {
    val progress: Float? get() = if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null
}

private val statusRequests = MutableStateFlow(0L)
val updateStatusRequests: StateFlow<Long> = statusRequests
fun openUpdateStatus() { statusRequests.update { it + 1 } }
