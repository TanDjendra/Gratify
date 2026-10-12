package com.tan.gratify.expect.ui

import android.graphics.Bitmap.CompressFormat.JPEG
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.toBitmap
import java.io.ByteArrayOutputStream

actual fun ImageBitmap.toByteArray(): ByteArray? {
    val byteArrayOutputStream = ByteArrayOutputStream()
    this.asAndroidBitmap().compress(JPEG, 100, byteArrayOutputStream)
    val bytesArray = byteArrayOutputStream.toByteArray()
    return bytesArray
}

actual fun Image.toImageBitmap(): ImageBitmap = this.toBitmap().asImageBitmap()
actual fun ImageBitmap.toReadableBitmap(width: Int, height: Int): ImageBitmap {
    val original = asAndroidBitmap()
    val software = if (original.config == android.graphics.Bitmap.Config.HARDWARE)
        original.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else original
    val scaled = android.graphics.Bitmap.createScaledBitmap(software, width, height, true)
    if (software !== original && software !== scaled) software.recycle()
    return scaled.asImageBitmap()
}
