package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.auth.AuthUiState
import com.tan.gratify.viewModel.auth.ForgotPasswordFormState
import com.tan.gratify.viewModel.auth.PasswordStrength
import com.tan.gratify.viewModel.auth.ResetPasswordFormState
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ForgotPasswordViewModel — State-machine-driven ViewModel untuk alur reset password
 * menggunakan Magic Link dari Supabase.
 *
 * Alur (Magic Link Reset):
 *   Idle → Loading (sendResetLink) → PasswordResetSent (menunggu user klik link di email)
 *       → PasswordResetReady (deep link kembali ke app) → Loading (setNewPassword)
 *       → PasswordResetSuccess → navigate ke Login
 *
 * PENTING:
 * - Magic link dikirim via `resetPasswordForEmail(email)` dari Supabase Auth.
 * - Setelah user mengklik link, app terbuka via deep link dan Supabase SDK
 *   otomatis membuat session recovery.
 * - DeteksiExit recovery session dilakukan di LoginLandingScreen/SplashScreen
 *   yang kemudian menavigasi ke ForgotPasswordScreen dengan state PasswordResetReady.
 */
class ForgotPasswordViewModel(
    private val dataStoreManager: DataStoreManager,
    private val supabase: SupabaseClient,
) : BaseViewModel() {

    // ── Auth State (sealed interface) ────────────────────────────────────────
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // ── Form State: Email input ──────────────────────────────────────────────
    private val _formState = MutableStateFlow(ForgotPasswordFormState())
    val formState: StateFlow<ForgotPasswordFormState> = _formState.asStateFlow()

    // ── Form State: New password input ───────────────────────────────────────
    private val _resetFormState = MutableStateFlow(ResetPasswordFormState())
    val resetFormState: StateFlow<ResetPasswordFormState> = _resetFormState.asStateFlow()

    // ── Form Field Updates ───────────────────────────────────────────────────

    fun updateEmail(email: String) {
        _formState.update {
            it.copy(
                email = email,
                isEmailValid = isValidEmail(email),
            )
        }
        // Clear error saat user mengedit input
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    fun updateNewPassword(password: String) {
        val strength = evaluatePasswordStrength(password)
        _resetFormState.update {
            it.copy(
                newPassword = password,
                isNewPasswordValid = password.length >= 8 && strength != PasswordStrength.WEAK,
                isConfirmPasswordMatch = password == it.confirmPassword && password.length >= 8,
                passwordStrength = strength,
            )
        }
    }

    fun updateConfirmPassword(password: String) {
        _resetFormState.update {
            it.copy(
                confirmPassword = password,
                isConfirmPasswordMatch = it.newPassword == password && it.newPassword.length >= 8,
            )
        }
    }

    // ── Validation Helpers ───────────────────────────────────────────────────

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$".toRegex()
        return email.matches(emailRegex)
    }

    // ── Core Actions ─────────────────────────────────────────────────────────

    /**
     * Step 1: Kirim magic link reset password ke email user.
     *
     * Supabase akan mengirim email berisi link yang mengarahkan kembali
     * ke app via deep link scheme `com.tan.gratify://login-callback`.
     */
    fun sendResetLink() {
        val email = _formState.value.email
        if (!_formState.value.isEmailValid) return

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading("Mengirim link reset...")
            try {
                supabase.auth.resetPasswordForEmail(email)

                // Magic link berhasil dikirim
                _authState.value = AuthUiState.PasswordResetSent(email)

            } catch (e: Throwable) {
                log("sendResetLink failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                _authState.value = AuthUiState.Error(mapResetError(e))
            }
        }
    }

    /**
     * Tandai bahwa deep link recovery berhasil diproses.
     * Dipanggil dari navigation handler saat mendeteksi recovery session.
     */
    fun markRecoveryReady() {
        _authState.value = AuthUiState.PasswordResetReady
    }

    /**
     * Expose SupabaseClient untuk pengecekan session recovery di UI layer.
     */
    fun getSupabaseClient(): SupabaseClient = supabase

    /**
     * Step 2: Set password baru setelah recovery session aktif.
     *
     * Pada titik ini, user sudah memiliki session recovery dari Supabase
     * (melalui magic link deep link). Kita tinggal memanggil `updateUser`
     * untuk mengubah password.
     */
    fun setNewPassword() {
        val form = _resetFormState.value
        if (!form.isValid()) return

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading("Menyimpan password baru...")
            try {
                supabase.auth.updateUser {
                    password = form.newPassword
                }

                // Password berhasil diubah
                _authState.value = AuthUiState.PasswordResetSuccess

            } catch (e: Throwable) {
                log("setNewPassword failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                val errorMsg = e.message ?: e.toString()
                val userMsg = when {
                    errorMsg.contains("same_password", ignoreCase = true) ||
                    errorMsg.contains("same as the old", ignoreCase = true) ->
                        "Password baru tidak boleh sama dengan password lama."

                    errorMsg.contains("weak_password", ignoreCase = true) ->
                        "Password terlalu lemah. Gunakan minimal 8 karakter."

                    errorMsg.contains("session", ignoreCase = true) ||
                    errorMsg.contains("expired", ignoreCase = true) ->
                        "Sesi reset telah kedaluwarsa. Silakan kirim ulang link reset."

                    else -> "Gagal mengubah password. Silakan coba lagi."
                }
                _authState.value = AuthUiState.Error(userMsg)
            }
        }
    }

    /**
     * Reset state ke Idle.
     */
    fun resetToIdle() {
        _authState.value = AuthUiState.Idle
        _formState.value = ForgotPasswordFormState()
        _resetFormState.value = ResetPasswordFormState()
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Map Supabase error ke pesan Indonesia yang user-friendly.
     */
    private fun mapResetError(e: Throwable): String {
        val msg = e.message ?: e.toString()
        return when {
            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("too many requests", ignoreCase = true) ->
                "Terlalu banyak percobaan. Silakan tunggu beberapa menit."

            msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("connection", ignoreCase = true) ->
                "Koneksi bermasalah. Periksa internet Anda dan coba lagi."

            msg.contains("user not found", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ->
                "Email tidak ditemukan. Pastikan email yang Anda masukkan benar."

            else -> "Gagal mengirim link reset. Silakan coba lagi nanti."
        }
    }

    private fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        val score = listOf(hasUpper, hasLower, hasDigit, hasSpecial).count { it }
        return when {
            score >= 4 && password.length >= 12 -> PasswordStrength.STRONG
            score >= 3 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}
