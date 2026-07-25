package com.tan.gratify.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.ui.theme.typo

private val AntigravityGreen = Color(0xFFE0E0E0)
private val BackgroundBlack = Color(0xFF000000)
private val DarkGray = Color(0xFF2E2E2E)
private val LightGray = Color(0xFFE0E0E0)
private val ErrorRed = Color(0xFFFF5252)

/**
 * Komponen OTP verification yang dapat digunakan ulang
 * di SignUpScreen maupun EmailLoginScreen.
 *
 * Menampilkan:
 * - Header teks dengan email tujuan
 * - 6-digit OTP input boxes
 * - Tombol "Verifikasi" (enabled saat 6 digit diisi)
 * - Tombol "Kirim Ulang Kode" dengan cooldown timer
 * - Loading indicator saat verifikasi
 */
@Composable
fun OtpVerificationSection(
    email: String,
    otpCode: String,
    onOtpCodeChanged: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onResendClick: () -> Unit,
    isLoading: Boolean = false,
    isResending: Boolean = false,
    resendCooldownSeconds: Int = 0,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Text(
            text = "Verifikasi Email",
            style = typo().titleLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Masukkan kode 6 digit yang telah dikirim ke",
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

        Spacer(Modifier.height(32.dp))

        // ── OTP Input Boxes ──────────────────────────────────────────────────
        BasicTextField(
            value = otpCode,
            onValueChange = onOtpCodeChanged,
            modifier = Modifier.focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            decorationBox = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(6) { index ->
                        val char = otpCode.getOrNull(index)?.toString() ?: ""
                        val isFocused = otpCode.length == index

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkGray)
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        isFocused -> AntigravityGreen
                                        char.isNotEmpty() -> LightGray.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = char,
                                style = typo().titleLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }

                        if (index < 5) {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            },
        )

        // ── Error Message ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = typo().bodySmall.copy(
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Verify Button ────────────────────────────────────────────────────
        Button(
            onClick = onVerifyClick,
            enabled = otpCode.length == 6 && !isLoading,
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
                    text = "Verifikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Resend Button ────────────────────────────────────────────────────
        if (resendCooldownSeconds > 0) {
            Text(
                text = "Kirim ulang kode dalam ${resendCooldownSeconds}s",
                style = typo().bodySmall.copy(color = Color.Gray),
                textAlign = TextAlign.Center,
            )
        } else {
            TextButton(
                onClick = onResendClick,
                enabled = !isResending,
            ) {
                if (isResending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AntigravityGreen,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "Kirim Ulang Kode",
                    style = typo().bodyMedium.copy(
                        color = AntigravityGreen,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Hint ─────────────────────────────────────────────────────────────
        Text(
            text = "Cek folder spam jika tidak menemukan email verifikasi.",
            style = typo().bodySmall.copy(
                color = Color.Gray.copy(alpha = 0.7f),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
