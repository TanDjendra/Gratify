package com.tan.gratify.expect

import com.tan.domain.data.model.update.UpdateData
import kotlinx.coroutines.flow.StateFlow

expect fun downloadAndInstallApk(update: UpdateData)
expect fun updateInstallState(): StateFlow<UpdateInstallState>
expect fun installDownloadedUpdate()
expect fun retryApkUpdate()
expect fun cancelApkUpdate()
