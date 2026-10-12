package com.tan.gratify.expect.ui

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

expect fun ImageBitmap.toByteArray(): ByteArray?

expect fun Image.toImageBitmap(): ImageBitmap
/** Small software bitmap used for artwork mesh sampling. */
expect fun ImageBitmap.toReadableBitmap(width: Int, height: Int): ImageBitmap
