package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.postgrest
import com.tan.domain.data.entities.UserProfile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.component.inject
import com.tan.gratify.utils.compressImage
import kotlinx.datetime.Clock
import io.ktor.http.ContentType

data class CreateProfileUiState(
    val displayName: String = "",
    val avatarPath: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class CreateProfileViewModel(
    private val dataStoreManager: DataStoreManager,
) : BaseViewModel() {

    private val supabase: SupabaseClient by inject()
    private val userRepository: com.tan.domain.repository.UserRepository by inject()

    private val _uiState = MutableStateFlow(CreateProfileUiState())
    val uiState: StateFlow<CreateProfileUiState> = _uiState

    init {
        // Coba load nama jika sebelumnya sudah ada (fallback ke account name lokal)
        viewModelScope.launch {
            val existingName = dataStoreManager.getString("AppProfileName").first()
                ?: dataStoreManager.getString("AccountName").first()
            if (!existingName.isNullOrEmpty()) {
                _uiState.update { it.copy(displayName = existingName) }
            }
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun updateAvatarPath(path: String) {
        _uiState.update { it.copy(avatarPath = path) }
    }

    fun saveProfile() {
        if (_uiState.value.isSaving) return
        if (_uiState.value.displayName.isBlank()) {
            makeToast("Nama tidak boleh kosong.")
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            var avatarUrlToSave = _uiState.value.avatarPath
            try {
                // Upload avatar if not empty and not an http URL
                if (avatarUrlToSave.isNotEmpty() && !avatarUrlToSave.startsWith("http")) {
                    val compressedBytes = requireNotNull(compressImage(avatarUrlToSave)) { "Avatar cannot be processed" }
                    if (compressedBytes != null) {
                        val currentUserId = supabase.auth.currentUserOrNull()?.id
                        if (currentUserId != null) {
                            // Selalu gunakan ID user sebagai nama file agar file lama otomatis tertimpa (upsert = true)
                            val fileName = "${currentUserId}.jpg"
                            
                            // Upload to Supabase Storage
                            val bucket = supabase.storage.from("avatars")
                            bucket.upload(
                                path = fileName,
                                data = compressedBytes
                            ) {
                                upsert = true
                                contentType = ContentType.Image.JPEG
                            }
                            // Get public url dan tambahkan timestamp agar cache gambar (Coil) di aplikasi ter-refresh
                            val timestamp = Clock.System.now().toEpochMilliseconds()
                            avatarUrlToSave = bucket.publicUrl(fileName) + "?cacheNonce=$timestamp"
                        }
                    }
                }

                // Update ke Supabase Backend jika ada sesi
                val currentUserId = requireNotNull(supabase.auth.currentUserOrNull()?.id) { "Login required" }
                if (currentUserId != null) {
                    supabase.auth.updateUser {
                        data = buildJsonObject {
                            put("display_name", _uiState.value.displayName)
                            if (avatarUrlToSave.isNotEmpty()) {
                                put("avatar_url", avatarUrlToSave)
                            }
                        }
                    }
                    userRepository.upsertUserProfile(
                            UserProfile(
                                id = currentUserId,
                                displayName = _uiState.value.displayName,
                                avatarUrl = avatarUrlToSave.ifEmpty { null }
                            )
                    ).first().getOrThrow()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                makeToast("Profil belum tersimpan. Periksa koneksi lalu coba lagi.")
                return@launch
            }

            
            dataStoreManager.putString("AppProfileName", _uiState.value.displayName)
            if (avatarUrlToSave.isNotEmpty()) {
                dataStoreManager.putString("AppProfileImage", avatarUrlToSave)
            }
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
