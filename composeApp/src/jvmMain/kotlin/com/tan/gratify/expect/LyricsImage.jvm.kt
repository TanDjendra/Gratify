package com.tan.gratify.expect

import coil3.PlatformContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.imageio.ImageIO

actual fun lyricsShareTargetAvailable(context: PlatformContext, target: LyricsShareTarget): Boolean = target == LyricsShareTarget.SYSTEM
actual suspend fun shareLyricsImage(image: ImageBitmap, context: PlatformContext, target: LyricsShareTarget): Boolean = withContext(Dispatchers.Swing) {
    if (target != LyricsShareTarget.SYSTEM) return@withContext false
    val bytes = Image.makeFromBitmap(image.asSkiaBitmap()).use {
        it.encodeToData(EncodedImageFormat.PNG)?.use { encoded -> encoded.bytes }
    } ?: return@withContext false
    val picture = ImageIO.read(bytes.inputStream()) ?: return@withContext false
    Toolkit.getDefaultToolkit().systemClipboard.setContents(object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
            return picture
        }
    }, null)
    true
}

actual suspend fun saveLyricsImage(image: ImageBitmap, context: PlatformContext, uri: String): Boolean =
    withContext(Dispatchers.IO) {
        val bytes = Image.makeFromBitmap(image.asSkiaBitmap()).use {
            it.encodeToData(EncodedImageFormat.PNG)?.use { data -> data.bytes }
        } ?: return@withContext false
        java.io.File(uri).outputStream().use { it.write(bytes) }
        true
    }
