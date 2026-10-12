// Ported from SimpMusic 62e0073ba818d899b22c75909deb9b034516d5db (GPL-3.0).
package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.tan.gratify.ui.screen.player.simp.theme.LocalForceDarkText
import com.tan.gratify.ui.screen.player.simp.support.LocalIsDarkTheme
import org.jetbrains.compose.resources.painterResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import gratify.composeapp.generated.resources.holder_video

/**
 * Theme-aware artwork placeholder.
 *
 * Immersive screens (Artist/Album/Playlist/LocalPlaylist/NowPlaying) mark their subtree with
 * [LocalForceDarkText] = true because they always sit on dark artwork; there the dark holder art is
 * kept. Everywhere else the placeholder follows the app theme, using the light variant in light mode
 * so it blends into the white page instead of showing a dark tile.
 */
@Composable
fun rememberHolderPainter(isVideo: Boolean = false): Painter {
    val dark = LocalForceDarkText.current || LocalIsDarkTheme.current
    return painterResource(
        if (isVideo) {
            if (dark) Res.drawable.holder_video else Res.drawable.holder_video
        } else {
            if (dark) Res.drawable.holder else Res.drawable.holder
        },
    )
}
