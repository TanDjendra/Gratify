package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tan.gratify.ui.component.OtpVerificationSection
import com.tan.gratify.ui.component.RippleIconButton
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.ui.navigation.destination.login.ForgotPasswordDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.EmailLoginViewModel
import com.tan.gratify.viewModel.auth.AuthUiState
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val AntigravityGreen = Color(0xFFE0E0E0)
private val BackgroundBlack = Color(0xFF000000)
private val LightGray = Color(0xFFE0E0E0)
private val DarkGray = Color(0xFF2E2E2E)
private val ErrorRed = Color(0xFFFF5252)

/**
 * EmailLoginScreen — Layar login menggunakan email + password.
 *
 * 2 halaman (HorizontalPager):
 * 1. Email + Password input
 * 2. OTP Verification (hanya jika email belum dikonfirmasi)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailLoginScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: EmailLoginViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit = {},
    showBottomNavigation: () -> Unit = {},
) {
    val authState by viewModel.authState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val otpState by viewModel.otpState.collectAsState()

    // 2 pages: Credentials → OTP Verification (fallback)
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    // ── React to auth state changes ──────────────────────────────────────────
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthUiState.VerificationPending -> {
                // Pindah ke halaman OTP
                if (pagerState.currentPage != 1) {
                    pagerState.animateScrollToPage(1)
                }
            }

            is AuthUiState.Authenticated -> {
                viewModel.resetToIdle()
                if (state.needsProfile) {
                    navController.navigate(CreateProfileDestination) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(MainDestination) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            else -> { /* Idle, Loading, Error — no navigation */ }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Log In",
                        style = typo().titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    Box(Modifier.padding(horizontal = 5.dp)) {
                        RippleIconButton(
                            Res.drawable.baseline_arrow_back_ios_new_24,
                            Modifier.size(32.dp),
                            true,
                        ) {
                            if (pagerState.currentPage > 0 && authState !is AuthUiState.VerificationPending) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            } else {
                                showBottomNavigation()
                                navController.navigateUp()
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundBlack,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = BackgroundBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> LoginCredentialsStep(
                        email = formState.email,
                        password = formState.password,
                        isEmailValid = formState.isEmailValid,
                        isPasswordValid = formState.isPasswordValid,
                        errorMessage = (authState as? AuthUiState.Error)?.message,
                        isLoading = authState is AuthUiState.Loading,
                        onEmailChanged = { viewModel.updateEmail(it) },
                        onPasswordChanged = { viewModel.updatePassword(it) },
                        onLoginClick = { viewModel.signIn() },
                        onForgotPasswordClick = {
                            navController.navigate(ForgotPasswordDestination)
                        },
                    )

                    1 -> {
                        // OTP Verification (fallback untuk email belum dikonfirmasi)
                        val pendingEmail = (authState as? AuthUiState.VerificationPending)?.email
                            ?: formState.email

                        OtpVerificationSection(
                            email = pendingEmail,
                            otpCode = otpState.code,
                            onOtpCodeChanged = { viewModel.updateOtpCode(it) },
                            onVerifyClick = { viewModel.verifyOtp() },
                            onResendClick = { viewModel.resendOtp() },
                            isLoading = authState is AuthUiState.Loading,
                            isResending = otpState.isResending,
                            resendCooldownSeconds = otpState.resendCooldownSeconds,
                        )
                    }
                }
            }
        }
    }
}


// ── Login Credentials Step ────────────────────────────────────────────────────
@Composable
private fun LoginCredentialsStep(
    email: String,
    password: String,
    isEmailValid: Boolean,
    isPasswordValid: Boolean,
    errorMessage: String?,
    isLoading: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isFormValid = isEmailValid && isPasswordValid

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Title ────────────────────────────────────────────────────────────
        Text(
            text = "Log in to your account",
            style = typo().titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )

        Spacer(Modifier.height(24.dp))

        // ── Email Field ──────────────────────────────────────────────────────
        Text(
            text = "Email",
            style = typo().bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            placeholder = {
                Text("Email", style = typo().bodyMedium.copy(color = Color.Gray))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LightGray,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AntigravityGreen
            ),
            isError = !isEmailValid && email.isNotEmpty(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
        )

        if (!isEmailValid && email.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Masukkan alamat email yang valid.",
                style = typo().bodySmall.copy(color = ErrorRed)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Password Field ───────────────────────────────────────────────────
        Text(
            text = "Password",
            style = typo().bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            placeholder = {
                Text("Password", style = typo().bodyMedium.copy(color = Color.Gray))
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = Color.Gray
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LightGray,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AntigravityGreen
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isFormValid && !isLoading) onLoginClick() }
            )
        )

        Spacer(Modifier.height(8.dp))

        // ── Error Message ────────────────────────────────────────────────────
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = typo().bodySmall.copy(color = ErrorRed)
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Forgot Password Link ─────────────────────────────────────────────
        TextButton(
            onClick = onForgotPasswordClick,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                text = "Lupa password?",
                style = typo().bodySmall.copy(
                    color = AntigravityGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Login Button ─────────────────────────────────────────────────────
        Button(
            onClick = onLoginClick,
            enabled = isFormValid && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityGreen,
                disabledContainerColor = DarkGray,
                contentColor = Color.Black,
                disabledContentColor = Color.Gray
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Log In",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
