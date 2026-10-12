package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.utils.uploadedProfileAvatar
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.ui.navigation.destination.login.EmailLoginDestination
import com.tan.gratify.ui.navigation.destination.login.SignUpDestination
import com.tan.gratify.viewModel.auth.GoogleLoginState
import gratify.composeapp.generated.resources.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Halaman awal Login / Landing Page untuk aplikasi musik Gratify.
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
    onGoogleClick: (Boolean) -> Unit = { signUpViewModel.logInWithGoogle(it) },
) {
    val googleState by signUpViewModel.googleLoginState.collectAsStateWithLifecycle()
    val googleWaiting = googleState is GoogleLoginState.Waiting
    var connectYouTube by rememberSaveable { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf<String?>(null) }
    var completionRetry by remember { mutableStateOf(0) }
    val completionFailure = stringResource(Res.string.auth_request_failed)
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    val dataStoreManager: DataStoreManager = koinInject()

    // Observasi sessionStatus dari Supabase Auth SDK untuk deep link OAuth callback.
    val supabase: io.github.jan.supabase.SupabaseClient = koinInject()
    val userRepository: com.tan.domain.repository.UserRepository = koinInject()
    val artistRepository: com.tan.domain.repository.ArtistRepository = koinInject()
    val userDataSyncManager: com.tan.data.sync.UserDataSyncManager = koinInject()
    LaunchedEffect(completionRetry) {
        supabase.auth.sessionStatus.collect { status ->
            if (hasNavigated || com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator.pending.value) return@collect
            when (status) {
                is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> {
                    val user = supabase.auth.currentUserOrNull() ?: return@collect
                    try {
                        finishing = true
                        completionError = null
                        userDataSyncManager.performLoginSync(waitForCloud = false)
                        // Account-specific preferences are cleared before cloud restore by the sync manager.
                        dataStoreManager.putString("AccountEmail", user.email ?: "")

                        val existingProfile = try {
                            userRepository.getUserProfile(user.id).firstOrNull()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }

                        if (supabase.auth.currentUserOrNull()?.id != user.id) return@collect
                        if (existingProfile != null) {
                            if (!existingProfile.displayName.isNullOrEmpty()) {
                                dataStoreManager.putString("AccountName", existingProfile.displayName!!)
                                dataStoreManager.putString("AppProfileName", existingProfile.displayName!!)
                            }
                            dataStoreManager.putString("AppProfileImage", existingProfile.avatarUrl.uploadedProfileAvatar().orEmpty())
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
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) {}
                                }
                            }
                        } else {
                            val profileName = listOf("display_name", "full_name", "name").firstNotNullOfOrNull {
                                (user.userMetadata?.get(it) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                            }.orEmpty()
                            if (profileName.isNotEmpty()) {
                                dataStoreManager.putString("AccountName", profileName)
                                dataStoreManager.putString("AppProfileName", profileName)
                            }
                            // Gratify avatars are chosen in the app, independently of Google.

                        }

                        // Pulihkan data akun ini dari cloud + jaga isolasi per-akun:
                        // bila akun yang login via Google berbeda dari akun sebelumnya,
                        // performLoginSync membersihkan DB akun lama dulu baru menarik data akun baru.
                        val appProfileName = dataStoreManager.getString("AppProfileName").first()
                        signUpViewModel.refreshYouTubeAfterGoogleLogin()
                        if (supabase.auth.currentUserOrNull()?.id != user.id) return@collect
                        val destination = if (appProfileName.isNullOrEmpty()) {
                            CreateProfileDestination(skipYouTubeConnection = true)
                        } else {
                            MainDestination
                        }
                        dataStoreManager.setLoggedIn(true)
                        hasNavigated = true
                        navController.navigate(destination) {
                            popUpTo(0) { inclusive = true }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (e: Exception) {
                        hasNavigated = false
                        completionError = completionFailure
                    } finally { finishing = false }
                }
                else -> {}
            }
        }
    }


    DisposableEffect(Unit) {
        onDispose {
            signUpViewModel.cancelGoogleLogin()
            showBottomNavigation()
        }
    }

    AuthLandingPage(innerPadding, onBack = if (navController.previousBackStackEntry != null) {
        { showBottomNavigation(); navController.navigateUp(); Unit }
    } else null) {
        AuthLandingContent(googleWaiting, completionError ?: (googleState as? GoogleLoginState.Failed)?.message,
            onEmailLoginClick, onSignUpClick, { onGoogleClick(connectYouTube) }, signUpViewModel::cancelGoogleLogin, finishing,
            connectYouTube, { connectYouTube = it })
        if (completionError != null) TextButton(onClick = { completionRetry++ }, enabled = !finishing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(Res.string.retry)) }
    }
}
