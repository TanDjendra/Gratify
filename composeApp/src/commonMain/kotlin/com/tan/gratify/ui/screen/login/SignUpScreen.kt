package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tan.gratify.ui.component.OtpVerificationSection
import com.tan.gratify.ui.component.RippleIconButton
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.SignUpViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: SignUpViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit = {},
    showBottomNavigation: () -> Unit = {},
) {
    val authState by viewModel.authState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val otpState by viewModel.otpState.collectAsState()

    // 4 pages: Email → Password → Name → OTP Verification
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    // ── React to auth state changes ──────────────────────────────────────────
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthUiState.VerificationPending -> {
                // Animasi ke halaman OTP
                if (pagerState.currentPage != 3) {
                    pagerState.animateScrollToPage(3)
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

            is AuthUiState.Error -> {
                // Jika error terkait duplicate email, kembali ke halaman email
                if (pagerState.currentPage != 0 && !authState.let { it is AuthUiState.Error && (it as AuthUiState.Error).message.contains("terdaftar", ignoreCase = true) }) {
                    // Tetap di halaman saat ini
                } else if (pagerState.currentPage != 0) {
                    pagerState.animateScrollToPage(0)
                }
            }

            else -> { /* Idle, Loading — no navigation */ }
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
                        text = "Create account",
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
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
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
                    0 -> EmailStep(
                        email = formState.email,
                        isValid = formState.isEmailValid,
                        errorMessage = (authState as? AuthUiState.Error)?.message,
                        isLoading = authState is AuthUiState.Loading,
                        onEmailChanged = { viewModel.updateEmail(it) },
                        onNext = {
                            viewModel.proceedWithEmail {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        },
                    )

                    1 -> PasswordStep(
                        password = formState.password,
                        isValid = formState.isPasswordValid,
                        onPasswordChanged = { viewModel.updatePassword(it) },
                        onNext = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        }
                    )

                    2 -> NameStep(
                        name = formState.name,
                        isValid = formState.isNameValid,
                        isTermsAccepted = formState.isTermsAccepted,
                        isLoading = authState is AuthUiState.Loading,
                        onNameChanged = { viewModel.updateName(it) },
                        onTermsChanged = { viewModel.updateTermsAccepted(it) },
                        onCreateAccount = { viewModel.sendOtp() }
                    )

                    3 -> {
                        // OTP Verification Step
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


// ── Step 1: Email ─────────────────────────────────────────────────────────────
@Composable
private fun EmailStep(
    email: String,
    isValid: Boolean,
    errorMessage: String?,
    isLoading: Boolean = false,
    onEmailChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "What's your email?",
            style = typo().titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LightGray,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AntigravityGreen
            ),
            isError = !isValid && email.isNotEmpty() || errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (isValid && !isLoading) onNext() }
            )
        )
        Spacer(Modifier.height(8.dp))

        // Error message atau hint
        val displayMessage = errorMessage
            ?: if (email.isEmpty()) "You'll need to confirm this email later."
            else if (!isValid) "Please enter a valid email address."
            else ""
        val messageColor = if ((!isValid && email.isNotEmpty()) || errorMessage != null) ErrorRed else Color.Gray

        Text(
            text = displayMessage,
            style = typo().bodySmall.copy(color = messageColor)
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = isValid && !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityGreen,
                disabledContainerColor = DarkGray,
                contentColor = Color.Black,
                disabledContentColor = Color.Gray
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (isLoading) "Memeriksa..." else "Next",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Step 2: Password ──────────────────────────────────────────────────────────
@Composable
private fun PasswordStep(
    password: String,
    isValid: Boolean,
    onPasswordChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Create a password",
            style = typo().titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
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
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (isValid) onNext() }
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Use at least 8 characters.",
            style = typo().bodySmall.copy(color = if (!isValid && password.isNotEmpty()) ErrorRed else Color.Gray)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = isValid,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityGreen,
                disabledContainerColor = DarkGray,
                contentColor = Color.Black,
                disabledContentColor = Color.Gray
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = "Next", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Step 3: Nama & Syarat Ketentuan ──────────────────────────────────────────
@Composable
private fun NameStep(
    name: String,
    isValid: Boolean,
    isTermsAccepted: Boolean,
    isLoading: Boolean = false,
    onNameChanged: (String) -> Unit,
    onTermsChanged: (Boolean) -> Unit,
    onCreateAccount: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "What's your name?",
            style = typo().titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LightGray,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AntigravityGreen
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isValid && isTermsAccepted && !isLoading) onCreateAccount() }
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This appears on your profile.",
            style = typo().bodySmall.copy(color = Color.Gray)
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isTermsAccepted,
                onCheckedChange = onTermsChanged,
                colors = CheckboxDefaults.colors(
                    checkedColor = AntigravityGreen,
                    uncheckedColor = DarkGray,
                    checkmarkColor = Color.Black
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "I agree to the Terms of Use and Privacy Policy.",
                style = typo().bodySmall.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onCreateAccount,
            enabled = isValid && isTermsAccepted && !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityGreen,
                disabledContainerColor = DarkGray,
                contentColor = Color.Black,
                disabledContentColor = Color.Gray
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (isLoading) "Mendaftarkan..." else "Create account",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
