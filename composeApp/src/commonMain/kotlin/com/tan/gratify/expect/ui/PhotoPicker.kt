@file:Suppress("ktlint:standard:filename")

package com.tan.gratify.expect.ui

import androidx.compose.runtime.Composable

interface PhotoPickerLauncher {
    val isActive: Boolean get() = false
    fun launch()
}

@Composable
expect fun photoPickerResult(shape: PhotoCropShape = PhotoCropShape.SQUARE, onResultUri: (String?) -> Unit): PhotoPickerLauncher

enum class PhotoCropShape { SQUARE, CIRCLE }
