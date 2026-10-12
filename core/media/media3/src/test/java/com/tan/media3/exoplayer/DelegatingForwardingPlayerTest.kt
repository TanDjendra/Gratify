package com.tan.media3.exoplayer

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tan.domain.mediaservice.player.MediaPlayerInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.lang.reflect.Proxy

@UnstableApi
class DelegatingForwardingPlayerTest {
    private fun statePlayer(state: Int, playingIntent: Boolean, playing: Boolean = false): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> state
                "getPlayWhenReady" -> playingIntent
                "isPlaying" -> playing
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as Player

    private fun intentControl(intent: () -> Boolean): MediaPlayerInterface =
        Proxy.newProxyInstance(MediaPlayerInterface::class.java.classLoader, arrayOf(MediaPlayerInterface::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlayWhenReady" -> intent()
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as MediaPlayerInterface

    @Test fun transitioningSessionRetainsPlayIntentAcrossPausedPreloadAndIdleRetry() {
        for (state in listOf(Player.STATE_ENDED, Player.STATE_READY, Player.STATE_IDLE, Player.STATE_BUFFERING)) {
            val player = DelegatingForwardingPlayer(statePlayer(state, false))
            player.playbackControl = intentControl { true }
            player.suppressPlaybackEnded = true
            assertTrue("App play intent was lost when delegate state=$state", player.playWhenReady)
            assertEquals(Player.STATE_BUFFERING, player.playbackState)
            assertFalse(player.isPlaying)
        }
    }

    @Test fun explicitPauseIsNeverMaskedAsAnOngoingTransitionAndCompletedSwapUsesRealState() {
        var intent = true
        val player = DelegatingForwardingPlayer(statePlayer(Player.STATE_READY, true, true))
        player.playbackControl = intentControl { intent }
        player.suppressPlaybackEnded = true
        intent = false
        assertFalse(player.playWhenReady)
        assertFalse(player.isPlaying)
        player.suppressPlaybackEnded = false
        intent = true
        assertEquals(Player.STATE_READY, player.playbackState)
        assertTrue(player.isPlaying)
    }

    @Test fun listenerCallbacksRetainQueueIntentAndBufferingInsteadOfRawDelegateEnded() {
        var nativeListener: Player.Listener? = null
        val delegate = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> { nativeListener = args!![0] as Player.Listener; null }
                "getPlaybackState" -> Player.STATE_ENDED
                "getPlayWhenReady", "isPlaying" -> false
                else -> null
            }
        } as Player
        val player = DelegatingForwardingPlayer(delegate)
        var intent = true
        player.playbackControl = intentControl { intent }
        player.suppressPlaybackEnded = true
        var observedState = -1
        var observedIntent = false
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { observedState = playbackState }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { observedIntent = playWhenReady }
        })
        nativeListener!!.onPlaybackStateChanged(Player.STATE_ENDED)
        nativeListener!!.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertEquals(Player.STATE_BUFFERING, observedState)
        assertTrue(observedIntent)
        intent = false
        nativeListener!!.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertFalse(observedIntent)
    }

    @Test fun transitionOwnershipIsReleasedExactlyOnceAndDuplicateStatesDoNotAcquireAgain() {
        val changes = mutableListOf<Boolean>()
        val player = DelegatingForwardingPlayer(statePlayer(Player.STATE_ENDED, false))
        player.onPlaybackTransitionChanged = { changes.add(it) }
        player.suppressPlaybackEnded = true
        player.suppressPlaybackEnded = true
        player.suppressPlaybackEnded = false
        player.suppressPlaybackEnded = false
        assertEquals(listOf(true, false), changes)
    }
    private inline fun <reified T> recordingProxy(calls: MutableList<String>): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            calls += "${method.name}(${args?.joinToString().orEmpty()})"
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        } as T

    @Test
    fun systemTransportUsesAdapterAndNeverDirectlyControlsSingleExoPlayer() {
        val delegateCalls = mutableListOf<String>()
        val adapterCalls = mutableListOf<String>()
        val player = DelegatingForwardingPlayer(recordingProxy<Player>(delegateCalls))
        player.playbackControl = recordingProxy<MediaPlayerInterface>(adapterCalls)

        player.play()
        player.pause()
        player.playWhenReady = true
        player.playWhenReady = false
        player.stop()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true

        assertEquals(listOf("play()", "pause()", "setPlayWhenReady(true)", "setPlayWhenReady(false)",
            "stop()", "setRepeatMode(2)", "setShuffleModeEnabled(true)"), adapterCalls)
        assertEquals(emptyList<String>(), delegateCalls)
    }

    @Test
    fun navigationIsExecutedExactlyOnceThroughQueueProvider() {
        val delegateCalls = mutableListOf<String>()
        val navigationCalls = mutableListOf<String>()
        val player = DelegatingForwardingPlayer(recordingProxy<Player>(delegateCalls))
        player.playlistNavigationProvider = object : DelegatingForwardingPlayer.PlaylistNavigationProvider {
            override fun hasNextMediaItem() = true
            override fun hasPreviousMediaItem() = true
            override fun seekToNext() { navigationCalls += "next" }
            override fun seekToPrevious() { navigationCalls += "previous" }
            override fun seekToPreviousMediaItem() { navigationCalls += "previousItem" }
        }

        player.seekToNext()
        player.seekToNextMediaItem()
        player.seekToPrevious()
        player.seekToPreviousMediaItem()

        assertEquals(listOf("next", "next", "previous", "previousItem"), navigationCalls)
        assertEquals(emptyList<String>(), delegateCalls)
    }

    @Test
    fun standaloneForwardingPlayerRetainsNormalTransportBehavior() {
        val calls = mutableListOf<String>()
        val player = DelegatingForwardingPlayer(recordingProxy<Player>(calls))
        player.play()
        player.pause()
        player.stop()
        assertEquals(listOf("play()", "pause()", "stop()"), calls)
    }
}
