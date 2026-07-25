package com.tan.gratify.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import multiplatform.network.cmptoast.showToast

@Composable
actual fun rememberQrScannerLauncher(
    onScanResult: (String) -> Unit
): () -> Unit {
    return remember {
        {
            showToast("Fitur pindai kamera QR hanya tersedia di aplikasi Android")
        }
    }
}
