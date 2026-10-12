package com.tan.gratify.utils

import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Versioned BSDiff commands, interleaved inside one gzip stream. Never buffers an APK. */
object StreamingDelta {
    const val MAX_APK_SIZE = 300L * 1024 * 1024
    private const val BLOCK = 64 * 1024
    private const val MAX_COMMANDS = 1_000_000L

    fun hash(file: File, check: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(BLOCK)
            while (true) {
                check()
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun apply(source: File, patch: File, output: File, expectedSize: Long, check: () -> Unit = {}) {
        require(expectedSize in 1..MAX_APK_SIZE && source.length() in 1..MAX_APK_SIZE)
        require(output.canonicalFile != source.canonicalFile && output.canonicalFile != patch.canonicalFile)
        val started = System.nanoTime()
        fun checkpoint() {
            check()
            kotlin.check(System.nanoTime() - started < 120_000_000_000L) { "Penyiapan pembaruan terlalu lama" }
        }
        try {
            RandomAccessFile(source, "r").use { old ->
                DataInputStream(patch.inputStream().buffered(BLOCK)).use { raw ->
                    val magic = ByteArray(8); raw.readFully(magic)
                    require(magic.contentEquals("GRTFDLT1".toByteArray(Charsets.US_ASCII))) { "Format patch tidak didukung" }
                    require(raw.readLong() == old.length()) { "Ukuran APK sumber berubah" }
                    require(raw.readLong() == expectedSize) { "Ukuran APK hasil tidak sesuai" }
                    val commands = raw.readLong()
                    require(commands in 1..minOf(MAX_COMMANDS, expectedSize)) { "Jumlah instruksi patch tidak valid" }
                    DataInputStream(GZIPInputStream(raw, BLOCK)).use { input ->
                        output.outputStream().buffered(BLOCK).use { target ->
                            val diff = ByteArray(BLOCK); val base = ByteArray(BLOCK)
                            var produced = 0L
                            repeat(commands.toInt()) {
                                checkpoint()
                                val offset = input.readLong(); val diffSize = input.readLong(); val extraSize = input.readLong()
                                require(offset in -MAX_APK_SIZE..MAX_APK_SIZE && diffSize >= 0 && extraSize >= 0)
                                require(diffSize <= expectedSize - produced && extraSize <= expectedSize - produced - diffSize)
                                require(diffSize + extraSize > 0) { "Instruksi patch tidak menghasilkan data" }
                                var position = offset; var remaining = diffSize
                                while (remaining > 0) {
                                    checkpoint()
                                    val count = minOf(BLOCK.toLong(), remaining).toInt()
                                    input.readFully(diff, 0, count)
                                    base.fill(0, 0, count)
                                    // BSDiff defines bytes beyond either end of the old file as zero.
                                    val first = maxOf(0L, position)
                                    val end = minOf(old.length(), position + count)
                                    if (end > first) {
                                        old.seek(first)
                                        old.readFully(base, (first - position).toInt(), (end - first).toInt())
                                    }
                                    for (i in 0 until count) diff[i] = (diff[i].toInt() + base[i].toInt()).toByte()
                                    target.write(diff, 0, count)
                                    position += count; remaining -= count
                                }
                                remaining = extraSize
                                while (remaining > 0) {
                                    checkpoint()
                                    val count = minOf(BLOCK.toLong(), remaining).toInt()
                                    input.readFully(diff, 0, count); target.write(diff, 0, count); remaining -= count
                                }
                                produced += diffSize + extraSize
                            }
                            require(produced == expectedSize) { "APK hasil belum lengkap" }
                            require(input.read() == -1) { "Patch berisi data tambahan" } // Also validates gzip CRC/trailer.
                            checkpoint()
                        }
                    }
                }
            }
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }
}
