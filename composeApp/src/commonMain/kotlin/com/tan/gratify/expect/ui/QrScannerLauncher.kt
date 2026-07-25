package com.tan.gratify.expect.ui

import androidx.compose.runtime.Composable

@Composable
expect fun rememberQrScannerLauncher(
    onScanResult: (String) -> Unit
): () -> Unit
