package com.gratify.media_jvm

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class VlcPlaybackOptionsTest {
    @Test fun musicWithMuxedVideoOrEmbeddedCoverCannotOpenAnOutputWindow() {
        assertContains(vlcPlaybackOptions(isVideo = false).toList(), ":no-video")
    }
    @Test fun explicitVideoStillAllowsItsEmbeddedSurfaceAndMergedAudio() {
        val options = vlcPlaybackOptions(true, "https://example.invalid/audio").toList()
        assertFalse(":no-video" in options)
        assertContains(options, ":input-slave=https://example.invalid/audio")
        assertContains(options, ":http-reconnect")
    }
}
