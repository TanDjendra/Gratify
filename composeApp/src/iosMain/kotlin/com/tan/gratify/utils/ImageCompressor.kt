package com.tan.gratify.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.UIImageJPEGRepresentation
import platform.UIKit.UIImage
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual suspend fun compressImage(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val cleanedPath = if (filePath.startsWith("file://")) {
            filePath.substring(7)
        } else {
            filePath
        }
        
        val image = UIImage(contentsOfFile = cleanedPath) ?: return@withContext null
        
        // Kompres gambar menjadi JPEG dengan kualitas 70%
        val imageData: NSData = UIImageJPEGRepresentation(image, 0.7) ?: return@withContext null
        
        // Konversi NSData ke ByteArray
        val byteArray = ByteArray(imageData.length.toInt())
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), imageData.bytes, imageData.length)
        }
        
        return@withContext byteArray
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
