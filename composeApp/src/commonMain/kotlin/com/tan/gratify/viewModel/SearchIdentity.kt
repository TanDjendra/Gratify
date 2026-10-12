package com.tan.gratify.viewModel

import com.tan.domain.data.type.SearchResultType
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.data.model.searchResult.videos.VideosResult
import com.tan.domain.data.model.searchResult.albums.AlbumsResult
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult

fun SearchResultType.searchIdentity(): String = "${objectType()}:" + when (this) {
    is SongsResult -> videoId
    is VideosResult -> videoId
    is AlbumsResult -> browseId
    is ArtistsResult -> browseId
    is PlaylistsResult -> browseId
    else -> toString()
}
