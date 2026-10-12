package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.*
import com.tan.gratify.expect.shareLyricsImage
import com.tan.gratify.ui.screen.home.wrapped.WrappedContent
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*

/** Debug-only, explicitly synthetic listening data. No account/history/playlist writes. */
class WrappedPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val empty = intent.getBooleanExtra("empty", false)
        val failed = intent.getBooleanExtra("error", false)
        setContent { AppTheme {
            var state by remember { mutableStateOf(WrappedState(year = 2026, years = listOf(2026, 2025), loading = false,
                error = failed, trackingEnabled = !empty,
                summary = if (empty) WrappedSummary() else WrappedSummary(minutes = 8420, plays = 2048, uniqueTracks = 312,
                    activeDays = 186, biggestDay = "2026-07-12", totalSeconds = 505200, biggestDaySeconds = 10800,
                    longestStreak = 24, clockBand = 3, monthlySeconds = listOf(42000, 61000, 36000, 54000, 25000, 72000, 45000, 41000, 9200, 20000, 0, 0)),
                tracks = if (empty) emptyList() else listOf(WrappedRank("Menuju Cahaya", 124, "a", subtitle = "Nadira"),
                    WrappedRank("Pelan-Pelan Pulang", 98, "b", subtitle = "Ari"), WrappedRank("Di Antara Kota", 86, "c", subtitle = "Laut"),
                    WrappedRank("Ruang Kecil", 74, "d", subtitle = "Nadira"), WrappedRank("Sampai Esok", 63, "e", subtitle = "Ari")),
                artists = if (empty) emptyList() else listOf(WrappedRank("Nadira", 230, "n", 132000), WrappedRank("Ari", 198, "r", 101400),
                    WrappedRank("Laut", 150, "l", 62400)), playlistAvailable = !empty)) }
            var saveAttempts by remember { mutableStateOf(0) }
            var shareAttempts by remember { mutableStateOf(0) }
            WrappedContent(state, WindowInsets.safeDrawing.asPaddingValues(), onBack = { finish() },
                onSelectYear = { year -> state = state.copy(year = year, provisional = year == 2026, playlistId = null, playlistError = false,
                    summary = state.summary.copy(biggestDay = state.summary.biggestDay?.replaceBefore('-', year.toString()))) },
                onRefresh = { state = state.copy(error = false) }, onSettings = { state = state.copy(trackingEnabled = true) },
                onSavePlaylist = { saveAttempts++; state = if (saveAttempts == 1) state.copy(playlistError = true) else state.copy(playlistId = 42, playlistError = false) },
                onOpenPlaylist = { state = state.copy(playlistId = null) },
                onShare = { image -> shareAttempts++; if (shareAttempts == 1) false else shareLyricsImage(image, this) })
        } }
    }
}
