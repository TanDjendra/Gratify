package com.tan.gratify.ui.screen.player.simp.support
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.streams.TimeLine
@Composable fun MediaPlayerViewWithSubtitle(modifier: Modifier, playerName: String, shouldPip: Boolean = false,
    shouldShowSubtitle: Boolean, shouldScaleDownSubtitle: Boolean = false, isInPipMode: Boolean, timelineState: TimeLine,
    lyricsData: Lyrics? = null, translatedLyricsData: Lyrics? = null, mainTextStyle: TextStyle, translatedTextStyle: TextStyle) {
    val preview = LocalPlayerVideo.current
    if (preview != null) preview(modifier)
    else com.tan.gratify.expect.ui.MediaPlayerViewWithSubtitle(modifier, playerName, shouldPip, shouldShowSubtitle,
        shouldScaleDownSubtitle, isInPipMode, timelineState, lyricsData, translatedLyricsData, mainTextStyle, translatedTextStyle)
}
