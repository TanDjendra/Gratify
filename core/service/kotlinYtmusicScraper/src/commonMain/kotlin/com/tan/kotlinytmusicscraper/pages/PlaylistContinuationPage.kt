package com.tan.kotlinytmusicscraper.pages

import com.tan.kotlinytmusicscraper.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)