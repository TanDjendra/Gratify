package com.tan.gratify.expect.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.toBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun ImageBitmap.toByteArray(): ByteArray? {
    val image = Image.makeFromBitmap(this.asSkiaBitmap())
    val bytesArray =
        image.encodeToData(EncodedImageFormat.JPEG, 100)?.bytes
    return bytesArray
}

actual fun coil3.Image.toImageBitmap(): ImageBitmap =
    this.toBitmap().asComposeImageBitmap()
actual fun ImageBitmap.toReadableBitmap(width: Int, height: Int): ImageBitmap {
    val result = ImageBitmap(width, height)
    androidx.compose.ui.graphics.Canvas(result).drawImageRect(this,
        androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(this.width, this.height),
        androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(width, height),
        androidx.compose.ui.graphics.Paint())
    return result
}
