package com.tan.domain.data.model.metadata

import kotlinx.serialization.Serializable

@Serializable
data class Line(
    val endTimeMs: String,
    val startTimeMs: String,
    val syllables: List<String>? = null,
    val words: String,
    // Stored inside the existing lines JSON; no database column or schema migration.
    val richSyncTextVersion: Int = 0,
    // A matching clean transcript confirmed the spaces. Persisted in the lines JSON.
    val textQualityVersion: Int = 0,
)
