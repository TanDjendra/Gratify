package com.tan.gratify.expect

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
actual fun StoryClipAudio(url: String, startMs: Long, durationMs: Long, playing: Boolean,
    replayKey: Int, onState: (ClipPlaybackState) -> Unit) {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onState)
    val player = remember(url, startMs, durationMs, replayKey) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { callback(ClipPlaybackState(error = true, buffering = false)) }
            override fun onPlaybackStateChanged(playbackState: Int) {
                callback(ClipPlaybackState(player.currentPosition.coerceIn(0, durationMs),
                    buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE,
                    completed = playbackState == Player.STATE_ENDED))
            }
        }
        player.addListener(listener)
        player.setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA).build(), true)
        player.setMediaItem(MediaItem.Builder().setUri(url).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs)
                .setEndPositionMs(startMs + durationMs).build()).build())
        player.prepare()
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    LaunchedEffect(player) {
        while (true) {
            delay(100)
            callback(ClipPlaybackState(player.currentPosition.coerceIn(0, durationMs),
                buffering = player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_BUFFERING,
                completed = player.playbackState == Player.STATE_ENDED, error = player.playerError != null))
        }
    }
}
