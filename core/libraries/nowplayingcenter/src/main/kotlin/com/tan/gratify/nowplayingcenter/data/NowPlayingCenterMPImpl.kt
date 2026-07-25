package com.tan.gratify.nowplayingcenter.data

import com.tan.gratify.nowplayingcenter.domain.NowPlayingCenter

class NowPlayingCenterMPImpl: NowPlayingCenter {
    override suspend fun setNowPlaying(
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?
    ) {
        // TODO("Not yet implemented")
    }

    override suspend fun setDuration(durationMs: Long) {
        // TODO("Not yet implemented")
    }

    override suspend fun setCurrentPosition(currentMs: Long) {
        // TODO("Not yet implemented")
    }

    override suspend fun setButtonEnabled(
        isPlaying: Boolean,
        canGoNext: Boolean,
        canGoPrevious: Boolean
    ) {
        // TODO("Not yet implemented")
    }
}
