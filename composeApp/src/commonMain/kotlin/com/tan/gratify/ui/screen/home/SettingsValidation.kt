package com.tan.gratify.ui.screen.home

import kotlinx.serialization.json.*

internal fun validSettingsApiUrl(value: String): Boolean {
    if (value.isBlank()) return true
    return Regex("^https?://[A-Za-z0-9\\[\\]:.-]+(?:/[^\\s]*)?$").matches(value.trim())
}

internal fun validSettingsHeaders(value: String): Boolean {
    if (value.isBlank()) return true
    val headers = runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull() ?: return false
    return headers.all { (key, value) -> key.isNotBlank() && '\r' !in key && '\n' !in key &&
        value is JsonPrimitive && value.isString && '\r' !in value.content && '\n' !in value.content }
}
