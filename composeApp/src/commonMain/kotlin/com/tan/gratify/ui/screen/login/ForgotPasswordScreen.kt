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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tan.gratify.ui.component.RippleIconButton
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.ForgotPasswordViewModel
import com.tan.gratify.viewModel.auth.AuthUiState
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import org.koin.compose.viewmodel.koinViewModel

private val AntigravityGreen = Color(0xFFE0E0E0)
private val BackgroundBlack = Color(0xFF000000)
private val LightGray = Color(0xFFE0E0E0)
private val DarkGray = Color(0xFF2E2E2E)
private val ErrorRed = Color(0xFFFF5252)

/**
 * ForgotPasswordScreen — Layar untuk reset password via magic link.
 *
 * Alur:
 * 1. User masukkan email → kirim magic link
 * 2. Tampilkan pesan sukses (cek email)
 * 3. Jika deep link recovery kembali → tampilkan form password baru
 * 4. Password berhasil direset → navigasi kembali
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit = {},
    showBottomNavigation: () -> Unit = {},
) {
    val authState by viewModel.authState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val resetFormState by viewModel.resetFormState.collectAsState()

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    // Navigasi setelah password berhasil direset
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.PasswordResetSuccess -> {
                viewModel.makeToast("Password berhasil diubah! Silakan login.")
                viewModel.resetToIdle()
                navController.navigateUp()
            }
            else -> {}
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
                        text = "Reset Password",
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
                            viewModel.resetToIdle()
                            showBottomNavigation()
                            navController.navigateUp()
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
        ) {
            when (authState) {
                is AuthUiState.Idle,
                is AuthUiState.Loading,
                is AuthUiState.Error -> {
                    // Step 1: Masukkan email untuk kirim magic link
                    SendResetLinkStep(
                        email = formState.email,
                        isEmailValid = formState.isEmailValid,
                        isLoading = authState is AuthUiState.Loading,
                        errorMessage = (authState as? AuthUiState.Error)?.message,
                        onEmailChanged = { viewModel.updateEmail(it) },
                        onSendClick = { viewModel.sendResetLink() },
                    )
                }

                is AuthUiState.PasswordResetSent -> {
                    // Step 2: Pesan sukses — cek email
                    ResetLinkSentStep(
                        email = (authState as AuthUiState.PasswordResetSent).email,
                        onResendClick = { viewModel.sendResetLink() },
                    )
                }

                is AuthUiState.PasswordResetReady -> {
                    // Step 3: Masukkan password baru
                    SetNewPasswordStep(
                        newPassword = resetFormState.newPassword,
                        confirmPassword = resetFormState.confirmPassword,
                        isNewPasswordValid = resetFormState.isNewPasswordValid,
                        isConfirmMatch = resetFormState.isConfirmPasswordMatch,
                        isLoading = false,
                        onNewPasswordChanged = { viewModel.updateNewPassword(it) },
                        onConfirmPasswordChanged = { viewModel.updateConfirmPassword(it) },
                        onSaveClick = { viewModel.setNewPassword() },
                    )
                }

                else -> {}
            }
        }
    }
}


// ── Step 1: Send Reset Link ──────────────────────────────────────────────────
@Composable
private fun SendResetLinkStep(
    email: String,
    isEmailValid: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChanged: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Reset password Anda",
            style = typo().titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Masukkan email yang terdaftar. Kami akan mengirim link untuk mengatur ulang password Anda.",
            style = typo().bodyMedium.copy(color = Color.Gray)
        )

        Spacer(Modifier.height(24.dp))

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
                cursorColor = AntigravityGreen,
            ),
            isError = !isEmailValid && email.isNotEmpty() || errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isEmailValid && !isLoading) onSendClick() }
            )
        )

        Spacer(Modifier.height(8.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = typo().bodySmall.copy(color = ErrorRed)
            )
        } else if (!isEmailValid && email.isNotEmpty()) {
            Text(
                text = "Masukkan alamat email yang valid.",
                style = typo().bodySmall.copy(color = ErrorRed)
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onSendClick,
            enabled = isEmailValid && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityGreen,
                disabledContainerColor = DarkGray,
                contentColor = Color.Black,
                disabledContentColor = Color.Gray,
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
                    text = "Kirim Link Reset",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}


// ── Step 2: Reset Link Sent ──────────────────────────────────────────────────
@Composable
private fun ResetLinkSentStep(
    email: String,
    onResendClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "📧",
            fontSize = 56.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Cek email Anda",
            style = typo().titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Kami telah mengirim link reset password ke",
            style = typo().bodyMedium.copy(color = Color.Gray),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = email,
            style = typo().bodyMedium.copy(
                color = AntigravityGreen,
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Klik link di email untuk mengatur ulang password Anda. Cek juga folder spam.",
            style = typo().bodySmall.copy(
                color = Color.Gray.copy(alpha = 0.7f),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onResendClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkGray,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "Kirim Ulang Link",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}


// ── Step 3: Set New Password ─────────────────────────────────────────────────
@Composable
private fun SetNewPasswordStep(
    newPassword: String,
    confirmPassword: String,
    isNewPasswordValid: Boolean,
    isConfirmMatch: Boolean,
    isLoading: Boolean,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Buat password baru",
            style = typo().titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Password baru harus berbeda dari password sebelumnya.",
            style = typo().bodyMedium.copy(color = Color.Gray)
        )

        Spacer(Modifier.height(24.dp))

        // ── New Password ─────────────────────────────────────────────────────
        Text(
            text = "Password Baru",
            style = typo().bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = Color.Gray,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LightGray,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AntigravityGreen,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Minimal 8 karakter.",
            style = typo().bodySmall.copy(
                color = if (!isNewPasswordValid && newPassword.isNotEmpty()) ErrorRed else Color.Gray
            )
        )

        Spacer(Modifier.height(16.dp))

        // ── Confirm Password ─────────────────────────────────────────────────
        Text(
            text = "Konfirmasi Password",
            style = typo().bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(
                        imageVector = if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = Color.Gray,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LightGray,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AntigravityGreen,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isConfirmMatch && !isLoading) onSaveClick() }
            ),
        )
        if (confirmPassword.isNotEmpty() && !isConfirmMatch) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Password tidak cocok.",
                style = typo().bodySmall.copy(color = ErrorRed)
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onSaveClick,
            enabled = isConfirmMatch && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityGreen,
                disabledContainerColor = DarkGray,
                contentColor = Color.Black,
                disabledContentColor = Color.Gray,
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
                    text = "Simpan Password Baru",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
