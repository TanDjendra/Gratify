package com.tan.gratify.expect

import com.tan.domain.data.model.update.UpdateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val iosUpdateState = MutableStateFlow(UpdateInstallState())
actual fun downloadAndInstallApk(update: UpdateData) { }
actual fun updateInstallState(): StateFlow<UpdateInstallState> = iosUpdateState
actual fun installDownloadedUpdate() { }
actual fun retryApkUpdate() { }
actual fun cancelApkUpdate() { }
