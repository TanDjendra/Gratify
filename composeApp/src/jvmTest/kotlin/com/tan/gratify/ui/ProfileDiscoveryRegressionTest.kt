package com.tan.gratify.ui

import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.data.model.home.Content
import com.tan.domain.data.model.home.HomeItem
import com.tan.domain.utils.uploadedProfileAvatar
import com.tan.gratify.ui.utils.appendDiscovery
import com.tan.gratify.ui.utils.profilePlaylistIds
import com.tan.gratify.ui.utils.nextDiscoverySeed
import com.tan.gratify.ui.utils.DiscoverySeedKind
import kotlin.test.*

class ProfileDiscoveryRegressionTest {
    @Test fun profileSurvivesCloudRestoreWithDifferentLocalIds() {
        val local = listOf(LocalPlaylistEntity(id = 97, title = "Senja", syncId = "stable-id"),
            LocalPlaylistEntity(id = 12, title = "Lain", syncId = "other-id"))
        val cloud = listOf(CloudPlaylistDto(localPlaylistId = 12, title = "Senja", clientSyncId = "stable-id", isPublic = true))
        assertEquals(setOf(97L), profilePlaylistIds(local, cloud))
    }

    @Test fun missingLegacyRowIdsDoNotHidePublishedPlaylists() {
        val local = listOf(LocalPlaylistEntity(id = 7, title = "Senja", syncId = "stable-id"))
        val cloud = listOf(CloudPlaylistDto(title = "Senja", clientSyncId = "stable-id", isPublic = true))
        assertEquals(setOf(7L), profilePlaylistIds(local, cloud))
        assertEquals(setOf(7L), profilePlaylistIds(local, listOf(CloudPlaylistDto(localPlaylistId = 7, title = "Senja", isPublic = true))))
    }

    @Test fun googlePhotosCannotReplaceAnUploadedAvatar() {
        assertNull("https://lh3.googleusercontent.com/avatar.jpg".uploadedProfileAvatar())
        assertNull("https://example.com/picture.jpg".uploadedProfileAvatar())
        val uploaded = "https://project.supabase.co/storage/v1/object/public/avatars/user.jpg?cacheNonce=42"
        assertEquals(uploaded, uploaded.uploadedProfileAvatar())
        assertNull(null.uploadedProfileAvatar())
    }

    private fun song(id: String, title: String = "Judul sama") = Content(null, null, null, false, null, null,
        emptyList(), title, id, null)

    @Test fun discoveryAppendsNewIdentitiesAndKeepsDifferentSongsWithTheSameTitle() {
        val before = listOf(HomeItem(listOf(song("one")), "Pilihanmu"))
        val incoming = listOf(HomeItem(listOf(song("one"), song("two"), song("two")), "Berikutnya"))
        val after = appendDiscovery(before, incoming)
        assertEquals(listOf("one", "two"), after.flatMap { it.contents }.map { it?.videoId })
        assertEquals(before, appendDiscovery(before, listOf(HomeItem(listOf(song("one")), "Duplikat"))))
    }

    @Test fun discoveryKeepsAlbumAndPlaylistIdentitiesSeparate() {
        val album = song("").copy(browseId = "same-id", videoId = null)
        val playlist = song("").copy(playlistId = "same-id", videoId = null)
        val result = appendDiscovery(emptyList(), listOf(HomeItem(listOf(album, playlist, album), "Koleksi")))
        assertEquals(2, result.single().contents.size)
    }

    @Test fun playlistOnlyHomeCanContinueDiscovery() {
        val playlist = song("").copy(videoId = null, playlistId = "PLcollection")
        val feed = listOf(HomeItem(listOf(playlist), "Koleksi"))
        val seed = nextDiscoverySeed(feed, emptySet())!!
        assertEquals(DiscoverySeedKind.PLAYLIST, seed.kind)
        assertEquals("PLcollection", seed.id)
        assertNull(nextDiscoverySeed(feed, setOf(seed.key)))
    }

    @Test fun albumFeedsAreExploredAndArtistLinksAreSkipped() {
        val album = song("").copy(videoId = null, browseId = "MPREb_album")
        val artist = album.copy(browseId = "UC_artist")
        val seed = nextDiscoverySeed(listOf(HomeItem(listOf(album, artist), "Koleksi")), emptySet())!!
        assertEquals(DiscoverySeedKind.ALBUM, seed.kind)
    }

    @Test fun relatedSongsContinueBeforeOpeningTheNextCollection() {
        val playlist = song("").copy(videoId = null, browseId = "VLplaylist")
        val feed = listOf(HomeItem(listOf(song("track"), playlist), "Pilihan"))
        val track = nextDiscoverySeed(feed, emptySet())!!
        assertEquals(DiscoverySeedKind.SONG, track.kind)
        assertEquals(DiscoverySeedKind.PLAYLIST, nextDiscoverySeed(feed, setOf(track.key))!!.kind)
    }
}
