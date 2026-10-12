package com.tan.gratify.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.tan.gratify.ui.theme.GratifyColors

data class LyricsColors(val background: Color, val translation: Color)

/** Preserve the cover's hue while keeping white lyrics readable on bright artwork. */
fun lyricsColorsFromArtwork(artworkColor: Color): LyricsColors {
    val source = if (artworkColor == Color.Unspecified || artworkColor.alpha == 0f)
        GratifyColors.SurfaceRaised else artworkColor.copy(alpha = 1f)
    var background = source
    while (1.05f / (background.luminance() + 0.05f) < 7f) {
        background = lerp(background, Color.Black, 0.08f)
    }
    return LyricsColors(background, lerp(background, Color.White, 0.82f))
}
