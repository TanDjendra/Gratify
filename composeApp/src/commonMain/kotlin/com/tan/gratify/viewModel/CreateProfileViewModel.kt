package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

const val PROFILE_NAME_LIMIT = 64

enum class ProfileSaveStage { UPLOADING_PHOTO, SAVING_PROFILE }

enum class ProfileSaveProblem { NAME_REQUIRED, NAME_TOO_LONG, PHOTO_FAILED, PHOTO_UPLOAD_FAILED, SIGN_IN_REQUIRED, ACCOUNT_CHANGED, SAVE_FAILED }

fun profileNameProblem(name: String): ProfileSaveProblem? = when {
    name.trim().isEmpty() -> ProfileSaveProblem.NAME_REQUIRED
    name.trim().length > PROFILE_NAME_LIMIT -> ProfileSaveProblem.NAME_TOO_LONG
    else -> null
}

data class CreateProfileUiState(
    val ownerId: String? = null,
    val displayName: String = "",
    val avatarPath: String = "",
    val initialName: String = "",
    val initialAvatar: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val problem: ProfileSaveProblem? = null,
    val saveStage: ProfileSaveStage? = null,
) {
    val hasChanges get() = displayName.trim() != initialName.trim() || avatarPath != initialAvatar
}

/** One immutable submission for both onboarding and editing an existing profile. */
class CreateProfileViewModel(private val gateway: ProfileEditorGateway) : BaseViewModel() {
    private val identity = gateway.initialIdentity()
    private val _uiState = MutableStateFlow(CreateProfileUiState(
        ownerId = gateway.currentUserId(), displayName = identity.first, avatarPath = identity.second,
        initialName = identity.first, initialAvatar = identity.second,
    ))
    val uiState: StateFlow<CreateProfileUiState> = _uiState

    fun beginEditing(ownerId: String?, name: String, avatar: String?) {
        if (_uiState.value.isSaving) return
        _uiState.value = CreateProfileUiState(ownerId, name, avatar.orEmpty(), name, avatar.orEmpty())
    }
    fun updateDisplayName(name: String) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(displayName = name, problem = null, saveSuccess = false) }
    }
    fun updateAvatarPath(path: String) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(avatarPath = path, problem = null, saveSuccess = false) }
    }
    /** A returned picker result belongs to the account that opened it, not to editor visibility. */
    fun applySelectedAvatar(requestOwner: String?, path: String?): Boolean {
        if (path.isNullOrBlank() || requestOwner == null || _uiState.value.isSaving ||
            _uiState.value.ownerId != requestOwner || gateway.currentUserId() != requestOwner) return false
        updateAvatarPath(path)
        return true
    }
    fun saveProfile() {
        val submitted = _uiState.value
        if (submitted.isSaving || submitted.saveSuccess) return
        val problem = profileNameProblem(submitted.displayName)
        if (problem != null) { _uiState.update { it.copy(problem = problem) }; return }
        val owner = submitted.ownerId
        if (owner == null) { _uiState.update { it.copy(problem = ProfileSaveProblem.SIGN_IN_REQUIRED) }; return }
        if (gateway.currentUserId() != owner) { _uiState.update { it.copy(problem = ProfileSaveProblem.ACCOUNT_CHANGED) }; return }
        val name = submitted.displayName.trim()
        _uiState.update { it.copy(isSaving = true, problem = null, saveStage =
            if (submitted.avatarPath.isNotBlank() && !submitted.avatarPath.startsWith("https://"))
                ProfileSaveStage.UPLOADING_PHOTO else ProfileSaveStage.SAVING_PROFILE) }
        viewModelScope.launch {
            var serverAvatar: String? = null
            fun confirmSaved(avatar: String) {
                _uiState.update { it.copy(displayName = name, avatarPath = avatar, initialName = name,
                    initialAvatar = avatar, saveSuccess = true, problem = null) }
            }
            try {
                withTimeout(30_000) {
                    fun checkOwner() {
                        if (gateway.currentUserId() != owner) throw ProfileWriteException(ProfileSaveProblem.ACCOUNT_CHANGED)
                    }
                    checkOwner()
                    val avatar = if (submitted.avatarPath.isBlank()) "" else if (submitted.avatarPath.startsWith("https://")) {
                        submitted.avatarPath
                    } else gateway.uploadAvatar(owner, submitted.avatarPath)
                    checkOwner()
                    _uiState.update { it.copy(saveStage = ProfileSaveStage.SAVING_PROFILE) }
                    gateway.writeMetadata(owner, name, avatar)
                    checkOwner()
                    gateway.writeProfile(owner, name, avatar)
                    checkOwner()
                    serverAvatar = avatar
                    // Server identity is authoritative; cache failure must not report a failed server save.
                    try { withTimeout(2_000) { gateway.cacheIdentity(owner, name, avatar) } }
                    catch (_: TimeoutCancellationException) { }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                    checkOwner()
                    confirmSaved(avatar)
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                val confirmed = serverAvatar
                if (confirmed != null && gateway.currentUserId() == owner) confirmSaved(confirmed)
                else _uiState.update { it.copy(problem = ProfileSaveProblem.SAVE_FAILED) }
            } catch (failure: ProfileWriteException) {
                _uiState.update { it.copy(problem = failure.problem) }
            } catch (_: Exception) {
                _uiState.update { it.copy(problem = ProfileSaveProblem.SAVE_FAILED) }
            } finally { _uiState.update { it.copy(isSaving = false, saveStage = null) } }
        }
    }
}
