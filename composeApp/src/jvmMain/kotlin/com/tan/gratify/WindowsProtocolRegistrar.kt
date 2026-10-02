package com.tan.gratify

import com.tan.logger.Logger

/**
 * Registers the "gratify://" custom URI protocol handler in Windows Registry
 * under HKEY_CURRENT_USER (no admin rights required).
 *
 * Registry structure:
 * ```
 * HKCU\Software\Classes\gratify
 *     (Default) = "URL:Gratify Protocol"
 *     URL Protocol = ""
 *     \DefaultIcon
 *         (Default) = "\"<exe_path>\",0"
 *     \shell\open\command
 *         (Default) = "\"<exe_path>\" \"%1\""
 * ```
 */
object WindowsProtocolRegistrar {
    private const val TAG = "WindowsProtocolRegistrar"
    internal val schemes = listOf("gratify", "com.tan.gratify")

    fun register() {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return
        // Used by isolated diagnostics; installed applications register by default.
        if (System.getProperty("gratify.protocol.register", "true") != "true") return

        val exePath = resolveExePath() ?: run {
            Logger.e(TAG, "Could not resolve executable path, skipping protocol registration")
            return
        }

        try {
            schemes.forEach { scheme ->
                val key = "HKCU\\Software\\Classes\\$scheme"
                if (!isAlreadyRegistered(key, exePath)) {
                    regAdd(key, null, "URL:Gratify Protocol")
                    regAdd(key, "URL Protocol", "")
                    regAdd("$key\\DefaultIcon", null, "\"$exePath\",0")
                    regAdd("$key\\shell\\open\\command", null, commandValue(exePath))
                }
            }

            Logger.d(TAG, "Protocol handler registered successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register protocol handler: ${e.message}")
        }
    }

    internal fun commandValue(exePath: String): String = "\"$exePath\" \"%1\""

    private fun isAlreadyRegistered(key: String, currentExePath: String): Boolean {
        return try {
            val result = regQuery("$key\\shell\\open\\command", null)
            // Registry stores path with quotes: "C:\path\to\Gratify.exe" "%1"
            // Normalize both for comparison
            result?.trim()?.endsWith(commandValue(currentExePath), ignoreCase = true) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveExePath(): String? {
        // JPackage directory structure:
        //   <app>/runtime/...  (java.home points here)
        //   <app>/Gratify.exe
        // So we go: java.home → parent (runtime) → parent (app) → Gratify.exe
        val javaHome = System.getProperty("java.home") ?: return null
        val javaHomeDir = java.io.File(javaHome)

        // Try JPackage layout: java.home is <app>/runtime/... or <app>/runtime
        val appDir = if (javaHomeDir.name == "runtime") {
            javaHomeDir.parentFile
        } else {
            // java.home might be deeper, e.g., <app>/runtime/conf/...
            generateSequence(javaHomeDir) { it.parentFile }
                .firstOrNull { it.name == "runtime" }
                ?.parentFile
        }

        if (appDir != null) {
            val exeFile = java.io.File(appDir, "Gratify.exe")
            if (exeFile.exists()) {
                return exeFile.absolutePath
            }
        }

        // A development java.exe/javaw.exe cannot launch this app by URI. Do not
        // replace the installed handler with a broken development command.
        return null
    }

    private fun regAdd(key: String, valueName: String?, data: String) {
        val process = ProcessBuilder(registrationCommand(key, valueName, data))
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) error("Protocol registration failed (exit=$exitCode)")
    }

    internal fun registrationCommand(key: String, valueName: String?, data: String): List<String> {
        val command = mutableListOf("reg.exe", "add", key, "/f")
        if (valueName == null) command.add("/ve") else command.addAll(listOf("/v", valueName))
        // reg.exe parses a Windows command line. Prevent Java from treating the
        // quoted executable inside the value as quotes around the whole argument.
        command.addAll(listOf("/t", "REG_SZ", "/d", data.replace("\"", "\\\"")))
        return command
    }

    private fun regQuery(key: String, valueName: String?): String? {
        val command = mutableListOf("reg", "query", key)
        if (valueName != null) {
            command.addAll(listOf("/v", valueName))
        } else {
            command.add("/ve")
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return if (exitCode == 0) output else null
    }
}
