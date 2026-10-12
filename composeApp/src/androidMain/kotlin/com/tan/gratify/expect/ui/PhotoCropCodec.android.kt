package com.tan.gratify.expect.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Orientation is corrected before crop coordinates are calculated. Longest decoded side is at most 2048. */
internal fun decodeCropPhoto(context: Context, uri: Uri, forceLegacyDecode: Boolean = false): Bitmap {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 28 && !forceLegacyDecode) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val ratio = minOf(1f, 2048f / max(info.size.width, info.size.height))
        decoder.setTargetSize(max(1, (info.size.width * ratio).roundToInt()), max(1, (info.size.height * ratio).roundToInt()))
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { requireNotNull(it); BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val bitmap = resolver.openInputStream(uri).use { requireNotNull(it); requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })) }
    val orientation = resolver.openInputStream(uri).use { requireNotNull(it); ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    val matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }
            8 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
}

internal fun exportCropPhoto(context: Context, source: Bitmap, crop: CropPixels): String {
    val directory = File(context.cacheDir, "photo_crops").apply { mkdirs() }
    directory.listFiles()?.filter { it.extension == "jpg" && System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }?.forEach { it.delete() }
    val output = File(directory, "${UUID.randomUUID()}.jpg")
    val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    try {
        android.graphics.Canvas(result).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(source, android.graphics.Rect(crop.x, crop.y, crop.x + crop.side, crop.y + crop.side),
                android.graphics.Rect(0, 0, 512, 512), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
        output.outputStream().use { check(result.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        var bytes = 0L
        directory.listFiles()?.filter { it.extension == "jpg" }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, file ->
            bytes += file.length()
            if (file != output && (index >= 64 || bytes > 16L * 1024 * 1024)) file.delete()
        }
        return Uri.fromFile(output).toString()
    } catch (error: Exception) { output.delete(); throw error }
    finally { result.recycle() }
}
