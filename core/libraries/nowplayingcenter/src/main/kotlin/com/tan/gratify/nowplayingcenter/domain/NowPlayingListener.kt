package com.tan.gratify.nowplayingcenter.domain

interface NowPlayingListener {
    fun onPlayPause()
    fun onNext()
    fun onPrevious()
    fun onStop()
}
