package com.tan.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tan.data.dataStore.DataStoreManagerImpl
import com.tan.domain.manager.lyricsAnimationsEnabled
import com.tan.domain.manager.setLyricsAnimationsEnabled
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class LyricsMotionPersistenceTest {
    @Test fun lineModeAppliesToNewAndRestoredAnimatedPreferences(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-lyrics-motion")
        val path = directory.resolve("lyrics.preferences_pb")
        var job = SupervisorJob()
        fun open() = DataStoreManagerImpl(PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { path.toString().toPath() }))
        try {
            val original = open()
            assertFalse(original.lyricsAnimationsEnabled.first())
            original.putString("lyrics_motion", "animated")
            assertFalse(original.lyricsAnimationsEnabled.first())
            job.cancelAndJoin(); job = SupervisorJob()
            val reopened = open()
            assertFalse(reopened.lyricsAnimationsEnabled.first())
            reopened.setLyricsAnimationsEnabled(true)
            assertFalse(reopened.lyricsAnimationsEnabled.first())
            assertEquals("no_animation", reopened.getString("lyrics_motion").first())
            reopened.setLyricsAnimationsEnabled(false)
            assertFalse(reopened.lyricsAnimationsEnabled.first())
        } finally { job.cancelAndJoin(); Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }
}
