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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.viewModel.SettingsViewModel
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.app_icon
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.first
import io.github.jan.supabase.auth.auth
import com.tan.data.sync.UserDataSyncManager

/**
 * Splash screen: solid black background + centred Gratify logo.
 * After a 1.5-second initialisation delay it navigates to [MainDestination] if logged in,
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
    val loggedInState by settingsViewModel.loggedIn.collectAsStateWithLifecycle(null)

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

    // Tunggu status login terisi, lalu navigasi setelah 1.5 s
    val dataStoreManager: DataStoreManager = koinInject()
    val supabase: io.github.jan.supabase.SupabaseClient = koinInject()
    val userDataSyncManager: UserDataSyncManager = koinInject()

    LaunchedEffect(loggedInState) {
        if (loggedInState != null) {
            delay(1_500L)

            // IRON-CLAD GUARD: Selalu periksa session dari Supabase untuk mencegah routing leak
            // karena external browser OAuth mungkin gagal tapi local DataStore belum tersinkronisasi.
            val currentSession = supabase.auth.currentSessionOrNull()

            val destination = if (currentSession != null && loggedInState == DataStoreManager.TRUE) {
                val appProfileName = dataStoreManager.getString("AppProfileName").first()
                if (appProfileName.isNullOrEmpty()) {
                    CreateProfileDestination
                } else {
                    userDataSyncManager.startPeriodicSync()
                    MainDestination
                }
            } else {
                // Jika session Supabase null (misal Google Auth gagal/dibatalkan), paksa kembali ke Landing
                if (loggedInState == DataStoreManager.TRUE) {
                    dataStoreManager.setLoggedIn(false) // Bersihkan leak state
                }
                LoginLandingDestination
            }
            
            navController.navigate(destination) {
                popUpTo(0) { inclusive = true } // Hapus seluruh stack
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = "Gratify Logo",
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            alpha = alpha,
        )
    }
}
