package com.tan.gratify.ui.screen.player

import androidx.compose.animation.Animatable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.gratify.Platform
import com.tan.gratify.getPlatform
import com.tan.gratify.extension.GradientOffset
import com.tan.gratify.extension.GradientAngle
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.viewModel.UIEvent
import com.tan.gratify.ui.screen.player.simp.content.*
import com.tan.gratify.ui.screen.player.simp.support.LocalPlayerPlatform
import kotlinx.coroutines.flow.StateFlow
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class AppleMusicPlayerTab { MUSIC, LYRICS, QUEUE }

data class AppleMusicPlayerState(
    val screen: NowPlayingScreenData,
    val controls: ControlState,
    val timeline: StateFlow<TimeLine>,
    val queue: List<Track>,
    val queueIndex: Int,
    val artworkColor: Color,
    val tab: AppleMusicPlayerTab,
    val artworkPager: PagerState,
    val showVideo: Boolean = false,
    val lyricsAnimationsEnabled: Boolean = false,
    val audioQualityLabel: String? = null,
    val endlessQueueEnabled: Boolean = false,
    val previewAndroid: Boolean = false,
    val isYouTubeLoggedIn: Boolean = false,
    val youtubeLikeStatus: Boolean = false,
)

class AppleMusicPlayerActions(
    val onEvent: (UIEvent) -> Unit,
    val onSelectTab: (AppleMusicPlayerTab) -> Unit,
    val onQueueTrack: (Int) -> Unit,
    val onArtwork: (ImageBitmap) -> Unit,
    val onDismiss: () -> Unit,
    val onMore: () -> Unit,
    val onInfo: () -> Unit,
    val onArtist: () -> Unit,
    val onFullscreenLyrics: () -> Unit,
    val onFullscreenVideo: () -> Unit,
    val onManageQueue: () -> Unit,
    val onVoteLyrics: () -> Unit = {},
    val onLyricsAnimationsChange: (Boolean) -> Unit = {},
    val onAddToPlaylist: () -> Unit = {},
    val onEndlessQueueChange: (Boolean) -> Unit = {},
    val onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    val onRemoveQueueItem: (Int) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onYouTubeLiked: () -> Unit = {},
    val onRetryLyrics: () -> Unit = {},
)


/** Gratify owns playback; the presentation below is the upstream SimpMusic Apple Music renderer. */
@Composable
fun AppleMusicPlayerContent(
    state: AppleMusicPlayerState,
    actions: AppleMusicPlayerActions,
    modifier: Modifier = Modifier,
    video: @Composable (Modifier) -> Unit = {},
    canvas: @Composable (Modifier) -> Unit = {},
    fullscreenLyrics: Boolean = false,
) {
    com.tan.gratify.ui.screen.player.simp.platform.PlayerSystemBars()
    val time = state.timeline.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(0f) }
    val displayedSlider = remember(time) { derivedStateOf {
        val position = time.value
        if (dragging) slider else if (position.total > 0) {
            (position.current.toFloat() * 100 / position.total).coerceIn(0f,100f)
        } else 0f
    } }
    val start = remember { Animatable(state.artworkColor) }
    LaunchedEffect(state.artworkColor) { start.animateTo(state.artworkColor) }
    val end = remember { Animatable(Color(0xFF121212)) }
    var missingFeature by remember { mutableStateOf<String?>(null) }
    val outputMessage = stringResource(Res.string.simp_output_unavailable)
    val togetherMessage = stringResource(Res.string.simp_together_unavailable)
    val lines = state.screen.lyricsData?.lyrics?.lines.orEmpty()
    val activeIndex = remember(lines, time) {
        val starts = lines.map { it.startTimeMs.toLongOrNull() ?: Long.MAX_VALUE }
        val synced = lines.map { it.startTimeMs }.distinct().size > 1
        derivedStateOf { if (!synced) -1 else starts.indexOfLast { it <= time.value.current } }
    }
    val mainScroll = rememberScrollState()
    val upstreamState = remember(state, start, end, time, displayedSlider, activeIndex, mainScroll) { NowPlayingContentState(
        screenData = state.screen, controllerState = state.controls, timelineState = TimeLine(0L, 0L, 0),
        timelineFlow = state.timeline, likeStatus = state.youtubeLikeStatus,
        shouldShowVideo = state.showVideo, isUserLoggedIn = state.isYouTubeLoggedIn,
        artworkQueue = state.queue, currentOrderIndex = state.queueIndex, artworkPagerState = state.artworkPager,
        startColor = start, endColor = end, spotShadowColor = state.artworkColor,
        gradientOffset = GradientOffset(GradientAngle.CW135), sliderTrackColor = Color.White,
        sliderValue = 0f, currentLyricLineIndex = -1, showControlLayout = true,
        controlLayoutAlpha = 1f, showHideMiddleLayout = true, shouldShowToolbar = false,
        isInPipMode = false, mainScrollState = mainScroll, isExpanded = true,
        dismissIcon = Icons.Rounded.KeyboardArrowDown, audioQualityLabel = state.audioQualityLabel,
        lyricsAnimationsEnabled = state.lyricsAnimationsEnabled, selectedTab = state.tab,
        endlessQueueEnabled = state.endlessQueueEnabled,
        timelineStateSource = time, sliderValueSource = displayedSlider, lyricLineIndexSource = activeIndex,
    ) }
    val upstreamActions = remember(actions, outputMessage, togetherMessage) { NowPlayingContentActions(
        onUIEvent = actions.onEvent, onSeekToQueueIndex = actions.onQueueTrack, onArtworkBitmap = actions.onArtwork,
        onSliderChange = { dragging = true; slider = it },
        onSliderChangeFinished = { actions.onEvent(UIEvent.UpdateProgress(slider)); dragging = false },
        onToggleControls = {}, onNavigateToArtist = actions.onArtist,
        onOpenListenTogether = { missingFeature = togetherMessage }, onShowOutput = { missingFeature = outputMessage },
        onAddToYouTubeLiked = actions.onYouTubeLiked, onShowMoreSheet = actions.onMore,
        onShowQueue = actions.onManageQueue, onShowInfo = actions.onInfo, onShowAddToPlaylist = actions.onAddToPlaylist,
        onShowFullscreenLyrics = actions.onFullscreenLyrics, onShowVoteDialog = actions.onVoteLyrics,
        onEnterFullscreenVideo = actions.onFullscreenVideo, onDismiss = actions.onDismiss,
        onToolbarVisibilityChange = {}, onMoveQueueItem = actions.onMoveQueueItem,
        onRemoveQueueItem = actions.onRemoveQueueItem, onTabChange = actions.onSelectTab,
        onLyricsAnimationsChange = actions.onLyricsAnimationsChange,
        onEndlessQueueChange = actions.onEndlessQueueChange, onLoadMore = actions.onLoadMore,
        onRetryLyrics = actions.onRetryLyrics,
    ) }
    var previewVolume by remember { mutableFloatStateOf(.5f) }
    val previewController = remember { object : com.tan.gratify.ui.screen.player.simp.platform.DeviceVolumeController {
        override val volumeFraction: Float get() = previewVolume
        override fun setVolumeFraction(fraction: Float) { previewVolume = fraction }
    } }
    CompositionLocalProvider(
        LocalPlayerPlatform provides if (state.previewAndroid) Platform.Android else getPlatform(),
        com.tan.gratify.ui.screen.player.simp.support.LocalPlayerVideo provides if (state.previewAndroid) video else null,
        com.tan.gratify.ui.screen.player.simp.support.LocalPreviewVolume provides if (state.previewAndroid) previewController else null,
    ) {
        Box(modifier.fillMaxSize()) {
            if (fullscreenLyrics) com.tan.gratify.ui.screen.player.simp.component.FullscreenLyricsContent(
                color = state.artworkColor, state = upstreamState, actions = upstreamActions, onDismiss = actions.onDismiss)
            else NowPlayingContentAppleMusic(upstreamState, upstreamActions)
        }
    }
    missingFeature?.let { message ->
        AlertDialog(onDismissRequest = { missingFeature = null }, text = { Text(message) },
            confirmButton = { TextButton({ missingFeature = null }) { Text(stringResource(Res.string.ok)) } })
    }
}
