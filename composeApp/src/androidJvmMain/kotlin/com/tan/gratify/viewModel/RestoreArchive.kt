package com.tan.gratify.viewModel

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

/** Decode staged preferences before replacing any live file. */
internal suspend fun validateRestorePreferences(file: File) {
    val job = SupervisorJob()
    try {
        val store = PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file.absolutePath.toPath() })
        store.data.first()
    } finally { job.cancelAndJoin() }
}

/** Extract to an isolated directory; no application file changes until the entire ZIP is valid. */
internal fun stageRestoreArchive(
    input: InputStream,
    stagingRoot: File,
    allowedFiles: Set<String>,
    downloadFolder: String,
    maxBytes: Long = minOf(8L * 1024 * 1024 * 1024, stagingRoot.usableSpace),
): List<Pair<String, File>> {
    val root = stagingRoot.canonicalFile
    check(root.isDirectory)
    val seen = mutableSetOf<String>()
    val files = mutableListOf<Pair<String, File>>()
    var bytes = 0L
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            require(name.isNotEmpty() && !name.startsWith('/') && !name.contains('\\') && !name.contains(':')) { "Invalid archive path" }
            require(name.split('/').none { it == ".." || it == "." }) { "Archive traversal rejected" }
            require(seen.add(name) && seen.size <= 100_000) { "Duplicate or excessive archive entries" }
            require(name in allowedFiles || name.startsWith("$downloadFolder/")) { "Unexpected backup entry" }
            val target = File(root, name).canonicalFile
            require(target.path.startsWith(root.path + File.separator)) { "Archive path outside staging directory" }
            if (!entry.isDirectory) {
                check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= maxBytes) { "Backup exceeds available space or restore limit" }
                        output.write(buffer, 0, count)
                    }
                }
                files += name to target
            }
            zip.closeEntry()
        }
    }
    require(files.isNotEmpty()) { "Empty backup" }
    return files
}

/** Failed replacement restores every original file/directory, including absent destinations. */
internal fun replaceRestoreFiles(replacements: List<Pair<File, File>>, rollbackRoot: File) {
    check(rollbackRoot.mkdirs() || rollbackRoot.isDirectory)
    val applied = mutableListOf<Triple<File, File, Boolean>>()
    try {
        replacements.forEachIndexed { index, (source, target) ->
            val backup = File(rollbackRoot, index.toString())
            val existed = target.exists()
            if (existed) check(target.renameTo(backup)) { "Cannot preserve existing data" }
            applied += Triple(target, backup, existed)
            check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
            check(source.renameTo(target)) { "Cannot install staged backup" }
        }
    } catch (failure: Exception) {
        applied.asReversed().forEach { (target, backup, existed) ->
            try {
                if (target.exists()) check(target.deleteRecursively()) { "Cannot roll back restored data" }
                if (existed) check(backup.renameTo(target)) { "Cannot restore original data" }
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
        }
        throw failure
    }
}
