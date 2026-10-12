package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifyColors

@Composable
fun DiscoveryFooter(loading: Boolean, failed: Boolean, exhausted: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                Spacer(Modifier.height(12.dp))
                Text("Menemukan pilihan berikutnya…", color = GratifyColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
            failed -> {
                Text("Rekomendasi belum dapat dimuat", color = GratifyColors.TextSecondary)
                TextButton(onClick = onRetry) { Text("Coba lagi") }
            }
            exhausted -> Text("Semua pilihan saat ini sudah ditampilkan. Tarik untuk menyegarkan.",
                color = GratifyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
