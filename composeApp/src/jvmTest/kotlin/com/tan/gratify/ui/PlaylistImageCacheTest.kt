package com.tan.gratify.ui

import com.tan.gratify.ui.utils.playlistImageCacheKey
import kotlin.test.*

class PlaylistImageCacheTest {
    private val server="https://example.supabase.co"
    private val cover="$server/storage/v1/object/sign/playlist-covers/owner/playlist/photo.jpg"
    @Test fun renewedSignatureReusesImageButOtherAccountAndNewObjectDoNot() {
        assertEquals(playlistImageCacheKey("$cover?token=old",server,"a"),playlistImageCacheKey("$cover?token=new",server,"a"))
        assertNotEquals(playlistImageCacheKey(cover,server,"a"),playlistImageCacheKey(cover,server,"b"))
        assertNotEquals(playlistImageCacheKey(cover,server,"a"),playlistImageCacheKey(cover.replace("photo.jpg","new.jpg"),server,"a"))
    }
    @Test fun unrelatedUnsignedAndMalformedUrlsKeepDefaultCacheBehavior() {
        assertNull(playlistImageCacheKey(cover,server,null))
        assertNull(playlistImageCacheKey(cover.replace("example","other"),server,"a"))
        assertNull(playlistImageCacheKey(cover.replace("/sign/","/public/"),server,"a"))
        assertNull(playlistImageCacheKey(cover.replace("/playlist/","/../"),server,"a"))
    }
}
