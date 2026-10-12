package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.tan.domain.data.model.update.UpdateData
import com.tan.gratify.Platform
import com.tan.gratify.expect.*
import com.tan.gratify.getPlatform

@Composable
fun AppUpdateDialog(
    update: UpdateData?,
    state: UpdateInstallState,
    androidInstaller: Boolean = getPlatform() == Platform.Android,
    actions: AppUpdateActions = AppUpdateActions(),
    onDismiss: () -> Unit,
) {
    val selected = state.update ?: update ?: return
    val uriHandler = LocalUriHandler.current
    val stage = state.stage
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (stage) {
            UpdateStage.DOWNLOADING -> "Mengunduh pembaruan"
            UpdateStage.PREPARING -> "Menyiapkan unduhan"
            UpdateStage.APPLYING -> "Menyiapkan pembaruan"
            UpdateStage.VERIFYING -> "Memeriksa pembaruan"
            UpdateStage.READY, UpdateStage.NEED_PERMISSION -> "Pembaruan siap dipasang"
            UpdateStage.INSTALLING -> "Lanjutkan di pemasang Android"
            UpdateStage.ERROR -> "Pembaruan belum berhasil"
            else -> "Pembaruan Gratify tersedia"
        }) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gratify ${selected.tagName.removePrefix("v")}", style = MaterialTheme.typography.titleMedium)
                selected.sizeBytes?.let {
                    val download = if (state.delta) state.totalBytes else it
                    Text("${if (state.delta) "Unduhan hemat kuota" else "Ukuran APK lengkap"}: ${(download / (1024.0 * 1024)).toInt()} MB", style = MaterialTheme.typography.bodySmall)
                    if (state.delta) Text("APK setelah disiapkan: ${(it / (1024.0 * 1024)).toInt()} MB", style = MaterialTheme.typography.bodySmall)
                }
                if (stage == UpdateStage.IDLE && selected.deltas.isNotEmpty()) Text("Jika versi terpasang cocok, Gratify memakai unduhan yang lebih kecil.")
                when (stage) {
                    UpdateStage.DOWNLOADING -> {
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("${(progress * 100).toInt()}% · ${state.downloadedBytes / (1024 * 1024)} MB diunduh")
                        }
                        Text(state.message ?: "Unduhan dapat dilanjutkan saat Anda membuka Gratify kembali.")
                    }
                    UpdateStage.VERIFYING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Memeriksa berkas, versi, dan tanda tangan aplikasi.") }
                    UpdateStage.PREPARING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Mencocokkan versi terpasang dengan pembaruan hemat kuota.") }
                    UpdateStage.APPLYING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Menyiapkan APK dari unduhan hemat kuota. Anda tetap bisa memakai aplikasi.") }
                    UpdateStage.NEED_PERMISSION -> Text("Izinkan pemasangan dari Gratify di pengaturan Android. Setelah kembali, tekan Pasang untuk melanjutkan.")
                    UpdateStage.READY -> Text("Berkas sudah diverifikasi. Pemasangan dilanjutkan melalui konfirmasi Android.")
                    UpdateStage.INSTALLING -> Text("Selesaikan konfirmasi pemasangan Android. Jika dibatalkan, pembaruan tetap bisa dipasang nanti.")
                    UpdateStage.ERROR -> Text(state.message ?: "Coba lagi saat koneksi tersedia.")
                    else -> if (selected.body.isNotBlank()) Markdown(selected.body)
                }
                Text("Anda tetap bisa memakai Gratify dan mendengarkan lagu unduhan.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            when {
                !androidInstaller -> TextButton(onClick = { uriHandler.openUri("https://github.com/TanDjendra/Gratify/releases/latest"); onDismiss() }) { Text("Lihat rilis resmi") }
                stage == UpdateStage.IDLE -> TextButton(onClick = { actions.download(selected) }) { Text("Unduh") }
                stage == UpdateStage.READY || stage == UpdateStage.NEED_PERMISSION -> TextButton(onClick = actions.install) {
                    Text(if (stage == UpdateStage.NEED_PERMISSION) "Izinkan pemasangan" else "Pasang")
                }
                stage == UpdateStage.ERROR && state.canDownloadFull -> TextButton(onClick = { actions.download(selected.copy(deltas = emptyList())) }) { Text("Unduh APK lengkap") }
                stage == UpdateStage.ERROR && state.canRetry -> TextButton(onClick = actions.retry) { Text("Coba lagi") }
                stage == UpdateStage.ERROR -> TextButton(onClick = { uriHandler.openUri("mailto:supportgratify@gmail.com") }) { Text("Hubungi dukungan") }
            }
        },
        dismissButton = {
            Row {
                if (stage in setOf(UpdateStage.PREPARING, UpdateStage.DOWNLOADING, UpdateStage.APPLYING, UpdateStage.VERIFYING, UpdateStage.ERROR, UpdateStage.READY, UpdateStage.NEED_PERMISSION)) {
                    TextButton(onClick = { actions.cancel(); onDismiss() }) { Text("Batalkan") }
                }
                TextButton(onClick = onDismiss) { Text(if (stage == UpdateStage.ERROR) "Tutup" else "Nanti") }
            }
        },
    )
}

data class AppUpdateActions(
    val download: (UpdateData) -> Unit = ::downloadAndInstallApk,
    val install: () -> Unit = ::installDownloadedUpdate,
    val retry: () -> Unit = ::retryApkUpdate,
    val cancel: () -> Unit = ::cancelApkUpdate,
)
