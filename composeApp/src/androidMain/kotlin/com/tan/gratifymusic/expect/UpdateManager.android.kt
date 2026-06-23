package com.tan.gratifymusic.expect

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.koin.mp.KoinPlatform.getKoin

actual fun downloadAndInstallApk(url: String, versionName: String) {
    val context: AppCompatActivity = getKoin().get()
    
    if (url.isEmpty() || !url.startsWith("http")) {
        Toast.makeText(context, "Tautan pembaruan tidak valid", Toast.LENGTH_SHORT).show()
        return
    }

    Toast.makeText(context, "Mengunduh pembaruan...", Toast.LENGTH_SHORT).show()

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = Uri.parse(url)
    
    val fileName = "GratifyMusic_v$versionName.apk"
    val request = DownloadManager.Request(uri).apply {
        setTitle("Pembaruan GratifyMusic v$versionName")
        setDescription("Mengunduh versi terbaru aplikasi...")
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setMimeType("application/vnd.android.package-archive")
    }

    val downloadId = downloadManager.enqueue(request)

    val onComplete = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId) {
                try {
                    ctx.unregisterReceiver(this)
                } catch (e: Exception) {
                    // Avoid double unregister crash
                }
                
                val downloadedUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (downloadedUri != null) {
                    installApk(ctx, downloadedUri)
                } else {
                    Toast.makeText(ctx, "Gagal mendapatkan berkas unduhan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    } else {
        context.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
}

private fun installApk(context: Context, fileUri: Uri) {
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fileUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    
    try {
        context.startActivity(installIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal meluncurkan pemasang aplikasi: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
