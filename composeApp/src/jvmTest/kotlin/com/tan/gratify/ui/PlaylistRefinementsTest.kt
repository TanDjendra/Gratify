package com.tan.gratify.ui

import com.tan.gratify.ui.component.automaticArtworkCells
import com.tan.gratify.viewModel.suggestedPlaylistName
import kotlin.test.*

class PlaylistRefinementsTest {
    @Test fun fourStoredSongsProvideFourCellsAndReorderingChangesTheCover() {
        assertEquals(listOf("a","b","c","d"),automaticArtworkCells(listOf("a","b","c","d","e")))
        assertEquals(listOf("e","a","b","c"),automaticArtworkCells(listOf("e","a","b","c","d")))
    }
    @Test fun shortPlaylistsUseTheTopSongAndEmptyPlaylistsUseThePlaceholder() {
        assertEquals(emptyList(),automaticArtworkCells(emptyList()))
        for(count in 1..3)assertEquals(listOf("a"),automaticArtworkCells(listOf("a","b","c").take(count)))
    }
    @Test fun numberingFillsHolesRatherThanUsingCollectionCount() {
        val titles=listOf("Playlist-ku No.1","Playlist-ku No.3","Playlist-ku No.21","Galau","Folder baru")
        assertEquals("Playlist-ku No.2",suggestedPlaylistName("personal",titles))
        assertEquals("Playlist-ku No.1",suggestedPlaylistName("personal",titles.drop(1)))
    }
    @Test fun sharedNamesAndOtherCollectionKindsDoNotConsumePersonalNumbers() {
        val titles=listOf("Playlist kami No.1","Playlist kami No.3","Blend kami No.24","Playlist-ku No.1")
        assertEquals("Playlist kami No.2",suggestedPlaylistName("collaborative",titles))
        assertEquals("Playlist-ku No.2",suggestedPlaylistName("personal",titles))
        assertEquals("Blend kami",suggestedPlaylistName("blend",titles))
        assertEquals("Folder baru",suggestedPlaylistName("folder",titles))
    }
    @Test fun renamedPlaylistsAndCaseVariantsAreHandledWithoutRenamingOldItems() {
        val titles=listOf(" playlist-KU no. 1 ","Bekerja","Playlist-ku No.2 lama")
        assertEquals("Playlist-ku No.2",suggestedPlaylistName("personal",titles))
        assertEquals(" playlist-KU no. 1 ",titles.first())
    }
}
