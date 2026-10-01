package com.tan.gratify.expect

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

actual fun downloadAndInstallApk(url: String, versionName: String) {
    val context: AppCompatActivity = getKoin().get()
    val appContext = context.applicationContext
    val uri = Uri.parse(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
        Toast.makeText(context, "Tautan pembaruan harus memakai HTTPS", Toast.LENGTH_SHORT).show()
        return
    }

    val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val fileName = "Gratify-update-${System.currentTimeMillis()}.apk"
    val request = DownloadManager.Request(uri).apply {
        setTitle("Pembaruan Gratify v$versionName")
        setDescription("Mengunduh versi terbaru aplikasi...")
        setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setMimeType("application/vnd.android.package-archive")
    }

    val downloadId = downloadManager.enqueue(request)
    Toast.makeText(context, "Mengunduh pembaruan...", Toast.LENGTH_SHORT).show()

    val onComplete = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
            runCatching { appContext.unregisterReceiver(this) }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apk = copyVerifiedDownload(appContext, downloadManager, downloadId)
                    withContext(Dispatchers.Main) { installApk(appContext, apk) }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Pembaruan gagal diverifikasi", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED,
        )
    } else {
        appContext.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}

private fun copyVerifiedDownload(context: Context, manager: DownloadManager, downloadId: Long): File {
    val succeeded = manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
        cursor.moveToFirst() &&
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
            DownloadManager.STATUS_SUCCESSFUL
    }
    check(succeeded) { "Download did not complete successfully" }

    val downloadedUri = manager.getUriForDownloadedFile(downloadId)
        ?: error("Downloaded APK is unavailable")
    val updateDirectory = File(context.cacheDir, "updates")
    check(updateDirectory.isDirectory || updateDirectory.mkdirs()) { "Update cache is unavailable" }
    val apk = File(updateDirectory, "gratify-update-$downloadId.apk")
    try {
        context.contentResolver.openInputStream(downloadedUri)?.use { input ->
            apk.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Downloaded APK cannot be opened")
        check(apk.length() > 0) { "Downloaded APK is empty" }
        verifyUpdatePackage(context, apk)
        return apk
    } catch (e: Exception) {
        apk.delete()
        throw e
    }
}

@Suppress("DEPRECATION")
private fun verifyUpdatePackage(context: Context, apk: File) {
    val packageManager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    val installed = packageManager.getPackageInfo(context.packageName, flags)
    val candidate = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        ?: error("Downloaded file is not an APK")

    check(candidate.packageName == context.packageName) { "APK package name differs" }
    val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        installed.longVersionCode
    } else {
        installed.versionCode.toLong()
    }
    val candidateVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        candidate.longVersionCode
    } else {
        candidate.versionCode.toLong()
    }
    check(candidateVersion > installedVersion) { "APK is not a newer version" }

    val trusted = signingCertificates(installed)
    val downloaded = signingCertificates(candidate)
    check(
        trusted.isNotEmpty() && trusted.size == downloaded.size &&
            trusted.all { certificate -> downloaded.any { it.contentEquals(certificate) } }
    ) { "APK signing certificate differs" }
}

@Suppress("DEPRECATION")
private fun signingCertificates(info: PackageInfo): List<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
    } else {
        info.signatures?.map { it.toByteArray() }.orEmpty()
    }

private fun installApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(installIntent)
}
