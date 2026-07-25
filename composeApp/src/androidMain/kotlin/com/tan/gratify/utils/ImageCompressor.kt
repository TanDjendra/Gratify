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
 * dan mengonversi format ke WebP atau JPEG.
 */
actual suspend fun compressImage(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val context = getKoin().get<Context>()
        val uri = Uri.parse(filePath)
        
        // Coba buka InputStream dari URI
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        
        if (originalBitmap == null) return@withContext null
        
        // Tentukan batas maksimal lebar/tinggi, misal 512px
        val maxSize = 512
        val width = originalBitmap.width
        val height = originalBitmap.height
        
        val bitmapToCompress = if (width > maxSize || height > maxSize) {
            val ratio = max(width, height).toFloat() / maxSize
            val newWidth = (width / ratio).toInt()
            val newHeight = (height / ratio).toInt()
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }
        
        val outputStream = ByteArrayOutputStream()
        // Kompresi (menggunakan WebP lebih efisien, tapi JPEG juga baik)
        // Kualitas 70 biasanya cukup untuk avatar (100-300kb max)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            bitmapToCompress.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, outputStream)
        } else {
            bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        }
        
        val byteArray = outputStream.toByteArray()
        
        // Membersihkan memori
        if (bitmapToCompress != originalBitmap) {
            bitmapToCompress.recycle()
        }
        originalBitmap.recycle()
        
        return@withContext byteArray
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
