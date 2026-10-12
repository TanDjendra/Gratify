package com.tan.domain.utils

import com.eygraber.uri.Uri
import com.tan.domain.data.entities.NotificationEntity

fun notificationWebLink(value: String?): String? = value?.trim()?.takeIf { raw ->
    raw.none { it.isWhitespace() || it.code < 32 } && runCatching {
        val uri = Uri.parse(raw)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

fun notificationSocialLink(row: NotificationEntity): String? {
    if (row.channelId !in setOf("social_story", "social_message")) return null
    val details = row.single.firstOrNull().orEmpty()
    val owner = parseProfileUserId(details["ownerId"].orEmpty()) ?: return null
    val key = if (row.channelId == "social_story") "sourceId" else "actorId"
    val target = parseProfileUserId(details[key].orEmpty()) ?: return null
    return "gratify://friends?${if (key == "sourceId") "story" else "peer"}=$target&owner=$owner"
}
