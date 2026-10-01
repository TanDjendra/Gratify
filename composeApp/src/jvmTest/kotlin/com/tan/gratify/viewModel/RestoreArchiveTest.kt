package com.tan.gratify.viewModel

import java.io.*
import java.nio.file.Files
import java.util.zip.*
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class RestoreArchiveTest {
    @Test fun corruptPreferencesAreRejectedBeforeInstallation(): Unit = runBlocking {
        val root = Files.createTempDirectory("gratify-prefs-test").toFile()
        try {
            val file = File(root,"settings.preferences_pb").apply { writeBytes(byteArrayOf(0xff.toByte(),0xff.toByte(),0xff.toByte())) }
            assertFails { validateRestorePreferences(file) }
        } finally { root.deleteRecursively() }
    }
    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
        return bytes.toByteArray()
    }
    private inline fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("gratify-restore-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    @Test fun maliciousAndOversizedArchivesCannotChangeExistingApplicationData() = inDirectory { root ->
        val original = File(root, "live.db").apply { writeText("existing") }
        listOf("../live.db", "/live.db", "downloads/../../live.db", "downloads\\..\\live.db", "C:/live.db").forEachIndexed { i, path ->
            val stage = File(root, "stage$i").apply { mkdir() }
            assertFails { stageRestoreArchive(ByteArrayInputStream(archive(path to "overwritten")), stage, setOf("app.db"), "downloads", 1000) }
            assertEquals("existing", original.readText())
        }
        val stage = File(root, "large").apply { mkdir() }
        assertFails { stageRestoreArchive(ByteArrayInputStream(archive("app.db" to "123456")), stage, setOf("app.db"), "downloads", 5) }
        assertEquals("existing", original.readText())
    }
    @Test fun validArchiveIsStagedAndAnInstallationFailureRestoresAllOriginalFiles() = inDirectory { root ->
        val stage = File(root, "stage").apply { mkdir() }
        val files = stageRestoreArchive(ByteArrayInputStream(archive("app.db" to "new-db", "settings" to "new-settings")), stage, setOf("app.db", "settings"), "downloads", 1000).toMap()
        val db = File(root, "live.db").apply { writeText("old-db") }
        val settings = File(root, "live-settings").apply { writeText("old-settings") }
        assertFails { replaceRestoreFiles(listOf(files.getValue("app.db") to db, File(root, "missing") to settings), File(root, "rollback")) }
        assertEquals("old-db", db.readText()); assertEquals("old-settings", settings.readText())
    }
}
