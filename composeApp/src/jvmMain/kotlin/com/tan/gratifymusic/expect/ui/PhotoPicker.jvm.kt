package com.tan.gratifymusic.expect.ui

import androidx.compose.runtime.Composable

@Composable
actual fun photoPickerResult(onResultUri: (String?) -> Unit): PhotoPickerLauncher =
    object : PhotoPickerLauncher {
        override fun launch() {
            onResultUri(null)
        }
    }