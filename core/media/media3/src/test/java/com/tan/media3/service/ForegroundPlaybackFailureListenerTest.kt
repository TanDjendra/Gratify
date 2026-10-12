package com.tan.media3.service

import androidx.media3.common.util.UnstableApi
import com.tan.domain.mediaservice.player.MediaPlayerInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

@UnstableApi
class ForegroundPlaybackFailureListenerTest {
    @Test
    fun rejectedPromotionPausesAdapterWithoutStoppingOrClearingQueue() {
        val calls = mutableListOf<String>()
        val adapter = Proxy.newProxyInstance(
            MediaPlayerInterface::class.java.classLoader, arrayOf(MediaPlayerInterface::class.java),
        ) { _, method, _ -> calls += method.name; null } as MediaPlayerInterface
        ForegroundPlaybackFailureListener { adapter.pause() }.onForegroundServiceStartNotAllowedException()
        assertEquals(listOf("pause"), calls)
    }
}
