package com.tan.gratify.expect

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import uk.co.caprica.vlcj.factory.MediaPlayerFactory

@Composable
actual fun StoryClipAudio(url: String, startMs: Long, durationMs: Long, playing: Boolean,
    replayKey: Int, onState: (ClipPlaybackState) -> Unit) {
    val callback by rememberUpdatedState(onState)
    var player by remember(url, startMs, durationMs, replayKey) { mutableStateOf<uk.co.caprica.vlcj.player.base.MediaPlayer?>(null) }
    var requestedPlaying by remember { mutableStateOf(playing) }
    LaunchedEffect(playing) { requestedPlaying = playing; player?.controls()?.setPause(!playing) }
    LaunchedEffect(url, startMs, durationMs, replayKey) {
        var factory: MediaPlayerFactory? = null
        var nativePlayer: uk.co.caprica.vlcj.player.base.MediaPlayer? = null
        try {
            withContext(Dispatchers.IO) {
                factory = MediaPlayerFactory("--no-video", "--no-osd")
                nativePlayer = factory!!.mediaPlayers().newMediaPlayer()
                nativePlayer!!.media().play(url, ":start-time=${startMs / 1000.0}", ":stop-time=${(startMs + durationMs) / 1000.0}")
            }
            player = nativePlayer
            var started = false
            var ticks = 0
            while (isActive) {
                delay(100)
                val p = nativePlayer ?: break
                val time = p.status().time()
                val nativeState = p.status().state().name
                if (nativeState == "ERROR") { callback(ClipPlaybackState(error = true, buffering = false)); break }
                if (nativeState == "PLAYING" || nativeState == "PAUSED") {
                    if (!started) { started = true; p.controls().setPause(!requestedPlaying) }
                }
                val ended = started && (time >= startMs + durationMs - 50 || nativeState == "ENDED" || nativeState == "STOPPED")
                callback(ClipPlaybackState((time - startMs).coerceIn(0, durationMs), buffering = !started || nativeState == "BUFFERING", completed = ended))
                if (ended) { p.controls().stop(); break }
                if (!started && ++ticks >= 300) { callback(ClipPlaybackState(error = true, buffering = false)); break }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Throwable) { callback(ClipPlaybackState(error = true, buffering = false)) }
        finally {
            player = null
            withContext(NonCancellable + Dispatchers.IO) { nativePlayer?.release(); factory?.release() }
        }
    }
}
