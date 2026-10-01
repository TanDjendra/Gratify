package com.tan.gratify.service.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tan.common.DB_NAME
import com.tan.common.DOWNLOAD_EXOPLAYER_FOLDER
import com.tan.common.EXOPLAYER_DB_NAME
import com.tan.common.SETTINGS_FILENAME
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.CommonRepository
import com.tan.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AutoBackupWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {

    private val commonRepository: CommonRepository by inject()
    private val dataStoreManager: DataStoreManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Logger.i(TAG, "Starting auto backup...")

            // Check if auto backup is still enabled
            val enabled = dataStoreManager.autoBackupEnabled.first()
            if (enabled != DataStoreManager.TRUE) {
                Logger.i(TAG, "Auto backup is disabled, skipping...")
                return@withContext Result.success()
            }

            // Get backup settings
            val backupDownloaded = dataStoreManager.backupDownloaded.first() == DataStoreManager.TRUE
            val maxFiles = dataStoreManager.autoBackupMaxFiles.first()

            // Create temp backup file
            val tempBackupFile = createBackupFile(backupDownloaded)

            // Save to Downloads/Gratify folder
            val success = saveToDownloads(tempBackupFile)

            // Delete temp file
            tempBackupFile.delete()

            if (success) {
                // Cleanup old backups
                cleanupOldBackups(maxFiles)

                // Update last backup time
                dataStoreManager.setAutoBackupLastTime(System.currentTimeMillis())

                Logger.i(TAG, "Auto backup completed successfully")
                Result.success()
            } else {
                Logger.e(TAG, "Failed to save backup to Downloads")
                Result.retry()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Auto backup failed: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun createBackupFile(backupDownloaded: Boolean): File {
        val tempFile = File(context.cacheDir, "temp_backup.zip")

        val exported = File(context.cacheDir, "active-library-${java.util.UUID.randomUUID()}.db")
        try {
        commonRepository.exportActiveAccountDatabase(exported.absolutePath)
        FileOutputStream(tempFile).buffered().use { bufferedOutput ->
            ZipOutputStream(bufferedOutput).use { zipOutputStream ->
                // Backup DataStore preferences
                val dataStoreFile = File(context.filesDir, "datastore/$SETTINGS_FILENAME.preferences_pb")
                if (dataStoreFile.exists()) {
                    zipOutputStream.putNextEntry(ZipEntry("$SETTINGS_FILENAME.preferences_pb"))
                    dataStoreFile.inputStream().buffered().use { inputStream ->
                        inputStream.copyTo(zipOutputStream)
                    }
                    zipOutputStream.closeEntry()
                }

                // Checkpoint and backup database
                FileInputStream(exported).use { inputStream ->
                    zipOutputStream.putNextEntry(ZipEntry(DB_NAME))
                    inputStream.copyTo(zipOutputStream)
                    zipOutputStream.closeEntry()
                }

                // Backup downloaded data if enabled
                if (backupDownloaded) {
                    // Backup ExoPlayer database
                    val exoPlayerDb = context.getDatabasePath(EXOPLAYER_DB_NAME)
                    if (exoPlayerDb.exists()) {
                        zipOutputStream.putNextEntry(ZipEntry(EXOPLAYER_DB_NAME))
                        exoPlayerDb.inputStream().buffered().use { inputStream ->
                            inputStream.copyTo(zipOutputStream)
                        }
                        zipOutputStream.closeEntry()
                    }

                    // Backup download folder
                    val downloadFolder = File(context.filesDir, DOWNLOAD_EXOPLAYER_FOLDER)
                    if (downloadFolder.exists() && downloadFolder.isDirectory) {
                        backupFolder(downloadFolder, DOWNLOAD_EXOPLAYER_FOLDER, zipOutputStream)
                    }
                }
            }
        }

        return tempFile
        } finally { exported.delete() }
    }

    private fun backupFolder(
        folder: File,
        baseName: String,
        zipOutputStream: ZipOutputStream,
    ) {
        if (!folder.exists() || !folder.isDirectory) return

        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                val entryName = "$baseName/${file.name}"
                zipOutputStream.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { inputStream ->
                    inputStream.copyTo(zipOutputStream)
                }
                zipOutputStream.closeEntry()
            } else if (file.isDirectory) {
                backupFolder(file, "$baseName/${file.name}", zipOutputStream)
            }
        }
    }

    private fun saveToDownloads(backupFile: File): Boolean {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "gratify_backup_$timestamp.zip"

        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val backupDirectory = legacyBackupDirectory()
                backupFile.copyTo(File(backupDirectory, fileName), overwrite = false)
                Logger.i(TAG, "Backup saved to app Downloads/Gratify/$fileName")
                return true
            }

            saveModernBackup(backupFile, fileName)
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving to Downloads: ${e.message}")
            false
        }
    }

    private fun legacyBackupDirectory(): File {
        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("App downloads directory is unavailable")
        val directory = File(downloads, "Gratify")
        check(directory.isDirectory || directory.mkdirs()) { "Backup directory is unavailable" }
        return directory
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveModernBackup(backupFile: File, fileName: String): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Gratify")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let { outputUri ->
                val saved = context.contentResolver.openOutputStream(outputUri)?.use { output ->
                    backupFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
                if (saved) Logger.i(TAG, "Backup saved to Downloads/Gratify/$fileName")
                else context.contentResolver.delete(outputUri, null, null)
                saved
            } ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving to Downloads: ${e.message}")
            false
        }
    }

    private fun cleanupOldBackups(maxFiles: Int) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                legacyBackupDirectory().listFiles()
                    ?.filter { it.isFile && it.name.startsWith("gratify_backup_") && it.name.endsWith(".zip") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(maxFiles.coerceAtLeast(0))
                    ?.forEach { if (!it.delete()) Logger.e(TAG, "Could not remove old backup: ${it.name}") }
                return
            }

            cleanupModernBackups(maxFiles)
        } catch (e: Exception) {
            Logger.e(TAG, "Error cleaning up old backups: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cleanupModernBackups(maxFiles: Int) {
        try {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_ADDED
            )

            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("Download/Gratify/", "gratify_backup_%.zip")

            val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)

                val backupFiles = mutableListOf<Pair<Long, String>>()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    if (name.startsWith("gratify_backup_") && name.endsWith(".zip")) {
                        backupFiles.add(id to name)
                    }
                }

                // Delete old files if exceeding maxFiles
                if (backupFiles.size > maxFiles) {
                    val filesToDelete = backupFiles.drop(maxFiles)
                    filesToDelete.forEach { (id, name) ->
                        val deleteUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id
                        )
                        context.contentResolver.delete(deleteUri, null, null)
                        Logger.i(TAG, "Deleted old backup: $name")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error cleaning up old backups: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
    }
}
