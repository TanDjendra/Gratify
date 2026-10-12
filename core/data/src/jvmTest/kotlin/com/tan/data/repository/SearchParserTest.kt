package com.tan.data.repository

import com.tan.data.parser.search.*
import com.tan.kotlinytmusicscraper.models.*
import com.tan.kotlinytmusicscraper.pages.SearchResult
import kotlin.test.*

class SearchParserTest {
    @Test fun unexpectedCategoriesCannotDiscardTheValidRows() {
        val page = SearchResult(listOf(SongItem("s", "Song", emptyList(), thumbnail = "image"),
            ArtistItem("a", "Artist", "image", null, null)), emptyList())
        assertEquals(listOf("s"), parseSearchSong(page).map { it.videoId })
        assertEquals(listOf("s"), parseSearchVideo(page).map { it.videoId })
        assertEquals(listOf("a"), parseSearchArtist(page).map { it.browseId })
        assertTrue(parseSearchAlbum(page).isEmpty()); assertTrue(parseSearchPlaylist(page).isEmpty())
    }
    @Test fun missingOptionalSongMetadataStillProducesAnUsableResult() {
        val page = SearchResult(listOf(SongItem("s", "Song", emptyList(), thumbnail = "image")), emptyList())
        assertEquals("s", parseSearchSong(page).single().videoId); assertEquals(0, parseSearchSong(page).single().durationSeconds)
        assertEquals("s", parseSearchVideo(page).single().videoId)
    }
}
