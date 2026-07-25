package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.auth.AuthUiState
import com.tan.gratify.viewModel.auth.LoginFormState
import com.tan.gratify.viewModel.auth.OtpFormState
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.tan.data.sync.UserDataSyncManager
import com.tan.logger.Logger

/**
 * EmailLoginViewModel — ViewModel untuk alur login menggunakan email + password.
 *
 * Alur utama:
 *   Idle → Loading (signIn) → Authenticated (sukses)
 *                            → VerificationPending (email belum dikonfirmasi → OTP)
 *                            → Error (kredensial salah)
 *
 * Fitur:
 * - Login dengan email + password via `signInWith(Email)`
 * - Fallback ke OTP verification jika email belum dikonfirmasi
 * - Resend OTP dengan cooldown 60 detik
 * - Error mapping ke pesan Bahasa Indonesia
 */
class EmailLoginViewModel(
    private val dataStoreManager: DataStoreManager,
    private val supabase: SupabaseClient,
    private val userDataSyncManager: UserDataSyncManager,
) : BaseViewModel() {

    // ── Auth State ────────────────────────────────────────────────────────────
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // ── Form State (email + password) ─────────────────────────────────────────
    private val _formState = MutableStateFlow(LoginFormState())
    val formState: StateFlow<LoginFormState> = _formState.asStateFlow()

    // ── OTP State (untuk fallback verifikasi) ─────────────────────────────────
    private val _otpState = MutableStateFlow(OtpFormState())
    val otpState: StateFlow<OtpFormState> = _otpState.asStateFlow()

    private var cooldownJob: Job? = null

    // ── Form Field Updates ───────────────────────────────────────────────────

    fun updateEmail(email: String) {
        _formState.update {
            it.copy(
                email = email,
                isEmailValid = isValidEmail(email),
            )
        }
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    fun updatePassword(password: String) {
        _formState.update {
            it.copy(
                password = password,
                isPasswordValid = password.length >= 8,
            )
        }
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    fun updateOtpCode(code: String) {
        if (code.length <= 6 && code.all { it.isDigit() }) {
            _otpState.update { it.copy(code = code) }
        }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$".toRegex()
        return email.matches(emailRegex)
    }

    // ── Core Auth: Login ─────────────────────────────────────────────────────

    /**
     * Login dengan email + password.
     *
     * Jika Supabase mengembalikan error "Email not confirmed",
     * secara otomatis mengirim ulang OTP dan pindah ke state VerificationPending.
     */
    fun signIn() {
        val form = _formState.value
        if (!form.isValid()) return

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading("Masuk ke akun...")
            try {
                supabase.auth.signInWith(Email) {
                    email = form.email
                    password = form.password
                }

                // Login berhasil — simpan data dan navigasi
                handleSuccessfulLogin()

            } catch (e: Throwable) {
                log("signIn failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                val errorMsg = e.message ?: e.toString()

                when {
                    errorMsg.contains("email_not_confirmed", ignoreCase = true) ||
                    errorMsg.contains("Email not confirmed", ignoreCase = true) -> {
                        handleEmailNotConfirmed(form.email)
                    }

                    else -> {
                        _authState.value = AuthUiState.Error(mapLoginError(e))
                    }
                }
            }
        }
    }

    /**
     * Verifikasi 6-digit OTP code (fallback saat email belum dikonfirmasi).
     * Setelah OTP berhasil, login ulang dengan email + password.
     */
    fun verifyOtp() {
        val code = _otpState.value.code
        val email = (_authState.value as? AuthUiState.VerificationPending)?.email
            ?: _formState.value.email

        if (code.length != 6 || email.isBlank()) return

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading("Memverifikasi kode...")
            try {
                supabase.auth.verifyEmailOtp(
                    type = io.github.jan.supabase.auth.OtpType.Email.SIGNUP,
                    email = email,
                    token = code,
                )

                // OTP terverifikasi — sekarang coba login ulang
                handleSuccessfulLogin()

            } catch (e: Throwable) {
                log("verifyOtp failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                val errorMsg = e.message ?: e.toString()
                val userMsg = when {
                    errorMsg.contains("otp_expired", ignoreCase = true) ||
                    errorMsg.contains("expired", ignoreCase = true) ->
                        "Kode verifikasi sudah kedaluwarsa. Silakan kirim ulang."

                    errorMsg.contains("invalid", ignoreCase = true) ->
                        "Kode verifikasi salah atau sudah kedaluwarsa."

                    else -> "Verifikasi gagal. Silakan coba lagi."
                }
                _authState.value = AuthUiState.VerificationPending(email)
                makeToast(userMsg)
            }
        }
    }

    /**
     * Kirim ulang OTP ke email user.
     */
    fun resendOtp() {
        val email = (_authState.value as? AuthUiState.VerificationPending)?.email
            ?: _formState.value.email
        if (email.isBlank() || _otpState.value.isResending) return

        viewModelScope.launch {
            _otpState.update { it.copy(isResending = true) }
            try {
                supabase.auth.resendEmail(
                    type = io.github.jan.supabase.auth.OtpType.Email.SIGNUP,
                    email = email,
                )
                makeToast("Kode verifikasi baru telah dikirim ke $email")
                _otpState.update { it.copy(code = "", isResending = false) }
                startResendCooldown()
            } catch (e: Throwable) {
                log("Resend OTP gagal: ${e.message}", com.tan.logger.LogLevel.ERROR)
                _otpState.update { it.copy(isResending = false) }
                makeToast("Gagal mengirim ulang kode. Coba lagi nanti.")
            }
        }
    }

    /**
     * Reset state ke Idle.
     */
    fun resetToIdle() {
        _authState.value = AuthUiState.Idle
        _otpState.value = OtpFormState()
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Handle email yang belum dikonfirmasi:
     * Kirim ulang OTP dan pindah ke VerificationPending.
     */
    private suspend fun handleEmailNotConfirmed(email: String) {
        try {
            supabase.auth.resendEmail(
                type = io.github.jan.supabase.auth.OtpType.Email.SIGNUP,
                email = email,
            )
            _authState.value = AuthUiState.VerificationPending(email)
            startResendCooldown()
            makeToast("Email belum diverifikasi. Kode verifikasi dikirim ke $email")
        } catch (resendError: Throwable) {
            log("handleEmailNotConfirmed resend failed: ${resendError.message}", com.tan.logger.LogLevel.ERROR)
            _authState.value = AuthUiState.Error(
                "Email belum diverifikasi dan gagal mengirim kode verifikasi. Coba lagi nanti."
            )
        }
    }

    /**
     * Handle login berhasil: simpan data ke DataStore dan emit Authenticated.
     */
    private suspend fun handleSuccessfulLogin() {
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            dataStoreManager.clearPerUserData()
            dataStoreManager.setLoggedIn(true)
            dataStoreManager.putString("AccountEmail", user.email ?: "")

            val profileName = user.userMetadata
                ?.get("display_name")
                ?.toString()
                ?.replace("\"", "")
                ?: ""
            if (profileName.isNotEmpty()) {
                dataStoreManager.putString("AccountName", profileName)
                dataStoreManager.putString("AppProfileName", profileName)
            }

            val needsProfile = checkNeedsProfile()
            
            // Pulihkan semua data user (playlist, likes, artists, history, dll).
            // performLoginSync membersihkan DB akun sebelumnya bila akun yang login berbeda,
            // agar tiap akun punya datanya sendiri dan tidak saling menabrak.
            userDataSyncManager.performLoginSync()
            
            _authState.value = AuthUiState.Authenticated(needsProfile = needsProfile)
        } else {
            _authState.value = AuthUiState.Error("Sesi tidak ditemukan setelah login.")
        }
    }

    /**
     * Cek apakah user membutuhkan profile setup.
     */
    private suspend fun checkNeedsProfile(): Boolean {
        val appProfileName = dataStoreManager.getString("AppProfileName").first()
        return appProfileName.isNullOrEmpty()
    }

    /**
     * Map Supabase error ke pesan Indonesia yang user-friendly.
     */
    private fun mapLoginError(e: Throwable): String {
        val msg = e.message ?: e.toString()
        return when {
            msg.contains("Invalid login credentials", ignoreCase = true) ||
            msg.contains("invalid_credentials", ignoreCase = true) ->
                "Email atau password salah. Silakan coba lagi."

            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("too many requests", ignoreCase = true) ->
                "Terlalu banyak percobaan. Silakan tunggu beberapa menit."

            msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("connection", ignoreCase = true) ->
                "Koneksi bermasalah. Periksa internet Anda dan coba lagi."

            msg.contains("user not found", ignoreCase = true) ->
                "Akun tidak ditemukan. Silakan daftar terlebih dahulu."

            else -> "Login gagal. Silakan coba lagi nanti."
        }
    }

    /**
     * Start 60-second cooldown untuk resend OTP.
     */
    private fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (i in 60 downTo 0) {
                _otpState.update { it.copy(resendCooldownSeconds = i) }
                if (i > 0) delay(1000)
            }
        }
    }
}
