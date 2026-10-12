package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.screen.login.AuthError
import com.tan.gratify.ui.screen.login.AuthHeading
import com.tan.gratify.ui.screen.login.AuthSubmit
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.auth.normalizeOtpCode
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun OtpVerificationSection(email: String, otpCode: String, onOtpCodeChanged: (String) -> Unit, onVerifyClick: () -> Unit,
    onResendClick: () -> Unit, isLoading: Boolean = false, isResending: Boolean = false, resendCooldownSeconds: Int = 0,
    errorMessage: String? = null, modifier: Modifier = Modifier, statusMessage: String? = null, onChangeEmail: (() -> Unit)? = null,
    showExistingAccountHint: Boolean = false, verified: Boolean = false) {
    val busy = isLoading || isResending
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AuthHeading(stringResource(Res.string.auth_verify_title), stringResource(Res.string.auth_verify_hint))
        Text(email, color = GratifyColors.Accent, style = typo().titleSmall)
        if (!verified) OutlinedTextField(otpCode, { onOtpCodeChanged(normalizeOtpCode(it)) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
            label = { Text(stringResource(Res.string.auth_otp_label)) }, isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (otpCode.length == 6 && !busy) onVerifyClick() }))
        AuthError(errorMessage)
        if (statusMessage != null) Text(statusMessage, color = GratifyColors.TextSecondary, style = typo().bodySmall)
        AuthSubmit(stringResource(if (isLoading) Res.string.auth_verifying else if (verified) Res.string.auth_finish_account else Res.string.auth_verify), isLoading,
            (verified || otpCode.length == 6 && otpCode.all { it in '0'..'9' }) && !isResending, onVerifyClick)
        if (!verified) TextButton(onClick = onResendClick, enabled = !busy && resendCooldownSeconds == 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (resendCooldownSeconds > 0) stringResource(Res.string.auth_resend_countdown, resendCooldownSeconds) else stringResource(Res.string.auth_resend))
        }
        if (showExistingAccountHint) Text(stringResource(Res.string.auth_inbox_hint), style = typo().bodySmall, color = GratifyColors.TextSecondary)
        if (!verified) Text(stringResource(Res.string.auth_confirmation_link_hint), style = typo().bodySmall, color = GratifyColors.TextSecondary)
        if (onChangeEmail != null) TextButton(onClick = onChangeEmail, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.auth_change_email))
        }
    }
}
