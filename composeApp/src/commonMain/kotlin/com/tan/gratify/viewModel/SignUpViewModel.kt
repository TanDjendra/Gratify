package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.YouTubeLibraryRepository
import com.tan.data.sync.UserDataSyncManager
import com.tan.gratify.viewModel.auth.*
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import gratify.composeapp.generated.resources.*

/** Create credentials during signup; confirming email never changes an existing account's password. */
class SignUpViewModel(
    private val dataStoreManager: DataStoreManager,
    private val supabase: SupabaseClient,
    private val userDataSyncManager: UserDataSyncManager,
    private val youtubeLibrary: YouTubeLibraryRepository,
) : BaseViewModel() {
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState = _authState.asStateFlow()
    private val _formState = MutableStateFlow(SignUpFormState())
    val formState = _formState.asStateFlow()
    private val _otpState = MutableStateFlow(OtpFormState())
    val otpState = _otpState.asStateFlow()
    private val request = AuthRequestGate(viewModelScope)
    private val cooldown = AuthResendCooldown(viewModelScope) { seconds -> _otpState.update { it.copy(resendCooldownSeconds = seconds) } }
    private var verifiedEmail: String? = null
    private var googleYouTubeRequested = false
    private val googleLogin = GoogleLoginController(viewModelScope,
        launchBrowser = {
            // Extra access is chosen on the login page, never added to basic sign-in automatically.
            supabase.auth.signInWith(io.github.jan.supabase.auth.providers.Google) {
                configureGoogleLogin(googleYouTubeRequested)
            }
        },
        awaitSession = { supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated && supabase.auth.currentUserOrNull() != null } },
        hasSession = { supabase.auth.currentUserOrNull() != null },
        waitsForCallback = com.tan.gratify.di.externalAuthWaitsForCallback)
    val googleLoginState = googleLogin.state

    init {
        viewModelScope.launch {
            combine(supabase.auth.sessionStatus, _otpState) { status, otp -> status to otp }.collect { (status, otp) ->
                val email = otp.email
                if (email != null && !otp.verified && status is SessionStatus.Authenticated && _authState.value is AuthUiState.VerificationPending &&
                    !PasswordRecoveryCoordinator.pending.value && supabase.auth.currentUserOrNull()?.email.equals(email, ignoreCase = true)) {
                    request.launch {
                        _authState.value = AuthUiState.Loading(getString(Res.string.auth_verifying))
                        verifiedEmail = email
                        _otpState.update { it.copy(verified = true, errorMessage = null) }
                        try { completeSignUp(email) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            _authState.value = AuthUiState.VerificationPending(email)
                            _otpState.update { it.copy(errorMessage = getString(Res.string.auth_finish_failed)) }
                        }
                    }
                }
            }
        }
    }

    fun updateEmail(email: String) {
        if (request.busy) return
        _formState.update { it.copy(email = email, isEmailValid = isValidEmailAddress(normalizeAuthEmail(email))) }
        clearError()
    }
    fun updatePassword(password: String) {
        if (request.busy) return
        _formState.update { it.copy(password = password, isPasswordValid = isValidNewPassword(password), passwordStrength = evaluatePasswordStrength(password),
            isConfirmPasswordMatch = password == it.confirmPassword && isValidNewPassword(password)) }
        clearError()
    }
    fun updateConfirmPassword(password: String) {
        if (!request.busy) _formState.update { it.copy(confirmPassword = password,
            isConfirmPasswordMatch = password == it.password && isValidNewPassword(it.password)) }
        clearError()
    }
    fun updateName(name: String) {
        if (request.busy) return
        _formState.update { it.copy(name = name, isNameValid = name.trim().length in 1..80) }
        clearError()
    }
    fun updateTermsAccepted(accepted: Boolean) { if (!request.busy) _formState.update { it.copy(isTermsAccepted = accepted) } }
    fun updateOtpCode(code: String) {
        if (!request.busy) _otpState.update { it.copy(code = normalizeOtpCode(code), errorMessage = null) }
    }
    private fun clearError() { if (_authState.value is AuthUiState.Error) _authState.value = AuthUiState.Idle }

    fun sendOtp() {
        val form = _formState.value
        if (!form.isSignUpReady() || _otpState.value.email != null) return
        request.launch {
            _authState.value = AuthUiState.Loading(getString(Res.string.auth_creating_account))
            try {
                val email = normalizeAuthEmail(form.email)
                val result = supabase.auth.signUpWith(Email) {
                    this.email = email
                    password = form.password
                    data = buildJsonObject { put("display_name", form.name.trim()) }
                }
                _formState.update { it.copy(password = "", confirmPassword = "", isPasswordValid = false, isConfirmPasswordMatch = false) }
                _otpState.update { it.copy(email = email, code = "", errorMessage = null, statusMessage = null, verified = false) }
                if (result == null) {
                    require(supabase.auth.currentUserOrNull()?.email.equals(email, ignoreCase = true))
                    verifiedEmail = email
                    _otpState.update { it.copy(verified = true) }
                    completeSignUp(email)
                } else {
                    _authState.value = AuthUiState.VerificationPending(email)
                    cooldown.start()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                log("Signup failed; provider details omitted")
                val email = _otpState.value.email
                if (email != null) {
                    _authState.value = AuthUiState.VerificationPending(email)
                    _otpState.update { it.copy(errorMessage = getString(Res.string.auth_finish_failed)) }
                } else _authState.value = AuthUiState.Error(authFailureMessage(error, ::getString))
            }
        }
    }
    fun verifyOtp() {
        val otp = _otpState.value
        val email = otp.email ?: return
        if (!otp.isCodeComplete() && !otp.verified) return
        request.launch {
            _authState.value = AuthUiState.Loading(getString(Res.string.auth_verifying))
            _otpState.update { it.copy(errorMessage = null) }
            try {
                if (verifiedEmail != email || !supabase.auth.currentUserOrNull()?.email.equals(email, ignoreCase = true)) {
                    supabase.auth.verifyEmailOtp(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email, otp.code)
                    require(supabase.auth.currentUserOrNull()?.email.equals(email, ignoreCase = true))
                    verifiedEmail = email
                    _otpState.update { it.copy(verified = true) }
                }
                completeSignUp(email)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _authState.value = AuthUiState.VerificationPending(email)
                _otpState.update { it.copy(errorMessage = getString(if (verifiedEmail == email) Res.string.auth_finish_failed else Res.string.auth_otp_failed)) }
            }
        }
    }
    fun resendOtp() {
        val otp = _otpState.value
        val email = otp.email ?: return
        if (otp.resendCooldownSeconds > 0 || otp.isResending || verifiedEmail == email) return
        request.launch {
            _otpState.update { it.copy(isResending = true, errorMessage = null, statusMessage = null) }
            try {
                supabase.auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email)
                _otpState.update { it.copy(code = "", statusMessage = getString(Res.string.auth_code_sent)) }
                cooldown.start()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _otpState.update { it.copy(errorMessage = getString(Res.string.auth_resend_failed)) } }
            finally { _otpState.update { it.copy(isResending = false) } }
        }
    }
    fun editCredentials() {
        if (request.busy) return
        verifiedEmail = null
        _authState.value = AuthUiState.Idle
        _otpState.update { it.copy(email = null, code = "", errorMessage = null, statusMessage = null, verified = false) }
    }
    fun resetToIdle() {
        request.cancel()
        _authState.value = AuthUiState.Idle
        _otpState.update { it.copy(email = null, code = "", errorMessage = null, statusMessage = null, verified = false) }
        _formState.update { it.copy(password = "", confirmPassword = "", isPasswordValid = false, isConfirmPasswordMatch = false) }
        verifiedEmail = null
    }
    fun logInWithGoogle(connectYouTube: Boolean = false) {
        if (request.busy || googleLoginState.value is GoogleLoginState.Waiting) return
        googleYouTubeRequested = connectYouTube
        googleLogin.start()
    }
    suspend fun refreshYouTubeAfterGoogleLogin() {
        if (!googleYouTubeRequested) return
        // A missing/declined grant must not undo an otherwise successful Gratify login.
        try { kotlinx.coroutines.withTimeoutOrNull(10000) { youtubeLibrary.refreshConnection() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The collection can be connected again from the library. */ }
    }
    fun cancelGoogleLogin() = googleLogin.cancel()
    private suspend fun completeSignUp(email: String) {
        userDataSyncManager.performLoginSync(waitForCloud = false)
        dataStoreManager.putString("AccountEmail", email)
        // Cloud profile remains authoritative when it exists; a submitted draft must not overwrite it.
        if (dataStoreManager.getString("AppProfileName").first().isNullOrBlank()) {
            dataStoreManager.putString("AppProfileName", _formState.value.name.trim())
            dataStoreManager.putString("AccountName", _formState.value.name.trim())
        }
        dataStoreManager.setLoggedIn(true)
        // A draft name is not a saved public profile. The next screen commits the profile.
        _authState.value = AuthUiState.Authenticated(needsProfile = true)
    }
}
