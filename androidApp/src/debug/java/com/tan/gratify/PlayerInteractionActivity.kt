package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.gratify.ui.screen.player.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Debug-only interaction fixture: production UI with synthetic playback and local artwork. */
class PlayerInteractionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bitmap = ImageBitmap(480, 480).also {
            Canvas(it).apply {
                drawRect(0f, 0f, 480f, 480f, Paint().apply { color = Color(0xFF34558A) })
                drawCircle(androidx.compose.ui.geometry.Offset(300f, 190f), 145f, Paint().apply { color = Color(0xFFCAD7E2) })
                drawRect(0f, 350f, 480f, 480f, Paint().apply { color = Color(0xFF172F54) })
            }
        }
        val words = listOf("Langkah menuju cahaya", "Bersama dalam irama", "Langit membuka cerita", "Kita melangkah bersama")
        val lyrics = NowPlayingScreenData.LyricsData(Lyrics(lines = words.mapIndexed { i, text ->
            Line(((i + 1) * 4000).toString(), (i * 4000).toString(), words = text.split(" ").mapIndexed { j, word ->
                val seconds = i * 4 + j
                "<00:${seconds.toString().padStart(2, '0')}.00> $word"
            }.joinToString(" "))
        }, syncType = "RICH_SYNCED"), lyricsProvider = LyricsProvider.LRCLIB)
        val screen = NowPlayingScreenData("Pratinjau pengujian", "Langkah menuju cahaya", "Nadira", false,
            thumbnailURL = null, bitmap = bitmap, lyricsData = lyrics)
        val track = Track(null, listOf(Artist("test", "Nadira")), "0:16", 16, true, false, null, null,
            screen.nowPlayingTitle, "test", null, null, null, null)
        val timeline = MutableStateFlow(TimeLine(0, 16000, 0, true))
        setContent {
            var tab by remember { mutableStateOf(AppleMusicPlayerTab.MUSIC) }
            var playing by remember { mutableStateOf(false) }
            var motion by remember { mutableStateOf(true) }
            var fullscreen by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(2000); timeline.value = timeline.value.copy(loading = false); playing = true }
            LaunchedEffect(playing) {
                while (playing) { delay(100); timeline.value = timeline.value.copy(current = (timeline.value.current + 100) % 16000) }
            }
            AppTheme {
                AppleMusicPlayerContent(AppleMusicPlayerState(screen,
                    ControlState(playing, false, RepeatState.None, false, true, true, false, 1f),
                    timeline, listOf(track), 0, Color(0xFF34558A), tab, rememberPagerState { 1 },
                    lyricsAnimationsEnabled = motion),
                    AppleMusicPlayerActions({ event ->
                        when (event) {
                            UIEvent.PlayPause -> playing = !playing
                            is UIEvent.UpdateProgress -> timeline.value = timeline.value.copy(current = (event.newProgress * 160).toLong())
                            else -> Unit
                        }
                    }, { tab = it }, {}, {}, { if (fullscreen) fullscreen = false else finish() }, {}, {}, {}, { fullscreen = true }, {}, {},
                        onLyricsAnimationsChange = { motion = it }), fullscreenLyrics = fullscreen)
            }
        }
    }
}
