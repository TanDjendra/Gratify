package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.EmailLoginDestination
import com.tan.gratify.ui.navigation.destination.login.SignUpDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.ui.theme.typo
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.app_icon
import gratify.composeapp.generated.resources.ic_google
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import io.github.jan.supabase.auth.auth

// Konstanta warna sesuai desain premium
private val AntigravityGreen = Color(0xFFE0E0E0)
private val ButtonOutlineColor = Color(0xFF727272)
private val BackgroundBlack = Color(0xFF000000)

/**
 * Halaman awal Login / Landing Page untuk aplikasi musik Antigravity.
 * Menggunakan Jetpack Navigation Compose (navigasi bawaan proyek saat ini).
 * Didesain modular agar mudah diintegrasikan dengan sistem navigasi lain seperti Voyager jika dibutuhkan.
 */
@Composable
fun LoginLandingScreen(
    navController: NavController,
    innerPadding: PaddingValues = PaddingValues(),
    hideBottomNavigation: () -> Unit = {},
    showBottomNavigation: () -> Unit = {},
    signUpViewModel: com.tan.gratify.viewModel.SignUpViewModel = koinViewModel(),
    // Callback navigasi agar modular & reusable (bisa diintegrasikan dengan Voyager/lainnya)
    onSignUpClick: () -> Unit = { navController.navigate(SignUpDestination) },
    onEmailLoginClick: () -> Unit = { navController.navigate(EmailLoginDestination) },
    onGoogleClick: () -> Unit = { signUpViewModel.logInWithGoogle() },
) {
    val authState by signUpViewModel.authState.collectAsStateWithLifecycle()
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    val dataStoreManager: DataStoreManager = koinInject()

    // Observasi state login dari ViewModel (Email/OTP + Google OAuth)
    LaunchedEffect(authState) {
        if (hasNavigated) return@LaunchedEffect
        when (val state = authState) {
            is com.tan.gratify.viewModel.auth.AuthUiState.Authenticated -> {
                hasNavigated = true
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
            is com.tan.gratify.viewModel.auth.AuthUiState.Error -> {
                signUpViewModel.makeToast(state.message)
                signUpViewModel.resetToIdle()
            }
            else -> {}
        }
    }

    // Observasi sessionStatus dari Supabase Auth SDK untuk deep link OAuth callback.
    val supabase: io.github.jan.supabase.SupabaseClient = koinInject()
    val userRepository: com.tan.domain.repository.UserRepository = koinInject()
    val artistRepository: com.tan.domain.repository.ArtistRepository = koinInject()
    val userDataSyncManager: com.tan.data.sync.UserDataSyncManager = koinInject()
    LaunchedEffect(Unit) {
        supabase.auth.sessionStatus.collect { status ->
            if (hasNavigated) return@collect
            when (status) {
                is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> {
                    val user = supabase.auth.currentUserOrNull() ?: return@collect
                    try {
                        dataStoreManager.clearPerUserData()
                        dataStoreManager.setLoggedIn(true)
                        dataStoreManager.putString("AccountEmail", user.email ?: "")

                        val existingProfile = try {
                            userRepository.getUserProfile(user.id).firstOrNull()
                        } catch (_: Exception) { null }

                        if (existingProfile != null) {
                            if (!existingProfile.displayName.isNullOrEmpty()) {
                                dataStoreManager.putString("AccountName", existingProfile.displayName!!)
                                dataStoreManager.putString("AppProfileName", existingProfile.displayName!!)
                            }
                            if (!existingProfile.avatarUrl.isNullOrEmpty()) {
                                dataStoreManager.putString("AppProfileImage", existingProfile.avatarUrl!!)
                            }
                            if (!existingProfile.topArtists.isNullOrEmpty()) {
                                existingProfile.topArtists!!.forEach { topArtist ->
                                    try {
                                        artistRepository.insertArtist(
                                            com.tan.domain.data.entities.ArtistEntity(
                                                channelId = topArtist.channelId,
                                                name = topArtist.name,
                                                thumbnails = topArtist.thumbnails,
                                                followed = true
                                            )
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                        } else {
                            val profileName = user.userMetadata
                                ?.get("display_name")
                                ?.toString()
                                ?.replace("\"", "")
                                ?: user.userMetadata
                                    ?.get("full_name")
                                    ?.toString()
                                    ?.replace("\"", "")
                                ?: user.userMetadata
                                    ?.get("name")
                                    ?.toString()
                                    ?.replace("\"", "")
                                ?: ""
                            if (profileName.isNotEmpty()) {
                                dataStoreManager.putString("AccountName", profileName)
                                dataStoreManager.putString("AppProfileName", profileName)
                            }
                            val profilePic = user.userMetadata
                                ?.get("avatar_url")
                                ?.toString()
                                ?.replace("\"", "")
                                ?: user.userMetadata?.get("picture")?.toString()?.replace("\"", "")
                                ?: ""
                            if (profilePic.isNotEmpty()) {
                                dataStoreManager.putString("AppProfileImage", profilePic)
                            }
                        }

                        // Pulihkan data akun ini dari cloud + jaga isolasi per-akun:
                        // bila akun yang login via Google berbeda dari akun sebelumnya,
                        // performLoginSync membersihkan DB akun lama dulu baru menarik data akun baru.
                        userDataSyncManager.performLoginSync()

                        val appProfileName = dataStoreManager.getString("AppProfileName").first()
                        val destination = if (appProfileName.isNullOrEmpty()) {
                            CreateProfileDestination
                        } else {
                            MainDestination
                        }
                        hasNavigated = true
                        navController.navigate(destination) {
                            popUpTo(0) { inclusive = true }
                        }
                    } catch (e: Exception) {
                        hasNavigated = false
                        signUpViewModel.makeToast("Login gagal. Silakan coba lagi.")
                        signUpViewModel.resetToIdle()
                    }
                }
                else -> {}
            }
        }
    }


    DisposableEffect(Unit) {
        onDispose {
            showBottomNavigation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── Logo Aplikasi ────────────────────────────────────────────────
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = "Antigravity Logo",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.height(28.dp))

            // ── Judul Utama (Sesuai Permintaan: Antigravity) ─────────────────
            Text(
                text = "Millions of songs.\nFree on Antigravity.",
                style = typo().titleLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            // ── Tombol Utama: Sign up free (Hijau Brand) ─────────────────────
            Button(
                onClick = onSignUpClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntigravityGreen,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = "Sign up free",
                    style = typo().titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    ),
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Tombol Log In (Email + Password) ─────────────────────────────
            OutlinedButton(
                onClick = onEmailLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, ButtonOutlineColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Log In",
                    style = typo().titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Tombol Google ────────────────────────────────────────────────
            SocialLoginButton(
                iconRes = Res.drawable.ic_google,
                iconTint = null,
                label = "Continue with Google",
                onClick = onGoogleClick,
            )


            // Catatan: Tombol "Continue with Apple" telah dihapus sepenuhnya sesuai permintaan

        }
    }
}

// ── Komponen Reusable Tombol Social Login dengan Border Bulat ────────────────
@Composable
private fun SocialLoginButton(
    iconRes: DrawableResource,
    iconTint: Color?,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, ButtonOutlineColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconTint != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(iconTint),
                )
            } else {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = label,
                style = typo().titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                ),
                modifier = Modifier.weight(6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(22.dp))
        }
    }
}
