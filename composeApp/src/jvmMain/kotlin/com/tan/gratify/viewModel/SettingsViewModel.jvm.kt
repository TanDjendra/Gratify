package com.tan.gratify.viewModel

import com.eygraber.uri.Uri
import com.tan.common.DB_NAME
import com.tan.common.SETTINGS_FILENAME
import com.tan.data.io.getHomeFolderPath
import com.tan.domain.repository.CacheRepository
import com.tan.domain.repository.CommonRepository
import com.tan.logger.Logger
import com.tan.gratify.extension.zipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.getString
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.restore_success
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? = null

actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    val stage = java.nio.file.Files.createTempDirectory("gratify-restore-").toFile()
    var databaseClosed = false
    var success = false
    try {
        withContext(Dispatchers.IO) {
            val parsed = java.net.URI(uri.toString())
            val source = if (parsed.scheme == "file") File(parsed) else File(uri.toString())
            val files = stageRestoreArchive(source.inputStream(), stage, setOf(DB_NAME, "$SETTINGS_FILENAME.preferences_pb"), "downloads")
            files.filter { it.first == "$SETTINGS_FILENAME.preferences_pb" }.forEach { validateRestorePreferences(it.second) }
            files.filter { it.first == DB_NAME }.forEach { (_, file) ->
                androidx.sqlite.driver.bundled.BundledSQLiteDriver().open(file.absolutePath).use { db ->
                    db.prepare("PRAGMA quick_check").use { statement ->
                        check(statement.step() && statement.getText(0) == "ok") { "Invalid backup database" }
                    }
                }
            }
            require(files.none { it.first.startsWith("downloads/") }) { "Desktop backup contains unsupported download data" }
            val replacements = files.map { (name, staged) -> staged to if (name == DB_NAME)
                File(requireNotNull(commonRepository.getDatabasePath())) else File(getHomeFolderPath(listOf(".gratify")), name) }
            commonRepository.databaseDaoCheckpoint(); commonRepository.closeDatabase(); databaseClosed = true
            replaceRestoreFiles(replacements, File(stage,"rollback"))
            replacements.map { it.second }.filter { it.name == DB_NAME }.forEach { db ->
                File(db.path + "-wal").delete(); File(db.path + "-shm").delete()
            }
            success = true
        }
    } finally {
        if (!databaseClosed || success) stage.deleteRecursively()
        if (databaseClosed) withContext(Dispatchers.Main) {
            showToast(if (success) "Restore complete. Reopen Gratify." else "Restore failed. Original data was preserved; reopen Gratify.", ToastGravity.Bottom)
            exitProcess(0)
        }
    }
}

actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) = kotlinx.coroutines.withContext(Dispatchers.IO) {
    val exported = File.createTempFile("gratify-active-library-", ".db")
    check(exported.delete()) // VACUUM INTO requires a destination that does not exist.
    try {
        commonRepository.exportActiveAccountDatabase(exported.absolutePath)
        val parsed = java.net.URI(uri.toString())
        val destination = if (parsed.scheme == "file") File(parsed) else File(uri.toString())
        ZipOutputStream(FileOutputStream(destination).buffered()).use { output ->
            fun append(file: File, name: String) {
                if (!file.isFile) return
                output.putNextEntry(ZipEntry(name))
                file.inputStream().buffered().use { it.copyTo(output) }
                output.closeEntry()
            }
            append(File(getHomeFolderPath(listOf(".gratify")), "$SETTINGS_FILENAME.preferences_pb"), "$SETTINGS_FILENAME.preferences_pb")
            append(exported, DB_NAME)
        }
    } finally { exported.delete() }
}

actual fun getPackageName(): String = ""

actual fun getFileDir(): String = ""

actual fun changeLanguageNative(code: String) {
    Locale.setDefault(
        Locale.forLanguageTag(
            if (code == "id-ID") {
                "in-ID"
            } else {
                code
            },
        ),
    )
}