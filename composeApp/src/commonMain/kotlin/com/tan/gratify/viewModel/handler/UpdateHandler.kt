package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.UpdateRepository
import com.tan.domain.utils.Resource
import com.tan.gratify.utils.VersionManager
import com.tan.logger.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.no_update
import gratify.composeapp.generated.resources.version_format

class UpdateHandler(
    private val scope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val updateRepository: UpdateRepository,
    private val makeToast: (String) -> Unit,
    private val log: (String, LogLevel) -> Unit,
) {
    private val _updateResponse = MutableStateFlow<UpdateData?>(null)
    val updateResponse: StateFlow<UpdateData?> = _updateResponse

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate

    var showedUpdateDialog: Boolean = false

    fun checkForUpdate(isManual: Boolean = false) {
        scope.launch {
            _isCheckingUpdate.value = true
            val updateChannel = dataStoreManager.updateChannel.first()
            dataStoreManager.putString(
                "CheckForUpdateAt",
                System.currentTimeMillis().toString(),
            )
            val currentVersion = runCatching {
                org.jetbrains.compose.resources.getString(
                    Res.string.version_format,
                    VersionManager.getVersionName()
                )
            }.getOrElse {
                "v" + VersionManager.getVersionName()
            }
            if (updateChannel == DataStoreManager.GITHUB) {
                updateRepository.checkForGithubReleaseUpdate().collectLatest { response ->
                    handleUpdateResponse(response, currentVersion, isManual)
                    _isCheckingUpdate.value = false
                }
            } else if (updateChannel == DataStoreManager.FDROID) {
                updateRepository.checkForFdroidUpdate().collectLatest { response ->
                    handleUpdateResponse(response, currentVersion, isManual)
                    _isCheckingUpdate.value = false
                }
            }
        }
    }

    private suspend fun handleUpdateResponse(
        response: Resource<UpdateData>,
        currentVersion: String,
        isManual: Boolean,
    ) {
        val data = response.data
        when (response) {
            is Resource.Success if (data != null) -> {
                _updateResponse.value = data
                showedUpdateDialog = true
                if (data.tagName == currentVersion) {
                    if (isManual) {
                        val noUpdateStr = runCatching {
                            org.jetbrains.compose.resources.getString(Res.string.no_update)
                        }.getOrDefault("Your version is the newest")
                        makeToast(noUpdateStr)
                    }
                }
            }

            is Resource.Error -> {
                log("Check for update error: ${response.message}", LogLevel.WARN)
                if (isManual) {
                    makeToast(response.message ?: "Failed to check for updates")
                }
            }

            else -> {
                if (isManual && response is Resource.Success && data == null) {
                    makeToast("No update data found")
                }
            }
        }
    }
}
