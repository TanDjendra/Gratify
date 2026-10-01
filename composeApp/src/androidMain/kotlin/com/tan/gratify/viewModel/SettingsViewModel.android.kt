package com.tan.gratify.viewModel

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil3.imageLoader
import com.eygraber.uri.Uri
import com.eygraber.uri.toAndroidUri
import com.tan.common.Config
import com.tan.common.DB_NAME
import com.tan.common.DOWNLOAD_EXOPLAYER_FOLDER
import com.tan.common.EXOPLAYER_DB_NAME
import com.tan.common.SETTINGS_FILENAME
import com.tan.domain.repository.CacheRepository
import com.tan.domain.repository.CommonRepository
import com.tan.logger.Logger
import com.tan.media3.di.stopService
import com.tan.gratify.extension.bytesToMB
import com.tan.gratify.extension.getSizeOfFile
import com.tan.gratify.extension.zipInputStream
import com.tan.gratify.extension.zipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform.getKoin
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.restore_failed
import gratify.composeapp.generated.resources.restore_success
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? {
    val application: Context = getKoin().get()
    return withContext(Dispatchers.Default) {
        val playerCache = cacheRepository.getCacheSize(Config.PLAYER_CACHE)
        val downloadCache = cacheRepository.getCacheSize(Config.DOWNLOAD_CACHE)
        val canvasCache = cacheRepository.getCacheSize(Config.CANVAS_CACHE)
        val mStorageStatsManager =
            application.getSystemService(StorageStatsManager::class.java)
        if (mStorageStatsManager != null) {
            val totalByte =
                mStorageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT).bytesToMB()
            val freeSpace =
                mStorageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT).bytesToMB()
            val usedSpace = totalByte - freeSpace
            val gratifyMusicSize = getSizeOfFile(application.filesDir).bytesToMB()
            val thumbSize = (application.imageLoader.diskCache?.size ?: 0L).bytesToMB()
            val otherApp = gratifyMusicSize.let { usedSpace.minus(it) - thumbSize }
            val databaseSize =
                gratifyMusicSize - playerCache.bytesToMB() - downloadCache.bytesToMB() - canvasCache.bytesToMB()
            if (totalByte ==
                freeSpace + otherApp + gratifyMusicSize + thumbSize
            ) {
                SettingsStorageSectionFraction(
                    otherApp = otherApp.toFloat().div(totalByte.toFloat()),
                    downloadCache =
                        downloadCache
                            .bytesToMB()
                            .toFloat()
                            .div(totalByte.toFloat()),
                    playerCache =
                        playerCache
                            .bytesToMB()
                            .toFloat()
                            .div(totalByte.toFloat()),
                    canvasCache =
                        canvasCache
                            .bytesToMB()
                            .toFloat()
                            .div(totalByte.toFloat()),
                    thumbCache = thumbSize.toFloat().div(totalByte.toFloat()),
                    freeSpace = freeSpace.toFloat().div(totalByte.toFloat()),
                    appDatabase = databaseSize.toFloat().div(totalByte.toFloat()),
                )
            } else {
                null
            }
        } else {
            null
        }
    }
}

actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    val application: Context = getKoin().get()
    val stage = File(application.cacheDir, "restore-${java.util.UUID.randomUUID()}")
    check(stage.mkdirs())
    var databaseClosed = false
    var success = false
    try {
        withContext(Dispatchers.IO) {
            val files = application.contentResolver.openInputStream(uri.toAndroidUri())?.use { input ->
                stageRestoreArchive(input, stage, setOf(DB_NAME, EXOPLAYER_DB_NAME, "$SETTINGS_FILENAME.preferences_pb"), DOWNLOAD_EXOPLAYER_FOLDER)
            } ?: error("Backup cannot be opened")
            files.filter { it.first == "$SETTINGS_FILENAME.preferences_pb" }.forEach { validateRestorePreferences(it.second) }
            // Validate SQLite before closing or replacing the live database.
            files.filter { it.first == DB_NAME || it.first == EXOPLAYER_DB_NAME }.forEach { (_, file) ->
                android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Invalid backup database" }
                    }
                }
            }
            val replacements = files.filterNot { it.first.startsWith("$DOWNLOAD_EXOPLAYER_FOLDER/") }.map { (name, source) ->
                source to when (name) {
                    DB_NAME -> File(requireNotNull(commonRepository.getDatabasePath()))
                    EXOPLAYER_DB_NAME -> application.getDatabasePath(EXOPLAYER_DB_NAME)
                    else -> File(application.filesDir, "datastore/$name")
                }
            }.toMutableList()
            if (files.any { it.first.startsWith("$DOWNLOAD_EXOPLAYER_FOLDER/") }) {
                replacements += File(stage, DOWNLOAD_EXOPLAYER_FOLDER) to File(application.filesDir, DOWNLOAD_EXOPLAYER_FOLDER)
            }
            withContext(Dispatchers.Main) { stopService(application) }
            commonRepository.databaseDaoCheckpoint()
            commonRepository.closeDatabase()
            databaseClosed = true
            replaceRestoreFiles(replacements, File(stage, "rollback"))
            replacements.map { it.second }.filter { it.name == DB_NAME || it.name == EXOPLAYER_DB_NAME }.forEach { database ->
                File(database.path + "-wal").delete()
                File(database.path + "-shm").delete()
            }
            success = true
        }
    } finally {
        if (!databaseClosed || success) stage.deleteRecursively()
        if (databaseClosed) withContext(Dispatchers.Main) {
            showToast(getString(if (success) Res.string.restore_success else Res.string.restore_failed), ToastGravity.Bottom)
            val ctx = application.applicationContext
            val launch = requireNotNull(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName))
            ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
            Runtime.getRuntime().exit(0)
        }
    }
}

private fun backupFolder(
    folder: File,
    baseName: String,
    zipOutputStream: ZipOutputStream,
) {
    if (!folder.exists() || !folder.isDirectory) return

    Logger.d("BackupRestore", "Backing up folder: ${folder.absolutePath} as $baseName")
    folder.listFiles()?.forEach { file ->
        if (file.isFile) {
            val entryName = "$baseName/${file.name}"
            Logger.d("BackupRestore", "Backing up file: $entryName")
            zipOutputStream.putNextEntry(ZipEntry(entryName))
            file.inputStream().buffered().use { inputStream ->
                inputStream.copyTo(zipOutputStream)
            }
            zipOutputStream.closeEntry()
        } else if (file.isDirectory) {
            Logger.d("BackupRestore", "Entering subdirectory: ${file.name}")
            backupFolder(file, "$baseName/${file.name}", zipOutputStream)
        }
    }
}

private fun debugFolderContents(
    folder: File,
    level: Int = 0,
) {
    if (!folder.exists()) {
        Logger.d("BackupRestore", "${"  ".repeat(level)}Folder does not exist: ${folder.absolutePath}")
        return
    }

    Logger.d("BackupRestore", "${"  ".repeat(level)}Folder: ${folder.name} (${folder.absolutePath})")
    folder.listFiles()?.forEach { file ->
        if (file.isFile) {
            Logger.d("BackupRestore", "${"  ".repeat(level + 1)}File: ${file.name} (${file.length()} bytes)")
        } else if (file.isDirectory) {
            debugFolderContents(file, level + 1)
        }
    }
}

private fun clearFolder(folder: File) {
    if (folder.exists() && folder.isDirectory) {
        Logger.d("BackupRestore", "Clearing folder: ${folder.absolutePath}")
        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                Logger.d("BackupRestore", "Deleting file: ${file.name}")
                file.delete()
            } else if (file.isDirectory) {
                clearFolder(file) // Recursive
                Logger.d("BackupRestore", "Deleting directory: ${file.name}")
                file.delete() // Delete empty directory
            }
        }
    }
}

private fun restoreFolder(
    entryName: String,
    zipInputStream: ZipInputStream,
    baseFolderName: String,
) {
    val application: Context = getKoin().get()
    Logger.d("BackupRestore", "Restoring entry: $entryName")

    // Extract relative path from entry name
    val relativePath = entryName.removePrefix("$baseFolderName/")
    val targetFile = application.filesDir / baseFolderName / relativePath

    Logger.d("BackupRestore", "Target file path: ${targetFile.absolutePath}")
    Logger.d("BackupRestore", "Relative path: $relativePath")

    // Create parent directories if they don't exist
    val parentCreated = targetFile.parentFile?.mkdirs()
    Logger.d("BackupRestore", "Parent dir created: $parentCreated, parent exists: ${targetFile.parentFile?.exists()}")

    try {
        // Restore the file content
        targetFile.outputStream().use { outputStream ->
            val bytesWritten = zipInputStream.copyTo(outputStream)
            Logger.d("BackupRestore", "Restored file: ${targetFile.name}, bytes: $bytesWritten")

            // Verify file was created
            if (targetFile.exists()) {
                Logger.d("BackupRestore", "File exists after restore: ${targetFile.name}, size: ${targetFile.length()}")
            } else {
                Logger.e("BackupRestore", "File NOT created: ${targetFile.name}")
            }
        }
    } catch (e: Exception) {
        Logger.e("BackupRestore", "Error restoring file: ${targetFile.name}")
    }
}

operator fun File.div(child: String): File = File(this, child)

actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) = withContext(Dispatchers.IO) {
    val application: Context = getKoin().get()
    val exported = File(application.cacheDir, "active-library-${java.util.UUID.randomUUID()}.db")
    try {
        commonRepository.exportActiveAccountDatabase(exported.absolutePath)
        val destination = requireNotNull(application.contentResolver.openOutputStream(uri.toAndroidUri())) { "Cannot open backup destination" }
        ZipOutputStream(destination.buffered()).use { output ->
            fun append(file: File, name: String) {
                if (!file.isFile) return
                output.putNextEntry(ZipEntry(name))
                file.inputStream().buffered().use { it.copyTo(output) }
                output.closeEntry()
            }
            append(application.filesDir / "datastore" / "$SETTINGS_FILENAME.preferences_pb", "$SETTINGS_FILENAME.preferences_pb")
            append(exported, DB_NAME)
            if (backupDownloaded) {
                append(application.getDatabasePath(EXOPLAYER_DB_NAME), EXOPLAYER_DB_NAME)
                backupFolder(application.filesDir / DOWNLOAD_EXOPLAYER_FOLDER, DOWNLOAD_EXOPLAYER_FOLDER, output)
            }
        }
    } finally { exported.delete() }
}

actual fun getPackageName(): String {
    val application: Context = getKoin().get()
    return application.packageName
}

actual fun getFileDir(): String {
    val application: Context = getKoin().get()
    return application.filesDir.absolutePath
}

actual fun changeLanguageNative(code: String) {
    try {
        val localeList =
            LocaleListCompat.forLanguageTags(code)
        Logger.d("Language", localeList.toString())
        AppCompatDelegate.setApplicationLocales(localeList)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
