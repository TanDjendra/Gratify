package com.tan.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tan.data.dataStore.DataStoreManagerImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryPreferencesPersistenceTest {
    @Test fun preferencesSurviveClosingAndReopeningTheStore(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-library-preferences")
        val file = directory.resolve("library.preferences_pb")
        val choices = mapOf("library_filter" to "Playlist", "library_layout" to "grid", "library_sort" to "ABJAD",
            "library_pinned_playlists" to "12,34")
        var storeJob = SupervisorJob()
        fun openStore() = DataStoreManagerImpl(PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + storeJob), produceFile = { file.toString().toPath() },
        ))
        try {
            val firstStore = openStore()
            choices.forEach { (key, value) -> firstStore.putString(key, value) }
            storeJob.cancelAndJoin()
            storeJob = SupervisorJob()
            val reopenedStore = openStore()
            choices.forEach { (key, value) -> assertEquals(value, reopenedStore.getString(key).first()) }
        } finally {
            storeJob.cancelAndJoin()
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
