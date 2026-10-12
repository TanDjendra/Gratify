package com.tan.gratify.expect

import android.content.ClipData
import android.content.Intent
import coil3.PlatformContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun sharePackages(target: LyricsShareTarget): List<String> = when (target) {
    LyricsShareTarget.INSTAGRAM -> listOf("com.instagram.android")
    LyricsShareTarget.WHATSAPP -> listOf("com.whatsapp", "com.whatsapp.w4b")
    LyricsShareTarget.SYSTEM -> emptyList()
}
private fun sharePackage(context: PlatformContext, target: LyricsShareTarget): String? =
    sharePackages(target).firstOrNull { name ->
        Intent(Intent.ACTION_SEND).setType("image/png").setPackage(name).resolveActivity(context.packageManager) != null
    }
actual fun lyricsShareTargetAvailable(context: PlatformContext, target: LyricsShareTarget): Boolean =
    target == LyricsShareTarget.SYSTEM || sharePackage(context, target) != null

actual suspend fun shareLyricsImage(image: ImageBitmap, context: PlatformContext, target: LyricsShareTarget): Boolean {
    val activity = context
    val packageName = if (target == LyricsShareTarget.SYSTEM) null else sharePackage(context, target) ?: return false
    val uri = withContext(Dispatchers.IO) {
        val directory = File(activity.cacheDir, "images").apply { mkdirs() }
        val file = File(directory, "Gratify_share_${UUID.randomUUID()}.png")
        file.outputStream().use { output ->
            check(image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        FileProvider.getUriForFile(activity, "${activity.packageName}.FileProvider", file)
    }
    return withContext(Dispatchers.Main) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Gratify image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            packageName?.let { setPackage(it) }
        }
        val launch = if (target == LyricsShareTarget.SYSTEM) Intent.createChooser(intent, "Gratify") else intent
        activity.startActivity(launch.apply {
            if (activity !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    }
}

actual suspend fun saveLyricsImage(image: ImageBitmap, context: PlatformContext, uri: String): Boolean =
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(android.net.Uri.parse(uri), "w")?.use { output ->
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        } ?: false
    }
