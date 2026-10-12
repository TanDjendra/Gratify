package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.data.sync.UserDataSyncManager
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.auth.*
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import gratify.composeapp.generated.resources.*

class EmailLoginViewModel(
    private val dataStoreManager: DataStoreManager,
    private val supabase: SupabaseClient,
    private val userDataSyncManager: UserDataSyncManager,
) : BaseViewModel() {
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState = _authState.asStateFlow()
    private val _formState = MutableStateFlow(LoginFormState())
    val formState = _formState.asStateFlow()
    private val _otpState = MutableStateFlow(OtpFormState())
    val otpState = _otpState.asStateFlow()
    private val request = AuthRequestGate(viewModelScope)
    private val cooldown = AuthResendCooldown(viewModelScope) { seconds -> _otpState.update { it.copy(resendCooldownSeconds = seconds) } }
    private var verifiedEmail: String? = null

    init {
        viewModelScope.launch {
            combine(supabase.auth.sessionStatus, _otpState) { status, otp -> status to otp }.collect { (status, otp) ->
                val email = otp.email
                if (email != null && !otp.verified && status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated &&
                    _authState.value is AuthUiState.VerificationPending && !PasswordRecoveryCoordinator.pending.value &&
                    supabase.auth.currentUserOrNull()?.email.equals(email, ignoreCase = true)) {
                    request.launch {
                        _authState.value = AuthUiState.Loading(getString(Res.string.auth_verifying))
                        verifiedEmail = email
                        _otpState.update { it.copy(verified = true, errorMessage = null) }
                        try { completeLogin() }
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
        // Login accepts existing credentials; a new-password policy must not lock out older accounts.
        _formState.update { it.copy(password = password, isPasswordValid = password.isNotEmpty()) }
        clearError()
    }
    fun updateOtpCode(code: String) {
        if (request.busy) return
        _otpState.update { it.copy(code = normalizeOtpCode(code), errorMessage = null) }
    }
    private fun clearError() { if (_authState.value is AuthUiState.Error) _authState.value = AuthUiState.Idle }

    fun signIn() {
        val form = _formState.value
        if (!form.isValid() || _otpState.value.email != null) return
        request.launch {
            _authState.value = AuthUiState.Loading(getString(Res.string.auth_signing_in))
            try {
                supabase.auth.signInWith(Email) { email = normalizeAuthEmail(form.email); password = form.password }
                completeLogin()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                log("Email login failed; provider details omitted")
                val message = error.message.orEmpty()
                if (message.contains("email_not_confirmed", true) || message.contains("Email not confirmed", true)) {
                    val email = normalizeAuthEmail(form.email)
                    _otpState.update { it.copy(email = email, code = "", errorMessage = null, isResending = true) }
                    _authState.value = AuthUiState.VerificationPending(email)
                    try {
                        supabase.auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email)
                        cooldown.start()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { _otpState.update { it.copy(errorMessage = getString(Res.string.auth_resend_failed)) } }
                    finally { _otpState.update { it.copy(isResending = false) } }
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
                completeLogin()
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
        _formState.update { it.copy(password = "", isPasswordValid = false) }
        verifiedEmail = null
    }
    private suspend fun completeLogin() {
        val user = supabase.auth.currentUserOrNull() ?: error("Missing session")
        userDataSyncManager.performLoginSync(waitForCloud = false)
        dataStoreManager.putString("AccountEmail", user.email.orEmpty())
        val name = user.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (dataStoreManager.getString("AppProfileName").first().isNullOrBlank() && name.isNotBlank()) {
            dataStoreManager.putString("AppProfileName", name)
            dataStoreManager.putString("AccountName", name)
        }
        dataStoreManager.setLoggedIn(true)
        _formState.update { it.copy(password = "", isPasswordValid = false) }
        _authState.value = AuthUiState.Authenticated(dataStoreManager.getString("AppProfileName").first().isNullOrBlank())
    }
}
