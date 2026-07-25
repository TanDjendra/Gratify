package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.data.sync.UserDataSyncManager
import com.tan.logger.Logger
import com.tan.gratify.viewModel.auth.AuthUiState
import com.tan.gratify.viewModel.auth.OtpFormState
import com.tan.gratify.viewModel.auth.PasswordStrength
import com.tan.gratify.viewModel.auth.SignUpFormState
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SignUpViewModel — State-machine-driven ScreenModel untuk alur pendaftaran OTP.
 *
 * Alur (Passwordless OTP → Set Password):
 *   Idle → Loading (checkEmail) → Loading (sendOtp) → VerificationPending (OTP input)
 *       → Loading (verifyOtp) → set password & metadata → Authenticated(needsProfile)
 *
 * Fitur utama:
 * - Duplicate email blocking via RPC `check_email_exists` + Supabase Auth response
 * - Mandatory 6-digit OTP email verification (menggunakan `signInWith(OTP)`)
 * - Password disimpan via `updateUser` setelah OTP terverifikasi
 * - Profile existence check setelah verifikasi berhasil
 * - Error mapping ke pesan Bahasa Indonesia yang user-friendly
 */
class SignUpViewModel(
    private val dataStoreManager: DataStoreManager,
    private val supabase: SupabaseClient,
    private val userDataSyncManager: UserDataSyncManager,
) : BaseViewModel() {

    // ── Auth State (sealed interface) ────────────────────────────────────────
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // ── Form State (input fields) ────────────────────────────────────────────
    private val _formState = MutableStateFlow(SignUpFormState())
    val formState: StateFlow<SignUpFormState> = _formState.asStateFlow()

    // ── OTP State ────────────────────────────────────────────────────────────
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
        // Clear error saat user mengedit input
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    fun updatePassword(password: String) {
        _formState.update {
            it.copy(
                password = password,
                isPasswordValid = password.length >= 8,
                passwordStrength = evaluatePasswordStrength(password),
            )
        }
    }

    fun updateName(name: String) {
        _formState.update {
            it.copy(
                name = name,
                isNameValid = name.isNotBlank(),
            )
        }
    }

    fun updateTermsAccepted(accepted: Boolean) {
        _formState.update { it.copy(isTermsAccepted = accepted) }
    }

    fun updateOtpCode(code: String) {
        if (code.length <= 6 && code.all { it.isDigit() }) {
            _otpState.update { it.copy(code = code) }
        }
    }

    // ── Validation Helpers ───────────────────────────────────────────────────

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$".toRegex()
        return email.matches(emailRegex)
    }

    // ── Core Auth Actions ────────────────────────────────────────────────────

    /**
     * Step 1: Lanjutkan ke langkah berikutnya (Passwordless / OTP)
     */
    fun proceedWithEmail(onEmailAvailable: () -> Unit) {
        if (!_formState.value.isEmailValid) return
        _authState.value = AuthUiState.Idle
        onEmailAvailable()
    }

    /**
     * Step 2: Kirim OTP ke email user via `signInWith(OTP)`.
     */
    fun sendOtp() {
        val form = _formState.value
        if (!form.isSignUpReady()) return

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading("Mengirim kode verifikasi...")
            try {
                supabase.auth.signInWith(OTP) {
                    email = form.email
                    createUser = true
                }

                // OTP berhasil dikirim via template "Magic Link"
                _authState.value = AuthUiState.VerificationPending(form.email)
                startResendCooldown()

            } catch (e: Throwable) {
                log("sendOtp failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                _authState.value = AuthUiState.Error(mapSignUpError(e))
            }
        }
    }

    /**
     * Step 3: Verifikasi 6-digit kode OTP yang diinput user.
     *
     * Setelah kode terverifikasi:
     * 1. Set password via updateUser (karena signInWith(OTP) membuat akun passwordless)
     * 2. Set display_name via metadata
     * 3. Tandai login di DataStore
     * 4. Emit Authenticated
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
                    type = io.github.jan.supabase.auth.OtpType.Email.EMAIL,
                    email = email,
                    token = code,
                )

                // Kode terverifikasi & session aktif
                // Set password (karena signInWith(OTP) tidak set password)
                setPasswordAfterOtp()

                // Set display_name ke metadata
                saveDisplayNameToMetadata()

                // Cek apakah profil sudah ada
                val needsProfile = checkNeedsProfile()

                // Bersihkan data user lama sebelum menyimpan data user baru
                dataStoreManager.clearPerUserData()

                // Tandai login
                dataStoreManager.setLoggedIn(true)
                dataStoreManager.putString("AccountEmail", email)

                // Pulihkan semua data user dari cloud (playlist, likes, artists, dll).
                // performLoginSync membersihkan DB akun sebelumnya bila akun berbeda,
                // agar tiap akun punya datanya sendiri dan tidak saling menabrak.
                userDataSyncManager.performLoginSync()

                _authState.value = AuthUiState.Authenticated(needsProfile = needsProfile)

            } catch (e: Throwable) {
                log("verifyOtp failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                val errorMsg = e.message ?: e.toString()
                val userMsg = when {
                    errorMsg.contains("otp_expired", ignoreCase = true) ||
                    errorMsg.contains("expired", ignoreCase = true) ->
                        "Kode verifikasi sudah kedaluwarsa. Silakan kirim ulang."

                    errorMsg.contains("otp_disabled", ignoreCase = true) ->
                        "Verifikasi OTP tidak aktif. Hubungi dukungan."

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
     * Kirim ulang OTP ke email user via signInWith(OTP).
     */
    fun resendOtp() {
        val email = (_authState.value as? AuthUiState.VerificationPending)?.email
            ?: _formState.value.email
        if (email.isBlank() || _otpState.value.isResending) return

        viewModelScope.launch {
            _otpState.update { it.copy(isResending = true) }
            try {
                supabase.auth.signInWith(OTP) {
                    this.email = email
                    createUser = true // Harus true agar tidak ditolak jika user belum confirmed
                }
                makeToast("Kode verifikasi baru telah dikirim ke $email")
                _otpState.update { it.copy(code = "", isResending = false) }
                startResendCooldown()
            } catch (e: Throwable) {
                log("resendOtp failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                _otpState.update { it.copy(isResending = false) }
                makeToast("Gagal mengirim ulang kode. Coba lagi nanti.")
            }
        }
    }

    /**
     * Reset state ke Idle (digunakan saat user kembali dari error screen).
     */
    fun resetToIdle() {
        _authState.value = AuthUiState.Idle
        _otpState.value = OtpFormState()
    }

    fun logInWithGoogle() {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading("Menghubungkan ke Google...")
            try {
                supabase.auth.signInWith(io.github.jan.supabase.auth.providers.Google)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _authState.value = AuthUiState.Idle
            } catch (e: Throwable) {
                log("Google login failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
                _authState.value = AuthUiState.Error("Google Login gagal. Silakan coba lagi.")
            }
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Set password setelah OTP terverifikasi.
     * Karena `signInWith(OTP)` membuat akun passwordless,
     * kita perlu menambahkan password agar user bisa login dengan email+password di kemudian hari.
     */
    private suspend fun setPasswordAfterOtp() {
        val password = _formState.value.password
        if (password.length < 8) return

        try {
            supabase.auth.updateUser {
                this.password = password
            }
        } catch (e: Throwable) {
            log("setPasswordAfterOtp failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
        }
    }

    /**
     * Simpan display_name ke Supabase user metadata setelah verifikasi.
     */
    private suspend fun saveDisplayNameToMetadata() {
        val name = _formState.value.name
        if (name.isBlank()) return

        try {
            supabase.auth.updateUser {
                data = buildJsonObject {
                    put("display_name", name)
                }
            }
            dataStoreManager.putString("AppProfileName", name)
            dataStoreManager.putString("AccountName", name)
        } catch (e: Throwable) {
            log("saveDisplayNameToMetadata failed: ${e.message}", com.tan.logger.LogLevel.ERROR)
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
    private fun mapSignUpError(e: Throwable): String {
        val msg = e.message ?: e.toString()
        return when {
            msg.contains("already registered", ignoreCase = true) ||
            msg.contains("User already exists", ignoreCase = true) ||
            msg.contains("duplicate", ignoreCase = true) ->
                "Email sudah terdaftar. Silakan Log In menggunakan metode yang sesuai (Email/Google/Facebook)."

            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("too many requests", ignoreCase = true) ->
                "Terlalu banyak percobaan. Silakan tunggu beberapa menit."

            msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("connection", ignoreCase = true) ->
                "Koneksi bermasalah. Periksa internet Anda dan coba lagi."

            msg.contains("otp_disabled", ignoreCase = true) ->
                "Fitur OTP tidak aktif di server. Hubungi dukungan."

            else -> "Pendaftaran gagal. Silakan coba lagi nanti."
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
            password.length >= 12 && score >= 3 -> PasswordStrength.STRONG
            password.length >= 8 && score >= 2 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
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
