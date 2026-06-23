package com.tan.gratifymusic.expect

actual fun getDownloadFolderPath(): String = System.getProperty("user.home") + "/Downloads"