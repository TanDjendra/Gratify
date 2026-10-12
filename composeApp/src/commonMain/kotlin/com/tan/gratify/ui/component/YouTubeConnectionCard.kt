package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.domain.repository.YouTubeConnectionStatus
import com.tan.gratify.ui.theme.*

@Composable
fun YouTubeConnectionCard(status: YouTubeConnectionStatus, onConnect: () -> Unit, onRetry: () -> Unit,
    waiting: Boolean = false, error: String? = null, onDisconnect: (() -> Unit)? = null) {
    Surface(color = GratifyColors.SurfaceRaised, shape = GratifyShapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Koleksi YouTube", style = typo().titleLarge, color = GratifyColors.TextPrimary)
            Text(when (status) {
                YouTubeConnectionStatus.CONNECTED -> "Google terhubung. Playlist milikmu dan video yang disukai tersedia dengan akses baca saja."
                YouTubeConnectionStatus.CONFIGURATION_REQUIRED -> "Koneksi resmi YouTube belum diaktifkan oleh pengelola aplikasi. Musik dan koleksi Gratify tetap bisa digunakan."
                YouTubeConnectionStatus.ERROR -> "Status koneksi belum dapat dimuat. Periksa koneksi dan coba kembali."
                YouTubeConnectionStatus.LOADING -> "Memeriksa koneksi Google…"
                YouTubeConnectionStatus.RECONNECT -> "Izin YouTube perlu diperbarui. Hubungkan Google kembali untuk membaca koleksimu."
                else -> "Izinkan Gratify membaca playlist milikmu dan video yang kamu sukai di YouTube. Gratify tidak akan mengubah akun atau koleksi YouTube."
            }, style = typo().bodyMedium, color = GratifyColors.TextSecondary)
            error?.let { Text(it, color = GratifyColors.Error, style = typo().bodySmall) }
            when {
                waiting -> { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GratifyColors.Accent)
                    Text("Selesaikan izin Google di browser lalu kembali ke Gratify.", style = typo().bodySmall, color = GratifyColors.TextSecondary) }
                status == YouTubeConnectionStatus.LOADING -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = GratifyColors.Accent)
                status == YouTubeConnectionStatus.CONNECTED && onDisconnect != null -> TextButton(onClick = onDisconnect) { Text("Putuskan akses YouTube") }
                status == YouTubeConnectionStatus.CONFIGURATION_REQUIRED || status == YouTubeConnectionStatus.ERROR -> OutlinedButton(onClick = onRetry) { Text("Periksa kembali") }
                status != YouTubeConnectionStatus.CONNECTED -> Button(onClick = onConnect) { Text("Hubungkan Google") }
            }
        }
    }
}
