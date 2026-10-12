package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.gratify.ui.component.LayerBackHandler
import com.tan.gratify.ui.component.OtpVerificationSection
import com.tan.gratify.ui.navigation.destination.login.*
import com.tan.gratify.viewModel.SignUpViewModel
import com.tan.gratify.viewModel.auth.AuthUiState
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignUpScreen(innerPadding: PaddingValues, navController: NavController, viewModel: SignUpViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit = {}, showBottomNavigation: () -> Unit = {}) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val otp by viewModel.otpState.collectAsStateWithLifecycle()
    val back = {
        if (otp.email != null && state !is AuthUiState.Loading && !otp.isResending) viewModel.editCredentials()
        else { viewModel.resetToIdle(); showBottomNavigation(); navController.navigateUp(); Unit }
    }
    LayerBackHandler(true, back)
    DisposableEffect(Unit) { hideBottomNavigation(); onDispose { viewModel.resetToIdle(); showBottomNavigation() } }
    LaunchedEffect(state) {
        val authenticated = state as? AuthUiState.Authenticated ?: return@LaunchedEffect
        navController.navigate(if (authenticated.needsProfile) CreateProfileDestination() else YouTubeConnectDestination) {
            popUpTo(0) { inclusive = true }; launchSingleTop = true
        }
    }
    AuthPage(stringResource(Res.string.auth_signup), innerPadding, back) {
        if (otp.email != null) OtpVerificationSection(otp.email!!, otp.code, viewModel::updateOtpCode, viewModel::verifyOtp, viewModel::resendOtp,
            state is AuthUiState.Loading, otp.isResending, otp.resendCooldownSeconds, otp.errorMessage,
            statusMessage = otp.statusMessage, onChangeEmail = viewModel::editCredentials, showExistingAccountHint = true, verified = otp.verified)
        else AuthSignUpForm(form, state is AuthUiState.Loading, (state as? AuthUiState.Error)?.message,
            viewModel::updateEmail, viewModel::updatePassword, viewModel::updateConfirmPassword, viewModel::updateName, viewModel::updateTermsAccepted,
            viewModel::sendOtp, onLogin = { navController.navigate(EmailLoginDestination) { popUpTo<SignUpDestination> { inclusive = true }; launchSingleTop = true } })
    }
}
