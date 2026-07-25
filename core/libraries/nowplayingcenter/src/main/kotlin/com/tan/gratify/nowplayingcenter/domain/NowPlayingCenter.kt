package com.tan.gratify.nowplayingcenter.domain

interface NowPlayingCenter {
    suspend fun setNowPlaying(title: String, artist: String, album: String, artworkUrl: String?)
    suspend fun setDuration(durationMs: Long)
    suspend fun setCurrentPosition(currentMs: Long)
    suspend fun setButtonEnabled(
        isPlaying: Boolean,
        canGoNext: Boolean,
        canGoPrevious: Boolean,
    )
}
