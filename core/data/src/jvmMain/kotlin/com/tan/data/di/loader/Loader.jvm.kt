package com.tan.data.di.loader

import com.gratifymusic.media_jvm.di.loadVlcModule

actual fun loadMediaService() {
    loadVlcModule()
}
