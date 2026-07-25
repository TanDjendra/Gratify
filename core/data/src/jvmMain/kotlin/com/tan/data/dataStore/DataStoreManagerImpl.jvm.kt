package com.tan.data.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tan.common.SETTINGS_FILENAME
import com.tan.data.io.getHomeFolderPath
import createDataStore
import java.io.File

actual fun createDataStoreInstance(): DataStore<Preferences> = createDataStore(
    producePath = {
        val file = File(getHomeFolderPath(listOf(".gratify")), "$SETTINGS_FILENAME.preferences_pb")
        file.absolutePath
    }
)