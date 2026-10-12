package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.gratify.ui.component.LayerBackHandler
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.ForgotPasswordViewModel
import com.tan.gratify.viewModel.auth.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ForgotPasswordScreen(innerPadding: PaddingValues, navController: NavController, viewModel: ForgotPasswordViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit = {}, showBottomNavigation: () -> Unit = {}) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val reset by viewModel.resetFormState.collectAsStateWithLifecycle()
    val sentEmail by viewModel.sentEmail.collectAsStateWithLifecycle()
    val cooldown by viewModel.resendCooldown.collectAsStateWithLifecycle()
    val recoveryUser by PasswordRecoveryCoordinator.verifiedUserId.collectAsStateWithLifecycle()
    val back = {
        viewModel.leaveRecovery {
            showBottomNavigation()
            if (!navController.navigateUp()) navController.navigate(LoginLandingDestination) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
        }
    }
    LayerBackHandler(true, back)
    DisposableEffect(Unit) { hideBottomNavigation(); onDispose { showBottomNavigation() } }
    LaunchedEffect(recoveryUser) { if (recoveryUser != null) viewModel.markRecoveryReady() }
    val successMessage = stringResource(Res.string.auth_reset_success)
    LaunchedEffect(state) {
        if (state is AuthUiState.PasswordResetSuccess) {
            viewModel.makeToast(successMessage)
            viewModel.resetToIdle()
            navController.navigate(LoginLandingDestination) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
        }
    }
    val busy = state is AuthUiState.Loading
    AuthPage(stringResource(Res.string.auth_reset_title), innerPadding, back) {
        if (viewModel.recoveryReady) AuthResetForm(reset, busy, (state as? AuthUiState.Error)?.message,
            viewModel::updateNewPassword, viewModel::updateConfirmPassword, viewModel::setNewPassword)
        else if (sentEmail != null) {
            AuthHeading(stringResource(Res.string.auth_link_sent_title), stringResource(Res.string.auth_link_sent_hint))
            Text(sentEmail!!, style = typo().titleSmall, color = GratifyColors.Accent)
            AuthError((state as? AuthUiState.Error)?.message)
            AuthSubmit(if (cooldown > 0) stringResource(Res.string.auth_resend_countdown, cooldown) else stringResource(Res.string.auth_send_link_again),
                busy, cooldown == 0, viewModel::sendResetLink)
            TextButton(onClick = viewModel::editEmail, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(Res.string.auth_change_email)) }
        } else {
            AuthHeading(stringResource(Res.string.auth_reset_title), stringResource(Res.string.auth_reset_hint))
            AuthEmailField(form.email, form.isEmailValid, busy, viewModel::updateEmail, viewModel::sendResetLink)
            AuthError((state as? AuthUiState.Error)?.message)
            AuthSubmit(if (cooldown > 0) stringResource(Res.string.auth_resend_countdown, cooldown) else stringResource(Res.string.auth_send_link),
                busy, form.isEmailValid && cooldown == 0, viewModel::sendResetLink)
        }
    }
}
