package com.tan.domain.utils

import com.eygraber.uri.Uri

private val ProfileId = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** One parser for internal QR scans, old QR schemes, and external profile intents. */
fun parseProfileUserId(value: String, allowRawId: Boolean = true): String? {
    val text = value.trim()
    if (allowRawId && ProfileId.matches(text)) return text.lowercase()
    return try {
        val uri = Uri.parse(text)
        val parts = uri.pathSegments
        val id = when {
            uri.scheme == "https" && uri.host in listOf("gratify.web.id", "gratify.org") && parts.size == 3 && parts.take(2) == listOf("app", "profile") -> parts[2]
            uri.scheme == "gratify" && uri.host == "profile" && parts.size == 1 -> parts[0]
            uri.scheme == "gratify" && uri.host == "app" && parts.size == 2 && parts[0] == "profile" -> parts[1]
            else -> null
        }
        id?.takeIf(ProfileId::matches)?.lowercase()
    } catch (_: IllegalArgumentException) { null }
}

fun profileUrl(userId: String): String {
    require(ProfileId.matches(userId))
    return "https://gratify.web.id/app/profile/${userId.lowercase()}"
}
