package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.ui.component.LegalDocumentLinks
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.auth.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun authTypo(): Typography = remember {
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    Typography(headlineMedium = style(28, 34, FontWeight.Bold), headlineLarge = style(32, 38, FontWeight.Bold),
        titleMedium = style(17, 24, FontWeight.Bold), bodyLarge = style(16, 24), bodyMedium = style(14, 20),
        bodySmall = style(12, 18), labelLarge = style(15, 20, FontWeight.Bold))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthPage(title: String, innerPadding: PaddingValues = PaddingValues(), onBack: () -> Unit,
    branded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = GratifyColors.Background, topBar = {
        TopAppBar(title = { if (!branded) Text(title, style = authTypo().titleMedium) }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back))
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = GratifyColors.Background))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(innerPadding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = if (branded) 400.dp else 360.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

/** Center the welcome group in the available screen; keep scrolling for short screens or large text. */
@Composable
internal fun AuthLandingPage(
    innerPadding: PaddingValues = PaddingValues(),
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(innerPadding).safeDrawingPadding()) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp).size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back), tint = GratifyColors.TextPrimary)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val viewportHeight = maxHeight
            Column(
                Modifier.widthIn(max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(horizontal = 24.dp, vertical = if (largeText) 16.dp else 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
        LegalDocumentLinks(showContact = false, compact = true, showTerms = false, subtle = true)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AuthBrand(size: androidx.compose.ui.unit.Dp = 64.dp) {
    androidx.compose.foundation.Image(painterResource(Res.drawable.mono), null,
        Modifier.size(size), colorFilter = ColorFilter.tint(GratifyColors.Accent))
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GratifyColors.TextPrimary,
    unfocusedBorderColor = Color(0xFF7C7C7C),
    focusedContainerColor = GratifyColors.Background,
    unfocusedContainerColor = GratifyColors.Background,
    cursorColor = GratifyColors.TextPrimary,
)

@Composable
internal fun AuthHeading(title: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = authTypo().headlineMedium, fontWeight = FontWeight.Bold, color = GratifyColors.TextPrimary, modifier = Modifier.semantics { heading() })
        Text(hint, style = authTypo().bodyMedium, color = GratifyColors.TextSecondary)
    }
}

@Composable
internal fun AuthError(message: String?) {
    if (!message.isNullOrBlank()) Text(message, style = authTypo().bodyMedium, color = GratifyColors.Error,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun AuthEmailField(value: String, valid: Boolean, busy: Boolean, onChange: (String) -> Unit, onSubmit: (() -> Unit)? = null) {
    val label = stringResource(Res.string.auth_email)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, style = authTypo().labelLarge)
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = label }, enabled = !busy, singleLine = true,
        textStyle = authTypo().bodyLarge, shape = RoundedCornerShape(4.dp), colors = authFieldColors(),
        placeholder = { Text("name@email.com", style = authTypo().bodyLarge, color = GratifyColors.TextSecondary) }, isError = value.isNotEmpty() && !valid,
        supportingText = if (value.isNotEmpty() && !valid) { { Text(stringResource(Res.string.auth_email_invalid), style = authTypo().bodySmall) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (onSubmit == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (valid && !busy) onSubmit?.invoke() }))
    }
}

@Composable
internal fun AuthPasswordField(label: String, value: String, busy: Boolean, onChange: (String) -> Unit, error: String? = null,
    hint: String? = null, onSubmit: (() -> Unit)? = null) {
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, style = authTypo().labelLarge)
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = label }, enabled = !busy, singleLine = true,
        textStyle = authTypo().bodyLarge, shape = RoundedCornerShape(4.dp), colors = authFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = error != null, supportingText = (error ?: hint)?.let { text -> { Text(text, style = authTypo().bodySmall) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (onSubmit == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!busy) { focus.clearFocus(); onSubmit?.invoke() } }),
        trailingIcon = { IconButton(onClick = { visible = !visible }, enabled = !busy) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                stringResource(if (visible) Res.string.auth_hide_password else Res.string.auth_show_password))
        } })
    }
}

@Composable
internal fun AuthSubmit(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val focus = LocalFocusManager.current
    Button(onClick = { focus.clearFocus(); onClick() }, enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = GratifyColors.Accent, contentColor = GratifyColors.OnAccent,
            disabledContainerColor = if (busy) GratifyColors.AccentContainer else GratifyColors.SurfaceRaised,
            disabledContentColor = GratifyColors.TextSecondary),
        contentPadding = PaddingValues(16.dp)) {
        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), color = GratifyColors.Accent, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        Text(label, style = authTypo().labelLarge)
    }
}

@Composable
internal fun AuthLoginForm(form: LoginFormState, busy: Boolean, error: String?, onEmail: (String) -> Unit, onPassword: (String) -> Unit,
    onSubmit: () -> Unit, onForgot: () -> Unit, onSignUp: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        AuthBrand()
        Text(stringResource(Res.string.auth_login_title), style = authTypo().headlineLarge.copy(letterSpacing = (-0.8).sp),
            modifier = Modifier.fillMaxWidth().semantics { heading() }, textAlign = TextAlign.Center)
    }
    AuthEmailField(form.email, form.isEmailValid, busy, onEmail)
    AuthPasswordField(stringResource(Res.string.auth_password), form.password, busy, onPassword,
        onSubmit = { if (form.isValid()) onSubmit() })
    AuthError(error)
    AuthSubmit(stringResource(if (busy) Res.string.auth_signing_in else Res.string.auth_login), busy, form.isValid(), onSubmit)
    TextButton(onClick = onForgot, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(Res.string.auth_forgot_password), style = authTypo().bodyMedium,
            color = if (busy) GratifyColors.TextSecondary else GratifyColors.TextPrimary, textDecoration = TextDecoration.Underline)
    }
    HorizontalDivider(color = GratifyColors.Outline.copy(alpha = 0.5f))
    TextButton(onClick = onSignUp, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(Res.string.auth_no_account), style = authTypo().bodyMedium, textAlign = TextAlign.Center,
            color = if (busy) GratifyColors.TextSecondary else GratifyColors.TextPrimary, textDecoration = TextDecoration.Underline)
    }
}

@Composable
internal fun AuthSignUpForm(form: SignUpFormState, busy: Boolean, error: String?, onEmail: (String) -> Unit, onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit, onName: (String) -> Unit, onTerms: (Boolean) -> Unit, onSubmit: () -> Unit, onLogin: () -> Unit) {
    AuthHeading(stringResource(Res.string.auth_signup_title), stringResource(Res.string.auth_signup_hint))
    OutlinedTextField(form.name, onName, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
        label = { Text(stringResource(Res.string.auth_name)) }, isError = form.name.isNotEmpty() && !form.isNameValid,
        supportingText = { Text(stringResource(Res.string.auth_name_hint)) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
    AuthEmailField(form.email, form.isEmailValid, busy, onEmail)
    AuthPasswordField(stringResource(Res.string.auth_password), form.password, busy, onPassword, hint = stringResource(Res.string.auth_password_hint))
    AuthPasswordField(stringResource(Res.string.auth_confirm_password), form.confirmPassword, busy, onConfirm,
        error = if (form.confirmPassword.isNotEmpty() && !form.isConfirmPasswordMatch) stringResource(Res.string.auth_password_mismatch) else null)
    Row(Modifier.fillMaxWidth().toggleable(form.isTermsAccepted, enabled = !busy, role = Role.Checkbox, onValueChange = onTerms).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(form.isTermsAccepted, onCheckedChange = null)
        Text(stringResource(Res.string.auth_terms_accept), modifier = Modifier.weight(1f), style = authTypo().bodySmall, color = GratifyColors.TextSecondary)
    }
    Text(stringResource(Res.string.auth_terms_required), style = authTypo().bodySmall, color = GratifyColors.TextSecondary)
    LegalDocumentLinks()
    AuthError(error)
    AuthSubmit(stringResource(if (busy) Res.string.auth_creating_account else Res.string.auth_submit_signup), busy, form.isSignUpReady(), onSubmit)
    TextButton(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(Res.string.auth_has_account)) }
}

@Composable
internal fun AuthResetForm(form: ResetPasswordFormState, busy: Boolean, error: String?, onNew: (String) -> Unit, onConfirm: (String) -> Unit, onSave: () -> Unit) {
    AuthHeading(stringResource(Res.string.auth_reset_title), stringResource(Res.string.auth_reset_new_hint))
    AuthPasswordField(stringResource(Res.string.auth_new_password), form.newPassword, busy, onNew, hint = stringResource(Res.string.auth_password_hint))
    AuthPasswordField(stringResource(Res.string.auth_confirm_password), form.confirmPassword, busy, onConfirm,
        error = if (form.confirmPassword.isNotEmpty() && !form.isConfirmPasswordMatch) stringResource(Res.string.auth_password_mismatch) else null,
        onSubmit = { if (form.isValid()) onSave() })
    AuthError(error)
    AuthSubmit(stringResource(if (busy) Res.string.auth_saving_password else Res.string.auth_save_password), busy, form.isValid(), onSave)
}

@Composable
internal fun AuthLandingContent(waiting: Boolean, error: String?, onLogin: () -> Unit, onSignUp: () -> Unit, onGoogle: () -> Unit, onCancel: () -> Unit, finishing: Boolean = false,
    connectYouTube: Boolean = false, onConnectYouTubeChange: (Boolean) -> Unit = {}) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val accountLabel = stringResource(Res.string.auth_has_account)
    val loginLabel = stringResource(Res.string.auth_login)
    val actionStart = accountLabel.lastIndexOf(loginLabel, ignoreCase = true)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthBrand(if (largeText) 56.dp else 64.dp)
        Spacer(Modifier.height(if (largeText) 20.dp else 28.dp))
        Text(stringResource(Res.string.auth_welcome_headline), style = authTypo().headlineLarge.copy(
            fontSize = if (largeText) 28.sp else 34.sp, lineHeight = if (largeText) 34.sp else 40.sp,
            letterSpacing = (-1).sp, fontWeight = FontWeight.ExtraBold),
            textAlign = TextAlign.Center, color = GratifyColors.TextPrimary, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Spacer(Modifier.height(if (largeText) 28.dp else 40.dp))
        AuthError(error)
        if (!error.isNullOrBlank()) Spacer(Modifier.height(12.dp))
        AuthSubmit(stringResource(if (finishing) Res.string.auth_signing_in else Res.string.auth_signup_free), finishing, !waiting, onSignUp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onGoogle, enabled = !waiting && !finishing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape,
            border = BorderStroke(1.dp, if (waiting || finishing) GratifyColors.Outline else Color(0xFF7C7C7C)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GratifyColors.TextPrimary), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
            androidx.compose.foundation.Image(painterResource(Res.drawable.ic_google), null, Modifier.size(20.dp))
            Text(stringResource(if (waiting) Res.string.auth_google_wait else Res.string.auth_google),
                style = authTypo().labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Spacer(Modifier.size(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(connectYouTube, enabled = !waiting && !finishing,
            role = Role.Checkbox, onValueChange = onConnectYouTubeChange).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(connectYouTube, onCheckedChange = null, enabled = !waiting && !finishing)
            Text(stringResource(Res.string.auth_google_youtube_option), modifier = Modifier.weight(1f), style = authTypo().bodySmall,
                color = GratifyColors.TextSecondary)
        }
        if (connectYouTube) Text(stringResource(Res.string.auth_google_youtube_short_hint), style = authTypo().bodySmall,
            color = GratifyColors.TextSecondary, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        if (waiting) {
            Text(stringResource(Res.string.auth_google_hint), style = authTypo().bodySmall, textAlign = TextAlign.Center, color = GratifyColors.TextSecondary)
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.cancel)) }
        }
        Spacer(Modifier.height(if (largeText) 16.dp else 24.dp))
        HorizontalDivider(color = GratifyColors.Outline.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onLogin, enabled = !waiting && !finishing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(buildAnnotatedString {
                if (actionStart >= 0) {
                    withStyle(SpanStyle(color = GratifyColors.TextSecondary)) { append(accountLabel.substring(0, actionStart)) }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append(accountLabel.substring(actionStart))
                    }
                } else append(accountLabel)
            }, style = authTypo().bodyMedium,
                color = if (waiting || finishing) GratifyColors.TextSecondary else GratifyColors.TextPrimary,
                textAlign = TextAlign.Center)
        }
    }
}
