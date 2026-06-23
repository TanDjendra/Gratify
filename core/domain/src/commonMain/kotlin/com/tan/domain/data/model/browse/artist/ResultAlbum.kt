package com.tan.domain.data.model.browse.artist

import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.domain.data.type.HomeContentType

data class ResultAlbum(
    val browseId: String,
    val isExplicit: Boolean,
    val thumbnails: List<Thumbnail>,
    val title: String,
    val year: String,
) : HomeContentType