package com.tan.kotlinytmusicscraper.pages

import com.tan.kotlinytmusicscraper.models.AlbumItem
import com.tan.kotlinytmusicscraper.models.VideoItem

data class ExplorePage(
    val released: List<AlbumItem>,
    val musicVideo: List<VideoItem>,
)