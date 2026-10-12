package com.tan.data.io

import com.tan.data.db.documentDirectory
import okio.FileSystem

actual fun fileSystem(): FileSystem = FileSystem.SYSTEM
actual fun fileDir(): String = documentDirectory() + "/Gratify"
actual fun cacheDir(): String = platform.Foundation.NSTemporaryDirectory() + "/Gratify"
