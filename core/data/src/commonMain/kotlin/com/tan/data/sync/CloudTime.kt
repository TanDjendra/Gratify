package com.tan.data.sync

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant

internal fun LocalDateTime.toCloudTimestamp(zone: TimeZone = TimeZone.currentSystemDefault()): String = toInstant(zone).toString()

/** Postgres TIMESTAMPTZ includes an offset. Legacy values without one are local times. */
internal fun String?.fromCloudTimestamp(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { Instant.parse(value).toLocalDateTime(zone) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
}
