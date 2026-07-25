package com.tan.gratify.utils

/**
 * Kompres gambar ke ByteArray sebelum diupload ke server.
 * Memastikan ukuran gambar menjadi lebih kecil (kisaran 100-300kb) jika memungkinkan,
 * dan mengonversi format ke WebP atau JPEG.
 */
expect suspend fun compressImage(filePath: String): ByteArray?
