package com.tan.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.tan.common.DB_NAME
import com.tan.data.io.getHomeFolderPath
import java.io.File

actual fun getDatabaseBuilder(
    converters: Converters
): RoomDatabase.Builder<MusicDatabase> {
    return Room.databaseBuilder<MusicDatabase>(
        name = getDatabasePath()
    ).addTypeConverter(converters)
}

actual fun getDatabasePath(): String {
    val dbFile = File(getHomeFolderPath(listOf(".gratify", "db")), DB_NAME)
    return dbFile.absolutePath
}