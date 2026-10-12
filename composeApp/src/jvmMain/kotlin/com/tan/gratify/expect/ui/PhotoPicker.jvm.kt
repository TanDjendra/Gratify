package com.tan.gratify.expect.ui

import androidx.compose.runtime.Composable

@Composable
actual fun photoPickerResult(shape: PhotoCropShape, onResultUri: (String?) -> Unit): PhotoPickerLauncher =
    object : PhotoPickerLauncher {
        override fun launch() {
            onResultUri(null)
        }
    }
