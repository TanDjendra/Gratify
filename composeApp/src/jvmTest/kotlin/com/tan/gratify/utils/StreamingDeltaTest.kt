package com.tan.gratify.utils

import java.io.*
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.*

class StreamingDeltaTest {
    private fun fixture(sourceSize: Long = 4, targetSize: Long = 6, commands: Long = 1, body: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeBytes("GRTFDLT1"); writeLong(sourceSize); writeLong(targetSize); writeLong(commands) }
        DataOutputStream(GZIPOutputStream(bytes)).use(body)
        return bytes.toByteArray()
    }
    private fun runPatch(bytes: ByteArray, expectedSize: Long = 6, check: () -> Unit = {}): ByteArray {
        val root = Files.createTempDirectory("delta-test").toFile()
        try {
            val source = File(root, "source").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val patch = File(root, "patch").apply { writeBytes(bytes) }
            val result = File(root, "result")
            try { StreamingDelta.apply(source, patch, result, expectedSize, check) }
            catch (error: Exception) { assertFalse(result.exists()); assertContentEquals(byteArrayOf(1, 2, 3, 4), source.readBytes()); throw error }
            return result.readBytes()
        } finally { root.deleteRecursively() }
    }
    private fun good() = fixture { it.writeLong(0); it.writeLong(4); it.writeLong(2); it.write(byteArrayOf(0, 1, -1, 0, 9, 8)) }
    @Test fun exactDiffAndExtraReconstruction() { assertContentEquals(byteArrayOf(1, 3, 2, 4, 9, 8), runPatch(good())) }
    @Test fun negativeSourceOffsetUsesZeroOutsideOldFile() {
        assertContentEquals(byteArrayOf(5, 6, 1, 2, 3, 4), runPatch(fixture { it.writeLong(-2); it.writeLong(6); it.writeLong(0); it.write(byteArrayOf(5, 6, 0, 0, 0, 0)) }))
    }
    @Test fun sourceOffsetBeyondEndUsesZero() {
        assertContentEquals(byteArrayOf(4, 3, 2, 1, 8, 9), runPatch(fixture { it.writeLong(4); it.writeLong(6); it.writeLong(0); it.write(byteArrayOf(4, 3, 2, 1, 8, 9)) }))
    }
    @Test fun truncatedPatchDeletesPartialOutput() { for (length in listOf(0, 8, 31, 38, good().size - 3)) assertFails { runPatch(good().copyOf(length)) } }
    @Test fun corruptGzipCrcDeletesOutput() { val bytes = good(); bytes[bytes.size - 8] = (bytes[bytes.size - 8].toInt() xor 1).toByte(); assertFails { runPatch(bytes) } }
    @Test fun invalidHeadersCannotAllocateLargeBuffers() {
        for (bytes in listOf(fixture(5) {}, fixture(targetSize = Long.MAX_VALUE) {}, fixture(commands = Long.MAX_VALUE) {}, fixture(commands = 0) {})) assertFails { runPatch(bytes) }
    }
    @Test fun overflowNegativeAndNonProgressingCommandsAreRejected() {
        for ((offset, x, y) in listOf(Triple(0L, -1L, 7L), Triple(0L, 0L, 0L), Triple(Long.MAX_VALUE, 1L, 5L), Triple(0L, Long.MAX_VALUE, 0L), Triple(0L, 1L, Long.MAX_VALUE))) {
            assertFails { runPatch(fixture { it.writeLong(offset); it.writeLong(x); it.writeLong(y) }) }
        }
    }
    @Test fun incompleteOrTrailingOutputIsRejected() {
        assertFails { runPatch(fixture { it.writeLong(0); it.writeLong(4); it.writeLong(0); it.write(byteArrayOf(0, 0, 0, 0)) }) }
        assertFails { runPatch(fixture { it.writeLong(0); it.writeLong(4); it.writeLong(2); it.write(byteArrayOf(0, 0, 0, 0, 1, 2, 3)) }) }
    }
    @Test fun cancellationPreservesInstalledSourceAndDeletesOutput() {
        var calls = 0
        assertFailsWith<InterruptedIOException> { runPatch(good()) { if (++calls >= 3) throw InterruptedIOException("cancel") } }
    }
    @Test fun outputCannotOverwriteSourceOrPatch() {
        val root = Files.createTempDirectory("delta-alias").toFile()
        try {
            val source = File(root, "source").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val patch = File(root, "patch").apply { writeBytes(good()) }
            assertFails { StreamingDelta.apply(source, patch, source, 6) }
            assertFails { StreamingDelta.apply(source, patch, patch, 6) }
            assertEquals(4L, source.length()); assertContentEquals(good(), patch.readBytes())
        } finally { root.deleteRecursively() }
    }
}

/** Runs the exact production applier in a heap-limited JVM for the local audit, never an installer. */
object DeltaAuditRunner {
    @JvmStatic fun main(args: Array<String>) {
        val start = System.nanoTime()
        val output = File(args[2])
        StreamingDelta.apply(File(args[0]), File(args[1]), output, args[3].toLong())
        check(StreamingDelta.hash(output) == args[4])
        println("Reconstruction verified; milliseconds=${(System.nanoTime() - start) / 1_000_000}; maxHeapBytes=${Runtime.getRuntime().maxMemory()}")
    }
}
