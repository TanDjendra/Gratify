package com.tan.gratify.nowplayingcenter.data

import com.tan.gratify.nowplayingcenter.domain.NowPlayingCenter
import io.github.selemba1000.JMTC
import io.github.selemba1000.JMTCEnabledButtons
import io.github.selemba1000.JMTCMusicProperties
import io.github.selemba1000.JMTCPlayingState
import io.github.selemba1000.JMTCTimelineProperties
import com.tan.gratify.nowplayingcenter.domain.ImageLoader

internal class NowPlayingCenterJmtcImpl(private val control: JMTC): NowPlayingCenter {
    private val imageLoader = ImageLoader()
    
    override suspend fun setNowPlaying(
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?
    ) {
        val artworkFile = artworkUrl?.let { url ->
            runCatching {
                imageLoader.loadImage(url)
            }.getOrNull()
        }
        control.mediaProperties = JMTCMusicProperties(
            title,
            artist,
            album,
            artist,
            arrayOf<String>(),
            0,
            0,
            artworkFile
        )
        control.updateDisplay()
    }

    override suspend fun setDuration(durationMs: Long) {
        control.setTimelineProperties(
            JMTCTimelineProperties(
                0L,
                durationMs,
                0L,
                durationMs
            )
        )
        control.updateDisplay()
    }

    override suspend fun setCurrentPosition(currentMs: Long) {
        control.setPosition(currentMs)
        control.updateDisplay()
    }

    override suspend fun setButtonEnabled(
        isPlaying: Boolean,
        canGoNext: Boolean,
        canGoPrevious: Boolean,
    ) {
        control.enabledButtons = JMTCEnabledButtons(
            true,
            isPlaying,
            true,
            canGoNext,
            canGoPrevious,
        )
        control.playingState = if(isPlaying) JMTCPlayingState.PLAYING else JMTCPlayingState.PAUSED
        control.updateDisplay()
    }
}
