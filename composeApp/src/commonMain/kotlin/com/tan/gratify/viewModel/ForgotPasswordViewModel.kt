package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.auth.*
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import gratify.composeapp.generated.resources.*

class ForgotPasswordViewModel(private val dataStoreManager: DataStoreManager, private val supabase: SupabaseClient) : BaseViewModel() {
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState = _authState.asStateFlow()
    private val _formState = MutableStateFlow(ForgotPasswordFormState())
    val formState = _formState.asStateFlow()
    private val _resetFormState = MutableStateFlow(ResetPasswordFormState())
    val resetFormState = _resetFormState.asStateFlow()
    private val _resendCooldown = MutableStateFlow(0)
    val resendCooldown = _resendCooldown.asStateFlow()
    private val request = AuthRequestGate(viewModelScope)
    private val exitRequest = AuthRequestGate(viewModelScope)
    private val cooldown = AuthResendCooldown(viewModelScope) { _resendCooldown.value = it }
    private val _sentEmail = MutableStateFlow<String?>(null)
    val sentEmail = _sentEmail.asStateFlow()
    val recoveryReady get() = PasswordRecoveryCoordinator.verifiedUserId.value?.let { it == supabase.auth.currentUserOrNull()?.id } == true

    fun updateEmail(email: String) {
        if (request.busy) return
        _formState.update { it.copy(email = email, isEmailValid = isValidEmailAddress(normalizeAuthEmail(email))) }
        if (_authState.value is AuthUiState.Error) _authState.value = AuthUiState.Idle
    }
    fun updateNewPassword(password: String) {
        if (request.busy) return
        _resetFormState.update { it.copy(newPassword = password, isNewPasswordValid = isValidNewPassword(password),
            isConfirmPasswordMatch = password == it.confirmPassword && isValidNewPassword(password), passwordStrength = evaluatePasswordStrength(password)) }
    }
    fun updateConfirmPassword(password: String) {
        if (!request.busy) _resetFormState.update { it.copy(confirmPassword = password,
            isConfirmPasswordMatch = it.newPassword == password && isValidNewPassword(it.newPassword)) }
    }
    fun sendResetLink() {
        val email = _sentEmail.value ?: normalizeAuthEmail(_formState.value.email)
        if (!isValidEmailAddress(email) || _resendCooldown.value > 0 || recoveryReady) return
        request.launch {
            _authState.value = AuthUiState.Loading(getString(Res.string.auth_sending_link))
            try {
                supabase.auth.resetPasswordForEmail(email,
                    redirectUrl = "${com.tan.gratify.di.externalAuthScheme}://login-callback?flow=recovery")
                _sentEmail.value = email
                cooldown.start()
                _authState.value = AuthUiState.PasswordResetSent(email)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                log("Recovery link request failed; provider details omitted")
                _authState.value = AuthUiState.Error(authFailureMessage(error, ::getString))
            }
        }
    }
    fun markRecoveryReady() { if (recoveryReady && !request.busy) _authState.value = AuthUiState.PasswordResetReady }
    fun setNewPassword() {
        val form = _resetFormState.value
        if (!form.isValid()) return
        if (!recoveryReady) { _authState.value = AuthUiState.Error(getString(Res.string.auth_recovery_invalid)); return }
        request.launch {
            _authState.value = AuthUiState.Loading(getString(Res.string.auth_saving_password))
            try {
                supabase.auth.updateUser { password = form.newPassword }
                supabase.auth.clearSession()
                dataStoreManager.setLoggedIn(false)
                PasswordRecoveryCoordinator.clear()
                _resetFormState.value = ResetPasswordFormState()
                _authState.value = AuthUiState.PasswordResetSuccess
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                log("Password recovery update failed; provider details omitted")
                _authState.value = AuthUiState.Error(if (error.message.orEmpty().contains("same_password", true))
                    getString(Res.string.auth_password_same) else authFailureMessage(error, ::getString))
            }
        }
    }
    fun editEmail() {
        if (request.busy) return
        _sentEmail.value = null
        _authState.value = AuthUiState.Idle
    }
    fun leaveRecovery(onDone: () -> Unit) {
        request.cancel()
        // A recovery session must not become an ordinary login merely by pressing Back.
        exitRequest.launch {
            try {
                if (PasswordRecoveryCoordinator.pending.value) {
                    if (PasswordRecoveryCoordinator.verifiedUserId.value != null) {
                        supabase.auth.clearSession()
                        dataStoreManager.setLoggedIn(false)
                    }
                    PasswordRecoveryCoordinator.clear()
                }
                resetToIdle()
                onDone()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _authState.value = AuthUiState.Error(getString(Res.string.auth_request_failed)) }
        }
    }
    fun resetToIdle() {
        request.cancel()
        _authState.value = AuthUiState.Idle
        _formState.value = ForgotPasswordFormState()
        _resetFormState.value = ResetPasswordFormState()
        _sentEmail.value = null
    }
}
