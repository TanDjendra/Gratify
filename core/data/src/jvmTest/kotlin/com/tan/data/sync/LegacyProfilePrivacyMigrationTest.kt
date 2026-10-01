package com.tan.data.sync

import com.tan.data.dataStore.DataStoreManagerImpl
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LegacyProfilePrivacyMigrationTest {
    @Test fun accountSwitchKeepsOutgoingPrivacyAndCannotApplyItToTheIncomingAccount(): Unit = runBlocking {
        val preferences = DataStoreManagerImpl(MemoryPreferences())
        preferences.putString("last_synced_user_id", "A")
        preferences.putString("privacy_show_playlist", "FALSE")
        preferences.putString("privacy_show_followers", "TRUE")
        preferences.clearPerUserData()
        preferences.putString("last_synced_user_id", "B")
        val migration = LegacyProfilePrivacyMigration(preferences)
        migration.apply("B") { _, _ -> fail("A's preferences reached B") }
        val applied = linkedMapOf<String, Boolean>()
        migration.apply("A") { key, value -> applied[key] = value }
        assertEquals(mapOf("show_playlists" to false, "show_followers" to true), applied)
        migration.apply("A") { _, _ -> fail("Acknowledged migration was repeated") }
    }

    @Test fun failedUploadPreservesRestrictiveChoicesForRetryBeforeServerPreferencesOverwriteThem(): Unit = runBlocking {
        val preferences = DataStoreManagerImpl(MemoryPreferences())
        preferences.putString("last_synced_user_id", "A")
        preferences.putString("privacy_show_recent_artists", "FALSE")
        preferences.putString("privacy_show_followers", "TRUE")
        val migration = LegacyProfilePrivacyMigration(preferences)
        assertFails { migration.apply("A") { _, _ -> error("Offline") } }
        assertEquals(false, preferences.captureLegacyProfilePrivacy("A")["show_recent_artists"])
        // A later screen read cannot replace a pending false choice with a server default.
        preferences.putString("privacy_show_recent_artists", "TRUE")
        val applied = mutableListOf<Pair<String, Boolean>>()
        migration.apply("A") { key, value -> applied += key to value }
        assertEquals("show_recent_artists" to false, applied.first())
        assertEquals(2, applied.size)
        assertTrue(preferences.captureLegacyProfilePrivacy("A").isEmpty())
    }
}
