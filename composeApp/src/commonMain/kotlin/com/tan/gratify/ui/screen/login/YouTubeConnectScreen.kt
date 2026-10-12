package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.repository.YouTubeConnectionStatus
import com.tan.gratify.ui.component.YouTubeConnectionCard
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.YouTubeConnectionViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun YouTubeConnectScreen(navController: NavController, onboarding: Boolean = false,
    hideBottomNavigation: () -> Unit = {}, showBottomNavigation: () -> Unit = {},
    viewModel: YouTubeConnectionViewModel = koinViewModel()) {
    val state by viewModel.connection.collectAsStateWithLifecycle()
    val waiting by viewModel.waiting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var confirmDisconnect by remember { mutableStateOf(false) }
    LaunchedEffect(url) { url?.let {
        try { uri.openUri(it); viewModel.urlOpened() } catch (_: Exception) { viewModel.browserUnavailable() }
    } }
    DisposableEffect(Unit) { hideBottomNavigation(); onDispose { viewModel.cancel(); showBottomNavigation() } }
    LaunchedEffect(Unit) { viewModel.refresh() }
    fun finish() {
        viewModel.cancel()
        if (onboarding) navController.navigate(MainDestination) { popUpTo(0) { inclusive = true } }
        else navController.navigateUp()
    }
    Surface(color = GratifyColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(if (onboarding) "Musikmu, terhubung" else "Koneksi YouTube", style = typo().headlineLarge, color = GratifyColors.TextPrimary)
            YouTubeConnectionCard(state.status, viewModel::connect, viewModel::refresh, waiting, error,
                onDisconnect = if (state.status == YouTubeConnectionStatus.CONNECTED) ({ confirmDisconnect = true }) else null)
            Text("Hanya data yang tersedia melalui API resmi YouTube yang digunakan. Riwayat dan rekomendasi pribadi YouTube Music tidak diimpor.",
                style = typo().bodySmall, color = GratifyColors.TextSecondary)
            Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                Text(if (!onboarding) "Kembali" else if (state.status != YouTubeConnectionStatus.CONNECTED) "Lanjut tanpa menghubungkan" else "Lanjutkan")
            }
            if (waiting) TextButton(onClick = viewModel::cancel) { Text("Batalkan koneksi") }
        }
    }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false },
        title = { Text("Putuskan akses YouTube?") },
        text = { Text("Playlist dan video yang disukai di YouTube tidak lagi dimuat. Akun Gratify tetap masuk.") },
        confirmButton = { TextButton(onClick = { confirmDisconnect = false; viewModel.disconnect() }) { Text("Putuskan") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Batal") } })
}
