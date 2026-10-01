package com.tan.data.sync

import com.tan.domain.manager.DataStoreManager

/** Retry until every captured choice is on the server; account changes cannot inherit pending choices. */
internal class LegacyProfilePrivacyMigration(private val preferences: DataStoreManager) {
    suspend fun apply(ownerId: String, publish: suspend (String, Boolean) -> Unit) {
        val pending = preferences.captureLegacyProfilePrivacy(ownerId)
        // Send restrictive choices first. A failure retains the entire batch for an idempotent retry.
        pending.entries.sortedBy { it.value }.forEach { publish(it.key, it.value) }
        preferences.acknowledgeLegacyProfilePrivacy(ownerId, pending)
    }
}
