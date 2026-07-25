package com.tan.gratify.nowplayingcenter

import io.github.selemba1000.JMTC
import io.github.selemba1000.JMTCButtonCallback
import io.github.selemba1000.JMTCCallbacks
import io.github.selemba1000.JMTCSettings
import com.tan.gratify.nowplayingcenter.data.NowPlayingCenterJmtcImpl
import com.tan.gratify.nowplayingcenter.data.NowPlayingCenterMPImpl
import com.tan.gratify.nowplayingcenter.domain.NowPlayingCenter
import com.tan.gratify.nowplayingcenter.domain.NowPlayingListener
import com.tan.gratify.nowplayingcenter.domain.Platform

class NPYC(
    platform: Platform,
) {
    private var listener: NowPlayingListener? = null
    private var nowPlayingCenter: NowPlayingCenter
    init {
        if (platform is Platform.MacOs) {
            nowPlayingCenter = NowPlayingCenterMPImpl()
        } else {
            val control = JMTC.getInstance(JMTCSettings(
                when (platform) {
                    is Platform.Windows -> "Gratify"
                    is Platform.Linux -> platform.playerName
                    else -> "Gratify"
                },
                when (platform) {
                    is Platform.Linux -> platform.desktopEntryFile
                    else -> ""
                }
            ))
            val callback = JMTCCallbacks()
            callback.onPlay = JMTCButtonCallback { listener?.onPlayPause() }
            callback.onPause = JMTCButtonCallback { listener?.onPlayPause() }
            callback.onNext = JMTCButtonCallback { listener?.onNext() }
            callback.onPrevious = JMTCButtonCallback { listener?.onPrevious() }
            callback.onStop = JMTCButtonCallback { listener?.onStop() }
            control.setCallbacks(callback)
            control.enabled = true
            nowPlayingCenter = NowPlayingCenterJmtcImpl(control)
        }
    }

    fun setListener(listener: NowPlayingListener) {
        this.listener = listener
    }

    fun removeListener() {
        this.listener = null
    }

    suspend fun setNowPlaying(title: String, artist: String, album: String, artworkUrl: String?) {
        nowPlayingCenter.setNowPlaying(
            title,
            artist,
            album,
            artworkUrl
        )
    }
    suspend fun setDuration(durationMs: Long) {
        nowPlayingCenter.setDuration(durationMs)
    }
    suspend fun setCurrentPosition(currentMs: Long) {
        nowPlayingCenter.setCurrentPosition(currentMs)
    }
    suspend fun setButtonEnabled(
        isPlaying: Boolean,
        canGoNext: Boolean,
        canGoPrevious: Boolean,
    ) {
        nowPlayingCenter.setButtonEnabled(
            isPlaying,
            canGoNext,
            canGoPrevious
        )
    }
}
