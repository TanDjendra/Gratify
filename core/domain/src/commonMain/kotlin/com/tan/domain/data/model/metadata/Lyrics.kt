package com.tan.domain.data.model.metadata

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val error: Boolean = false,
    val lines: List<Line>?,
    val syncType: String?,
    val gratifyMusicLyrics: GratifyMusicLyrics? = null,
)

@Serializable
data class GratifyMusicLyrics(
    val id: String,
    val vote: Int,
)