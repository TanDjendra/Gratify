package com.tan.domain.data.model.mood.genre

import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.domain.data.type.HomeContentType

data class Content(
    val playlistBrowseId: String,
    val thumbnail: List<Thumbnail>?,
    val title: Title,
) : HomeContentType