package com.tan.data.di.loader

import com.gratify.media_jvm.di.loadVlcModule

actual fun loadMediaService() {
    loadVlcModule()
}
