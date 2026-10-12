package com.tan.gratify.ui.screen.login

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.viewModel.SettingsViewModel
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.mono
import com.tan.gratify.ui.theme.GratifyColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

/**
 * Splash screen: solid black background + centred Gratify logo.
 * After restoring the saved session it navigates to [MainDestination] if logged in,
 * or [LoginLandingDestination] if not logged in yet.
 *
 * It removes itself from the back-stack so the user cannot press Back to return.
 */
@Composable
fun SplashScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    var logoVisible by remember { mutableStateOf(false) }
    val sessions: com.tan.data.sync.AccountSessionCoordinator = koinInject()
    val access by sessions.access.collectAsStateWithLifecycle()

    val scale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.75f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splash_logo_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "splash_logo_alpha",
    )

    // Inisialisasi pengambilan data login status
    LaunchedEffect(Unit) {
        settingsViewModel.getLoggedIn()
        logoVisible = true
    }

    LaunchedEffect(access) {
        delay(300L)
        if (com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator.pending.value) return@LaunchedEffect
        val destination = when (val current = access) {
            is com.tan.data.sync.AccountAccess.Ready -> if (current.needsProfile && !current.offline)
                CreateProfileDestination() else MainDestination
            com.tan.data.sync.AccountAccess.SignedOut -> LoginLandingDestination
            else -> return@LaunchedEffect
        }
        navController.navigate(destination) { popUpTo(0) { inclusive = true } }
    }

    if (access == com.tan.data.sync.AccountAccess.RecoveryRequired) {
        com.tan.gratify.ui.component.AccountRecoveryScreen(false, sessions::retry)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratifyColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.mono),
            contentDescription = "Gratify Logo",
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            alpha = alpha,
        )
    }
}
