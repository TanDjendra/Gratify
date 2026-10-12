package com.gratify.media_jvm

/** Per-media options: audio remains headless, while explicit video keeps its embedded surface. */
internal fun vlcPlaybackOptions(isVideo: Boolean, audioSlaveUrl: String? = null): Array<String> = buildList {
    if (audioSlaveUrl != null) add(":input-slave=$audioSlaveUrl")
    if (isVideo) {
        add(":network-caching=15000")
        add(":http-reconnect")
    } else add(":no-video")
}.toTypedArray()
