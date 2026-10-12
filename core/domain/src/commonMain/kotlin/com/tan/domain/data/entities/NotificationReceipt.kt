package com.tan.domain.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kept after a card is deleted so polling cannot recreate the same event. */
@Entity(tableName = "notification_receipt")
data class NotificationReceipt(@PrimaryKey val eventKey: String)

fun NotificationEntity.eventKey(): String {
    val parts = when (channelId) {
        "developer" -> listOf(channelId, name, single.firstOrNull()?.get("title").orEmpty(),
            single.firstOrNull()?.get("browseId").orEmpty())
        "follower_activity" -> listOf(channelId, single.firstOrNull()?.get("ownerId").orEmpty(),
            single.firstOrNull()?.get("browseId").orEmpty())
        else -> listOf(channelId) + single.map { "single:${it["browseId"]}" }.sorted() +
            album.map { "album:${it["browseId"]}" }.sorted()
    }
    return parts.joinToString("") { "${it.length}:$it" }
}
