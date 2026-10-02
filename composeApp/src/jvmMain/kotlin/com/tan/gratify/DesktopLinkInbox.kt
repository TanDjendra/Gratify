package com.tan.gratify

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock

/** Per-user, bounded and short-lived IPC. No bearer tokens in the shared system temp folder. */
internal class DesktopLinkInbox(private val directory: Path, private val clock: Clock = Clock.systemUTC()) {
    companion object {
        const val MAX_URI_LENGTH = 65_536
        private const val MAX_AGE_MS = 5 * 60 * 1000L
    }
    private val pending get() = directory.resolve("pending-uri.txt")

    @Synchronized fun write(uri: String) {
        require(uri.length <= MAX_URI_LENGTH && DesktopDeepLinkHandler.acceptsArgument(uri))
        Files.createDirectories(directory)
        restrictToOwner(directory, true)
        val staging = Files.createTempFile(directory, "pending-", ".tmp")
        try {
            restrictToOwner(staging, false)
            Files.writeString(staging, uri)
            try { Files.move(staging, pending, ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(staging, pending, REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(staging) }
    }

    @Synchronized fun consume(): String? {
        if (!Files.exists(pending)) return null
        val claimed = directory.resolve("received-${java.util.UUID.randomUUID()}.tmp")
        // Claim atomically so consuming an old callback cannot delete a new one
        // that another process writes while this callback is being read.
        try { Files.move(pending, claimed, ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) {
            try { Files.move(pending, claimed) } catch (_: NoSuchFileException) { return null }
        } catch (_: NoSuchFileException) { return null }
        return try {
            val age = clock.millis() - Files.getLastModifiedTime(claimed).toMillis()
            if (age !in 0..MAX_AGE_MS || Files.size(claimed) > MAX_URI_LENGTH * 4L) return null
            Files.readString(claimed).takeIf {
                it.length <= MAX_URI_LENGTH && DesktopDeepLinkHandler.acceptsArgument(it)
            }
        } finally { Files.deleteIfExists(claimed) }
    }

    private fun restrictToOwner(path: Path, directory: Boolean) {
        Files.getFileAttributeView(path, PosixFileAttributeView::class.java)?.let {
            val permissions = mutableSetOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            if (directory) permissions.add(PosixFilePermission.OWNER_EXECUTE)
            it.setPermissions(permissions)
            return
        }
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            ?: error("Owner-only link storage unavailable")
        acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.owner)
            .setPermissions(*AclEntryPermission.values()).build())
    }
}
