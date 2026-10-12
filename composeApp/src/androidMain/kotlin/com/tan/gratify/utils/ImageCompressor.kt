package com.tan.gratify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Kompres gambar ke ByteArray sebelum diupload ke server.
 * Memastikan ukuran gambar menjadi lebih kecil (kisaran 100-300kb) jika memungkinkan,
 * dan mengonversi format ke JPEG agar sesuai dengan path avatar .jpg.
 */
actual suspend fun compressImage(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val context = getKoin().get<Context>()
        val uri = Uri.parse(filePath)
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return@withContext null
        boundsStream.use { BitmapFactory.decodeStream(it, null, options) }

        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return@withContext null

        // Keep the final size based on the original bounds, not decoder rounding.
        val maxSize = 512
        val ratio = max(width, height).toFloat() / maxSize
        val newWidth = if (ratio > 1) (width / ratio).toInt() else width
        val newHeight = if (ratio > 1) (height / ratio).toInt() else height
        if (newWidth <= 0 || newHeight <= 0) return@withContext null

        // Decode the smallest power-of-two sample that still covers both target axes.
        // Reopen the URI: content-provider streams need not support mark/reset.
        var sampleSize = 1
        while (width / sampleSize / 2 >= newWidth && height / sampleSize / 2 >= newHeight) {
            sampleSize *= 2
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        val sampledBitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        var bitmapToCompress = sampledBitmap
        try {
            if (sampledBitmap.width != newWidth || sampledBitmap.height != newHeight) {
                bitmapToCompress = Bitmap.createScaledBitmap(sampledBitmap, newWidth, newHeight, true)
            }
            val outputStream = ByteArrayOutputStream()
            // Avatar Storage uses a .jpg path on every platform.
            bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            return@withContext outputStream.toByteArray()
        } finally {
            if (bitmapToCompress !== sampledBitmap) bitmapToCompress.recycle()
            sampledBitmap.recycle()
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        return@withContext null
    }
}
