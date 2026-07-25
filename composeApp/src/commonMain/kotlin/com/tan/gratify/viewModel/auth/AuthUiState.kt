package com.tan.gratify.viewModel.auth

/**
 * Sealed interface yang merepresentasikan seluruh state UI
 * untuk alur autentikasi (Sign Up & Log In).
 *
 * Menggantikan pola multiple boolean flags yang rawan terhadap
 * "impossible states" (misal: loading = true DAN error != null sekaligus).
 */
sealed interface AuthUiState {

    /** State awal, tidak ada operasi yang berjalan. */
    data object Idle : AuthUiState

    /** Sedang memproses request (sign up, log in, verify OTP, dll). */
    data class Loading(val message: String = "Memproses...") : AuthUiState

    /**
     * Sign Up berhasil, menunggu verifikasi OTP.
     * User TIDAK boleh melanjutkan ke aplikasi sebelum OTP terverifikasi.
     */
    data class VerificationPending(val email: String) : AuthUiState

    /**
     * Autentikasi berhasil (Sign Up + OTP verified, atau Log In sukses).
     * @param needsProfile true jika user belum punya profil (harus ke ProfileSetupScreen).
     */
    data class Authenticated(val needsProfile: Boolean) : AuthUiState

    /**
     * Terjadi error pada proses autentikasi.
     * @param message Pesan error yang ditampilkan ke user (dalam Bahasa Indonesia).
     * @param isVerificationError true jika error karena email belum diverifikasi
     *        (user perlu memasukkan OTP). Digunakan pada Login flow.
     */
    data class Error(
        val message: String,
        val isVerificationError: Boolean = false,
    ) : AuthUiState

    // ── Password Reset States ────────────────────────────────────────────────

    /**
     * Magic link untuk reset password telah dikirim ke email.
     * User perlu membuka email dan klik link.
     */
    data class PasswordResetSent(val email: String) : AuthUiState

    /**
     * Deep link recovery berhasil diproses — user siap memasukkan password baru.
     * State ini diaktifkan setelah user mengklik magic link dari email.
     */
    data object PasswordResetReady : AuthUiState

    /**
     * Password berhasil direset. User akan diarahkan kembali ke login.
     */
    data object PasswordResetSuccess : AuthUiState
}

/**
 * Password strength level.
 */
enum class PasswordStrength {
    WEAK, FAIR, STRONG;

    val label: String
        get() = when (this) {
            WEAK -> "Lemah"
            FAIR -> "Cukup"
            STRONG -> "Kuat"
        }
}

/**
 * Evaluate password strength based on length, character variety.
 */
fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.length < 8) return PasswordStrength.WEAK
    var score = 0
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score >= 3 -> PasswordStrength.STRONG
        score >= 2 -> PasswordStrength.FAIR
        else -> PasswordStrength.WEAK
    }
}

/**
 * State form Sign Up yang terpisah dari AuthUiState.
 * Menyimpan data input field dan validasi lokal.
 */
data class SignUpFormState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    val password: String = "",
    val isPasswordValid: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val name: String = "",
    val isNameValid: Boolean = false,
    val isTermsAccepted: Boolean = false,
) {
    /** Form valid untuk submit Sign Up (email + password). */
    fun isSignUpReady(): Boolean = isEmailValid && isPasswordValid

    /** Form valid untuk complete profile (nama + terms). */
    fun isProfileReady(): Boolean = isNameValid && isTermsAccepted
}

/**
 * State form Log In yang terpisah dari AuthUiState.
 */
data class LoginFormState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    val password: String = "",
    val isPasswordValid: Boolean = false,
) {
    fun isValid(): Boolean = isEmailValid && isPasswordValid
}

/**
 * State form OTP input yang terpisah.
 */
data class OtpFormState(
    val code: String = "",
    val isResending: Boolean = false,
    val resendCooldownSeconds: Int = 0,
) {
    fun isCodeComplete(): Boolean = code.length == 6
}

/**
 * State form Forgot Password (langkah 1: masukkan email).
 */
data class ForgotPasswordFormState(
    val email: String = "",
    val isEmailValid: Boolean = false,
)

/**
 * State form Reset Password (langkah 2: masukkan password baru).
 */
data class ResetPasswordFormState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isNewPasswordValid: Boolean = false,
    val isConfirmPasswordMatch: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
) {
    fun isValid(): Boolean = isNewPasswordValid && isConfirmPasswordMatch
}
