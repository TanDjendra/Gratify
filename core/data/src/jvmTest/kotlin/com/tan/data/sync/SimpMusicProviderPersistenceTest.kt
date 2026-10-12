package com.tan.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tan.data.dataStore.DataStoreManagerImpl
import com.tan.domain.manager.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class SimpMusicProviderPersistenceTest {
    @Test fun upstreamDefaultAndSavedChoicesSurviveReopening(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-simp-provider")
        val path = directory.resolve("lyrics.preferences_pb")
        var job = SupervisorJob()
        fun open() = DataStoreManagerImpl(PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { path.toString().toPath() }))
        try {
            val original = open()
            assertEquals(DataStoreManager.SIMPMUSIC, original.lyricsProvider.first())
            original.setLyricsProvider(DataStoreManager.GRATIFYMUSIC)
            assertEquals(DataStoreManager.SIMPMUSIC, original.lyricsProvider.first())
            original.setLyricsProvider(DataStoreManager.BETTER_LYRICS)
            assertEquals(DataStoreManager.BETTER_LYRICS, original.lyricsProvider.first())
            job.cancelAndJoin(); job = SupervisorJob()
            assertEquals(DataStoreManager.BETTER_LYRICS, open().lyricsProvider.first())
        } finally { job.cancelAndJoin(); Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }
}
