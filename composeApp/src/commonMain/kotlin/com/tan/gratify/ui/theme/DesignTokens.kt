package com.tan.gratify.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared visual language for authentication, browsing, library and playback. */
object GratifyColors {
    val Accent = Color(0xFF1ED760)
    val OnAccent = Color(0xFF07150B)
    val AccentContainer = Color(0xFF163D25)
    val Background = Color(0xFF121212)
    val Navigation = Color(0xFF0C0C0C)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceRaised = Color(0xFF242424)
    val SurfaceHighest = Color(0xFF303030)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB3B3B3)
    val Outline = Color(0xFF535353)
    val Divider = Color(0xFF333333)
    val Error = Color(0xFFFF8A80)
    // Artwork and browse categories can be colorful while controls share one accent.
    val BrowseArtwork = listOf(Color(0xFF254F42), Color(0xFF9A4C39), Color(0xFF2C435D),
        Color(0xFF88622E), Color(0xFF5D3C62), Color(0xFF22716C))
}

object GratifySpacing {
    val Page = 16.dp
    val Section = 28.dp
    val Item = 12.dp
    val TouchTarget = 48.dp
    val Cover = 160.dp
}

val GratifyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
