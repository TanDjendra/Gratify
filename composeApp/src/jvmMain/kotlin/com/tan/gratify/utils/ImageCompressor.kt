package com.tan.gratify.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max

actual suspend fun compressImage(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        // Hapus awalan file:// jika ada, file picker JVM kadang memberikan path absolut
        val cleanedPath = if (filePath.startsWith("file://")) {
            filePath.substring(7)
        } else {
            filePath
        }
        
        val file = File(cleanedPath)
        if (!file.exists()) return@withContext null

        val originalImage: BufferedImage = ImageIO.read(file) ?: return@withContext null
        
        val maxSize = 512
        val width = originalImage.width
        val height = originalImage.height

        val compressedImage = if (width > maxSize || height > maxSize) {
            val ratio = max(width, height).toFloat() / maxSize
            val newWidth = (width / ratio).toInt()
            val newHeight = (height / ratio).toInt()
            
            val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
            val g = resized.createGraphics()
            g.drawImage(originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
            g.dispose()
            resized
        } else {
            // Konversi ke RGB jika ada transparansi (JPEG tidak mendukung alpha channel)
            val rgbImage = BufferedImage(originalImage.width, originalImage.height, BufferedImage.TYPE_INT_RGB)
            val g = rgbImage.createGraphics()
            g.drawImage(originalImage, 0, 0, null)
            g.dispose()
            rgbImage
        }

        val outputStream = ByteArrayOutputStream()
        
        // Kompresi menggunakan penulis JPEG dengan kualitas 70%
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (writers.hasNext()) {
            val writer = writers.next()
            val ios = ImageIO.createImageOutputStream(outputStream)
            writer.output = ios
            
            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                param.compressionQuality = 0.7f // 70% kualitas
            }
            
            writer.write(null, IIOImage(compressedImage, null, null), param)
            
            ios.close()
            writer.dispose()
        } else {
            // Fallback
            ImageIO.write(compressedImage, "jpg", outputStream)
        }

        return@withContext outputStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
