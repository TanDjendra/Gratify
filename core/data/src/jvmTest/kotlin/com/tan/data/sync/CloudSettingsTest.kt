package com.tan.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.tan.data.dataStore.DataStoreManagerImpl
import com.tan.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.*

internal class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(state.value).also { state.value = it }
}

class CloudSettingsTest {
    @Test fun accountPreferenceCleanupRemovesPrivateTokenCachesAndKeepsPlaybackSettings(): Unit = runBlocking {
        val manager = DataStoreManagerImpl(MemoryPreferences())
        manager.setSpotifyPersonalToken("synthetic-private-token"); manager.setSpotifyPersonalTokenExpires(999)
        manager.setSpdc("synthetic-cookie"); manager.setDiscordToken("synthetic-discord-token"); manager.setPlaybackSpeed(1.5f)
        manager.clearPerUserData()
        assertEquals("",manager.spotifyPersonalToken.first()); assertEquals(0L,manager.spotifyPersonalTokenExpires.first())
        assertEquals("",manager.spdc.first()); assertEquals("",manager.discordToken.first()); assertEquals(1.5f,manager.playbackSpeed.first())
    }
    @Test fun typedSettingsRoundTripAndUnknownOrInvalidValuesCannotOverwritePreferences(): Unit = runBlocking {
        val prefs = MemoryPreferences(); val manager = DataStoreManagerImpl(prefs)
        manager.setPlayerVolume(0.6f); manager.setPlaybackSpeed(1.5f); manager.setPitch(-2)
        manager.recoverShuffleAndRepeatKey(true, 2)
        val exported = manager.cloudSettings()
        val restoredPrefs = MemoryPreferences(); val restored = DataStoreManagerImpl(restoredPrefs)
        restored.restoreCloudSettings(exported)
        assertEquals(exported, restored.cloudSettings())
        restored.restoreCloudSettings(mapOf("playerVolume" to "NaN", "playbackSpeed" to "99", "pitch" to "999", "auth_token" to "secret"))
        assertEquals(0.6f, restored.playerVolume.first()); assertEquals(1.5f, restored.playbackSpeed.first()); assertEquals(-2, restored.pitch.first())
        assertEquals(DataStoreManager.TRUE, restored.shuffleKey.first()); assertEquals(DataStoreManager.REPEAT_ALL, restored.repeatKey.first())
        assertFalse(restoredPrefs.data.first().asMap().keys.any { it.name == "auth_token" })
    }
    @Test fun postgresOffsetAndUtcTimesRestoreTheOriginalLocalHistoryDate() {
        val zone = TimeZone.of("Asia/Jakarta"); val date = LocalDateTime.parse("2026-10-01T08:15:30")
        assertEquals("2026-10-01T01:15:30Z", date.toCloudTimestamp(zone))
        assertEquals(date, "2026-10-01T01:15:30+00:00".fromCloudTimestamp(zone))
        assertEquals(date, date.toCloudTimestamp(zone).fromCloudTimestamp(zone))
    }
}
