// Ported from SimpMusic 62e0073 (GPL-3.0); Apple Music branch and Gratify action adapters.
package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.gratify.Platform
import com.tan.gratify.expect.toggleMiniPlayer
import com.tan.gratify.ui.screen.player.simp.platform.PlatformBackdrop
import com.tan.gratify.ui.screen.player.simp.platform.layerBackdrop
import com.tan.gratify.ui.screen.player.simp.platform.rememberBackdrop
import com.tan.gratify.extension.KeepScreenOn
import com.tan.gratify.extension.formatDuration
import com.tan.gratify.extension.getScreenSizeInfo
import com.tan.gratify.extension.hsvToColor
import com.tan.gratify.ui.screen.player.simp.support.playerPlatform
import com.tan.gratify.ui.component.lyrics.ShareLyricsSheet
import com.tan.gratify.ui.component.lyrics.toShareLyricsLines
import com.tan.gratify.ui.screen.player.simp.icon.Close
import com.tan.gratify.ui.screen.player.simp.icon.Info
import com.tan.gratify.ui.screen.player.simp.icon.MoreVert
import com.tan.gratify.ui.screen.player.simp.icon.PictureInPictureAlt
import com.tan.gratify.ui.screen.player.simp.icon.QueueMusic
import com.tan.gratify.ui.screen.player.simp.icon.Share
import com.tan.gratify.ui.screen.player.simp.icon.SimpIcons
import com.tan.gratify.ui.screen.player.simp.icon.VolumeOff
import com.tan.gratify.ui.screen.player.simp.icon.VolumeUp
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.screen.player.simp.content.AppleMusicArtworkBackdrop
import com.tan.gratify.ui.screen.player.simp.content.AppleMusicMainTitleRow
import com.tan.gratify.ui.screen.player.simp.content.NowPlayingContentActions
import com.tan.gratify.ui.screen.player.simp.content.NowPlayingContentState
import com.tan.gratify.ui.screen.player.simp.content.applemusic.AppleMusicPlaybackControls
import com.tan.gratify.ui.screen.player.simp.content.applemusic.rememberAppleMusicTypography
import com.tan.gratify.ui.theme.seed
import com.tan.gratify.ui.screen.player.simp.theme.typo
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.ui.screen.player.simp.support.smoothScrimBrush
import com.tan.gratify.viewModel.UIEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.crossfading
import gratify.composeapp.generated.resources.share_lyrics
import gratify.composeapp.generated.resources.unavailable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The gutter this sheet has always used for its lyrics column.
private val FULLSCREEN_LYRICS_GUTTER = 50.dp

// Landscape insets. Desktop's top leaves room for the chrome row drawn over the layout (a 48dp
// pill inside 12dp of padding); Android's sits below the status bar instead.
private val LANDSCAPE_DESKTOP_TOP_PADDING = 72.dp
private val LANDSCAPE_ANDROID_TOP_PADDING = 24.dp
private val LANDSCAPE_BOTTOM_PADDING = 32.dp

// Apple Music's desktop full-screen player caps its artwork well short of the column it sits in.
private val LANDSCAPE_ARTWORK_MAX_SIZE = 420.dp

private val CHROME_VOLUME_SLIDER_WIDTH = 120.dp

/**
 * The fullscreen lyrics page, hosted the way Now Playing is: [FullscreenLyricsSheet] puts it in a
 * ModalBottomSheet on Android, NowPlayingScreenContent puts it in a full-window Popup on Desktop.
 *
 * The orientation picks the layout. Portrait is the page as it has always been. Landscape sets the
 * artwork, with the current Now Playing style's own track row and playback controls under it, beside
 * the lyrics — the shape of Apple Music's desktop full-screen player.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullscreenLyricsContent(
    color: Color,
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    onDismiss: () -> Unit,
) {
    // The Apple Music renderer applies its own gutter inside LyricsView — it has to, because the
    // blur needs margin of its own to spill into and a caller-side gutter leaves it sliced flat at
    // the edge. This sheet's own 50dp then stacked on top of it, insetting the text by 70dp.
    val fullscreenAppleLyrics = true
    val screenDataState = state.screenData
    val timelineState = state.timelineState
    val fullscreenLyricsOffsetMs = state.lyricsOffsetMs
    val controllerState = state.controllerState

    val coroutineScope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val windowInsets = WindowInsets.systemBars

    var sliderValue by rememberSaveable {
        mutableFloatStateOf(0f)
    }

    // Auto-hide controls state - Only hide control buttons, not title/progress
    var showControlButtons by rememberSaveable {
        mutableStateOf(true)
    }

    

    // Reset auto-hide timer when controls are shown
    LaunchedEffect(key1 = showControlButtons) {
        if (showControlButtons) {
            delay(4000) // Hide after 4 seconds
            showControlButtons = false
        }
    }

    LaunchedEffect(key1 = timelineState) {
        sliderValue =
            if (timelineState.total > 0L) {
                timelineState.current.toFloat() * 100 / timelineState.total.toFloat()
            } else {
                0f
            }
    }

    if (screenDataState.lyricsData != null) {
        KeepScreenOn()
    }

    

    

    var showShareLyricsSheet by rememberSaveable {
        mutableStateOf(false)
    }

    // This sheet renders lyrics through LyricsView, which computes the sung line internally and
    // keeps it to itself. The share sheet needs the same number to open on that line, so it is
    // recomputed here from the same two inputs, using the same helper.
    val shareTimedLineIndexes =
        remember(screenDataState.lyricsData?.lyrics?.lines) {
            screenDataState.lyricsData
                ?.lyrics
                ?.lines
                .orEmpty()
                .mapIndexedNotNull { index, line ->
                    line.startTimeMs.toLongOrNull()?.let { TimedLineIndex(index, it) }
                }.sortedBy { it.startTimeMs }
        }

    val screenInfo = getScreenSizeInfo()
    val isLandscape = screenInfo.wDP > screenInfo.hDP
    val isDesktop = playerPlatform() == Platform.Desktop
    // Glass source for the Desktop chrome. Each layout marks its own background with it, and the
    // pills are always drawn as siblings of that background — nesting them inside the source is
    // the render-feedback loop that crashes the RuntimeShader.
    val backdrop = rememberBackdrop(Color.Black)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // The sheet host paints black under this page. The Popup host on Desktop paints
                // nothing, and the gradient's translucent stops would let the app show through.
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    // Show controls on tap
                    showControlButtons = true
                },
    ) {
        if (isLandscape) {
            FullscreenLyricsLandscape(
                color = color,
                state = state,
                actions = actions,
                backdrop = backdrop,
            )
            if (isDesktop) {
                FullscreenLyricsDesktopChrome(
                            state = state, actions = actions,
                    backdrop = backdrop,
                    onDismiss = onDismiss,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        } else {
            // Crossfade: RGB rainbow color cycling when transitioning between tracks
            val shouldAnimateRainbow = timelineState.isCrossfading && controllerState.isPlaying
            val rainbowColor = if (shouldAnimateRainbow) {
                val infiniteTransition = rememberInfiniteTransition(label = "crossfadeRainbow")
                val rainbowHue by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "rainbowHue",
                )
                hsvToColor(rainbowHue, 1f, 1f)
            } else {
                Color.White
            }
            val sliderTrackColor by animateColorAsState(
                targetValue = if (timelineState.isCrossfading) rainbowColor else Color.White,
                animationSpec = tween(300),
                label = "sliderCrossfadeColor",
            )
            Box(modifier = Modifier.fillMaxSize()) {
                // Animated gradient background
                AnimatedLyricsGradientBackground(
                    color = color,
                    animationsEnabled = false,
                    modifier = if (isDesktop) Modifier.layerBackdrop(backdrop) else Modifier,
                )

                // ── Foreground content column ─────────────────────────────────────
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                bottom =
                                    with(localDensity) {
                                        windowInsets.getBottom(localDensity).toDp()
                                    },
                                top =
                                    with(localDensity) {
                                        windowInsets.getTop(localDensity).toDp()
                                    },
                            ),
                ) {
                    if (isDesktop) {
                        FullscreenLyricsDesktopChrome(
                            state = state, actions = actions,                                        backdrop = backdrop,
                            onDismiss = onDismiss,
                        )
                    }

                    // New Apple Music Style Header
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Song Poster (Small, Top Left)
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalPlatformContext.current)
                                    .data(screenDataState.thumbnailURL)
                                    .crossfade(300)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .diskCacheKey(screenDataState.thumbnailURL)
                                    .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(45.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Song Info Column
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            // Song Name
                            Text(
                                text = screenDataState.nowPlayingTitle,
                                style = typo().labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                modifier =
                                    Modifier
                                        .basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            animationMode = MarqueeAnimationMode.Immediately,
                                        ).focusable(),
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Artist Name with Explicit Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        actions.onNavigateToArtist()
                                    },
                            ) {
                                if (screenDataState.isExplicit) {
                                    ExplicitBadge(
                                        modifier =
                                            Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp),
                                    )
                                }
                                Text(
                                    text = screenDataState.artistName,
                                    style = typo().bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    modifier =
                                        Modifier
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                animationMode = MarqueeAnimationMode.Immediately,
                                            ).focusable(),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Like Button (Heart)
                        HeartCheckBox(
                            checked = controllerState.isLiked,
                            size = 24,
                        ) {
                            actions.onUIEvent(UIEvent.ToggleLike)
                        }

                        // Share lyrics — only when there are lyrics to share.
                        if (screenDataState.lyricsData != null) {
                            IconButton(
                                onClick = { showShareLyricsSheet = true },
                            ) {
                                Icon(
                                    imageVector = SimpIcons.Share,
                                    contentDescription = stringResource(Res.string.share_lyrics),
                                    tint = Color.White,
                                )
                            }
                        }

                        // Three Dot Menu
                        IconButton(
                            onClick = { actions.onShowMoreSheet() },
                        ) {
                            Icon(
                                imageVector = SimpIcons.MoreVert,
                                contentDescription = "",
                                tint = Color.White,
                            )
                        }
                    }

                    // Lyrics Content - Expands when controls are hidden
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                // Same TOTAL gutter either way, just split differently. The Apple
                                // renderer applies AppleMusicLyricPaddingX itself so its blur has
                                // margin to spill into, so the sheet contributes the remainder — the
                                // text still lines up with this screen's header and slider. Dropping
                                // the sheet's side to zero (which this did for one revision) left the
                                // lyrics sitting further out than everything else on the page.
                                .padding(
                                    horizontal =
                                        if (fullscreenAppleLyrics) {
                                            FULLSCREEN_LYRICS_GUTTER - AppleMusicLyricPaddingX
                                        } else {
                                            FULLSCREEN_LYRICS_GUTTER
                                        },
                                ),
                    ) {
                        FullscreenLyricsList(
                    state = state, actions = actions,
                            lyricsData = screenDataState.lyricsData,
                                        color = color,
                        )
                    }

                    // Progress Bar and Time - Always visible
                    Column {
                        // Real Slider
                        Box(
                            Modifier
                                .padding(
                                    top = 15.dp,
                                ).padding(horizontal = 40.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Crossfade(timelineState.loading) {
                                    if (it) {
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                            LinearProgressIndicator(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .padding(
                                                            horizontal = 3.dp,
                                                        ).clip(
                                                            RoundedCornerShape(8.dp),
                                                        ),
                                                color = Color.Gray,
                                                trackColor = Color.DarkGray,
                                                strokeCap = StrokeCap.Round,
                                            )
                                        }
                                    } else {
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                            LinearProgressIndicator(
                                                progress = { timelineState.bufferedPercent.toFloat() / 100 },
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .padding(
                                                            horizontal = 3.dp,
                                                        ).clip(
                                                            RoundedCornerShape(8.dp),
                                                        ),
                                                color = Color.Gray,
                                                trackColor = Color.DarkGray,
                                                strokeCap = StrokeCap.Round,
                                                drawStopIndicator = {},
                                            )
                                        }
                                    }
                                }
                            }
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                Slider(
                                    // Fraction, not 0..100 — see the note in NowPlayingScreen:
                                    // material3 alpha25 drops valueRange on its binary-compatibility
                                    // overload.
                                    value = sliderValue / 100f,
                                    onValueChange = {
                                        actions.onUIEvent(
                                            UIEvent.UpdateProgress(it * 100f),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 3.dp)
                                            .align(
                                                Alignment.TopCenter,
                                            ),
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            modifier =
                                                Modifier
                                                    .height(5.dp),
                                            enabled = true,
                                            sliderState = sliderState,
                                            colors =
                                                SliderDefaults.colors().copy(
                                                    thumbColor = sliderTrackColor,
                                                    activeTrackColor = sliderTrackColor,
                                                    inactiveTrackColor = Color.Transparent,
                                                ),
                                            thumbTrackGapSize = 0.dp,
                                            drawTick = { _, _ -> },
                                            drawStopIndicator = null,
                                        )
                                    },
                                    thumb = {
                                        SliderDefaults.Thumb(
                                            modifier =
                                                Modifier
                                                    .height(18.dp)
                                                    .width(8.dp)
                                                    .padding(
                                                        vertical = 4.dp,
                                                    ),
                                            thumbSize = DpSize(8.dp, 8.dp),
                                            interactionSource =
                                                remember {
                                                    MutableInteractionSource()
                                                },
                                            colors =
                                                SliderDefaults.colors().copy(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = Color.White,
                                                    inactiveTrackColor = Color.Transparent,
                                                ),
                                            enabled = true,
                                        )
                                    },
                                )
                            }
                        }
                        LazyColumn {
                            item {
                                // Time Layout
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 40.dp),
                                ) {
                                    Text(
                                        text = if ((timelineState.total == -9223372036854775807L)) "" else formatDuration(timelineState.current),
                                        style = typo().bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Left,
                                    )
                                    AnimatedVisibility(
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        visible = timelineState.isCrossfading,
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.crossfading),
                                            style = typo().bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    Text(
                                        text = formatDuration(timelineState.total),
                                        style = typo().bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Right,
                                    )
                                }

                                Spacer(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(5.dp),
                                )
                            }

                            item {
                                // Control Buttons - Animated visibility
                                AnimatedVisibility(
                                    visible = showControlButtons,
                                    enter =
                                        expandVertically(
                                            tween(300),
                                        ),
                                    exit =
                                        shrinkVertically(
                                            tween(300),
                                        ),
                                ) {
                                    PlayerControlLayout(controllerState) {
                                        actions.onUIEvent(it)
                                    }
                                }
                                AnimatedVisibility(
                                    visible = showControlButtons,
                                    enter =
                                        expandVertically(
                                            tween(300),
                                        ),
                                    exit =
                                        shrinkVertically(
                                            tween(300),
                                        ),
                                ) {
                                    // List Bottom Buttons
                                    Box(
                                        modifier =
                                            Modifier
                                                .height(32.dp)
                                                .fillMaxWidth()
                                                .padding(horizontal = 40.dp),
                                    ) {
                                        IconButton(
                                            modifier =
                                                Modifier
                                                    .size(24.dp)
                                                    .aspectRatio(1f)
                                                    .align(Alignment.CenterStart)
                                                    .clip(
                                                        CircleShape,
                                                    ),
                                            onClick = {
                                                actions.onShowInfo()
                                                showControlButtons = true
                                            },
                                        ) {
                                            Icon(imageVector = SimpIcons.Info, tint = Color.White, contentDescription = "")
                                        }
                                        Row(
                                            Modifier.align(Alignment.CenterEnd),
                                        ) {
                                            Spacer(modifier = Modifier.size(8.dp))
                                            IconButton(
                                                modifier =
                                                    Modifier
                                                        .size(24.dp)
                                                        .aspectRatio(1f)
                                                        .clip(
                                                            CircleShape,
                                                        ),
                                                onClick = {
                                                    actions.onShowQueue()
                                                    showControlButtons = true
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = SimpIcons.QueueMusic,
                                                    tint = Color.White,
                                                    contentDescription = "",
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }

                    // When control buttons are hidden, add spacer to maintain proper spacing
                    if (!showControlButtons) {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
    screenDataState.lyricsData?.let { lyricsData ->
        if (showShareLyricsSheet) {
            ShareLyricsSheet(
                lines = lyricsData.toShareLyricsLines(),
                songTitle = screenDataState.nowPlayingTitle,
                artistName = screenDataState.artistName,
                artwork = screenDataState.bitmap,
                seedColor = color,
                initialIndex = shareTimedLineIndexes.activeIndexAt(timelineState.current - fullscreenLyricsOffsetMs),
                onDismiss = { showShareLyricsSheet = false },
            )
        }
    }
}

/**
 * Landscape: the artwork with the current Now Playing style's track row and playback controls under
 * it on the left, the lyrics on the right. Only the row and the controls come from the style — its
 * action row (info, cast, queue…) stays on the Now Playing page.
 */
@Composable
private fun FullscreenLyricsLandscape(
    color: Color,
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    backdrop: PlatformBackdrop,
) {
    val localDensity = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        // The glass backdrop source. The Desktop chrome drawn over this layout is a sibling of this
        // whole Box, never a child of it.
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // The same seed rule the Apple Music page applies: the shell's colour starts on black
                // and only turns into the palette colour once the palette resolves.
                val paletteColor = state.startColor.value
                AppleMusicArtworkBackdrop(
                    state = state,
                    actions = actions,
                    seedColor = if (paletteColor == Color.Black) seed else paletteColor,
                )

        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top =
                            if (playerPlatform() == Platform.Desktop) {
                                LANDSCAPE_DESKTOP_TOP_PADDING
                            } else {
                                LANDSCAPE_ANDROID_TOP_PADDING +
                                    with(localDensity) { WindowInsets.statusBars.getTop(localDensity).toDp() }
                            },
                        bottom = LANDSCAPE_BOTTOM_PADDING,
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints {
                    val artworkSize = minOf(maxWidth * 0.75f, maxHeight * 0.5f, LANDSCAPE_ARTWORK_MAX_SIZE)
                    Column(
                        modifier =
                            Modifier
                                // Every style row carries its own 20dp side gutters, so a column
                                // 40dp wider than the artwork lines the rows up with its edges.
                                .width(artworkSize + 40.dp)
                                // A short landscape window — a phone on its side — cannot fit the
                                // artwork, the row and the transport at once. Scrolling keeps every
                                // control full size where the column would otherwise squash the last.
                                .verticalScroll(rememberScrollState()),
                    ) {
                        var artworkUrl by remember(state.screenData.thumbnailURL) { mutableStateOf(state.screenData.thumbnailURL) }
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalPlatformContext.current)
                                    .data(artworkUrl)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .diskCacheKey(artworkUrl)
                                    .crossfade(300)
                                    .build(),
                            placeholder = rememberHolderPainter(),
                            error = rememberHolderPainter(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // Same fallback the player's artwork carries: maxresdefault is missing
                            // for plenty of videos.
                            onError = {
                                val fallback = artworkUrl?.replace("maxresdefault", "hqdefault")
                                if (fallback != null && fallback != artworkUrl) artworkUrl = fallback
                            },
                            modifier =
                                Modifier
                                    .padding(horizontal = 20.dp)
                                    .size(artworkSize)
                                    .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                                val typography = rememberAppleMusicTypography()
                                AppleMusicMainTitleRow(state = state, actions = actions, typography = typography)
                                Spacer(modifier = Modifier.height(16.dp))
                                // The gutter and top inset AppleMusicBottomCluster gives these
                                // controls on the Now Playing page; the controls leave both to
                                // their Column.
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(horizontal = 20.dp)
                                            .padding(top = 8.dp),
                                ) {
                                    // Apple's own desktop player puts shuffle and repeat at the
                                    // two ends of this row; Now Playing keeps them in the queue.
                                    AppleMusicPlaybackControls(
                                        state = state,
                                        actions = actions,
                                        typography = typography,
                                        showShuffleAndRepeat = true,
                                    )
                                }
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .padding(end = 32.dp),
            ) {
                FullscreenLyricsList(
                    state = state, actions = actions,
                    lyricsData = state.screenData.lyricsData,
                        color = color,
                )
            }
        }
    }
}

/**
 * Desktop's way out of the page, plus the two controls it covers up. There is no sheet to drag
 * away and no back gesture there, and the page sits over the capsule that normally carries the
 * mini-player switch and the volume.
 */
@Composable
private fun FullscreenLyricsDesktopChrome(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    backdrop: PlatformBackdrop,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controllerState = state.controllerState

    // The Desktop capsule's volume state machine (MiniPlayer), unchanged.
    var isVolumeSliding by rememberSaveable {
        mutableStateOf(false)
    }
    var volumeValue by rememberSaveable {
        mutableFloatStateOf(0f)
    }
    LaunchedEffect(key1 = controllerState, key2 = isVolumeSliding) {
        if (!isVolumeSliding) {
            volumeValue = controllerState.volume
        }
    }
    // Remembers the level to come back to when unmuting, so the button restores
    // what the user was listening at instead of jumping to full volume.
    // Starting muted leaves nothing to restore, so full volume stays the fallback.
    var previousVolumeValue by rememberSaveable {
        mutableFloatStateOf(controllerState.volume.takeIf { it > 0f } ?: 1f)
    }
    LaunchedEffect(controllerState.volume) {
        if (controllerState.volume > 0f) {
            previousVolumeValue = controllerState.volume
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .height(48.dp)
                    .liquidGlass(backdrop, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = SimpIcons.Close, contentDescription = "", tint = Color.White)
            }
            IconButton(onClick = { toggleMiniPlayer() }) {
                Icon(
                    imageVector = SimpIcons.PictureInPictureAlt,
                    contentDescription = "Mini Player",
                    tint = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier =
                Modifier
                    .height(48.dp)
                    // Static glass: the press swell scales the whole pill, which would slide the
                    // slider out from under a pointer that is dragging it.
                    .liquidGlass(backdrop, RoundedCornerShape(24.dp), interactive = false)
                    .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    // Toggle mute/unmute
                    if (controllerState.volume > 0f) {
                        actions.onUIEvent(UIEvent.UpdateVolume(0f))
                    } else {
                        actions.onUIEvent(
                            UIEvent.UpdateVolume(previousVolumeValue.coerceIn(0.1f, 1f)),
                        )
                    }
                },
            ) {
                Icon(
                    imageVector =
                        if (controllerState.volume > 0f) {
                            SimpIcons.VolumeUp
                        } else {
                            SimpIcons.VolumeOff
                        },
                    tint = Color.White,
                    contentDescription = if (controllerState.volume > 0f) "Mute" else "Unmute",
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Slider(
                    value = volumeValue,
                    onValueChangeFinished = {
                        isVolumeSliding = false
                        actions.onUIEvent(
                            UIEvent.UpdateVolume(volumeValue.coerceIn(0f, 1f)),
                        )
                    },
                    onValueChange = {
                        isVolumeSliding = true
                        volumeValue = it
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(CHROME_VOLUME_SLIDER_WIDTH),
                    track = { sliderState ->
                        SliderDefaults.Track(
                            modifier =
                                Modifier
                                    .height(4.dp),
                            enabled = true,
                            sliderState = sliderState,
                            colors =
                                SliderDefaults.colors().copy(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                            thumbTrackGapSize = 0.dp,
                            drawTick = { _, _ -> },
                            drawStopIndicator = null,
                        )
                    },
                    thumb = {
                        // No thumb, as on the capsule's volume slider — the active/inactive split
                        // marks the level.
                        Spacer(Modifier.size(0.dp))
                    },
                )
            }
        }
    }
}

// The lyrics themselves, or the "unavailable" line when the track has none. The portrait and
// landscape layouts show exactly the same thing, only boxed differently.
@Composable
private fun FullscreenLyricsList(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    lyricsData: NowPlayingScreenData.LyricsData?,
    color: Color,
) {
    Crossfade(
        targetState = lyricsData != null,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (it) {
            lyricsData?.let { lyrics ->
                LyricsView(
                    lyricsData = lyrics,
                    timeLine = state.timelineFlow,
                    onLineClick = { f ->
                        actions.onUIEvent(UIEvent.UpdateProgress(f))
                    },
                    modifier = Modifier.fillMaxSize(),
                    showScrollShadows = true,
                    backgroundColor = color,
                    animationsEnabled = false,
                    isPlaying = state.controllerState.isPlaying,
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LyricsAvailabilityStatus(state.screenData.lyricsLoading, actions.onRetryLyrics)
            }
        }
    }
}

/**
 * The fullscreen lyrics page's background: a gradient from [color] down to black that drifts and
 * turns on a loop, and glides to a new [color] when the track changes.
 */
@Composable
internal fun AnimatedLyricsGradientBackground(
    color: Color,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = false,
) {
    if (!animationsEnabled) {
        Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(color, Color.Black))))
        return
    }
    // Animated gradient colors - SMOOTH ANIMATION
    val startColor = remember { Animatable(color) }
    val midColor1 = remember { Animatable(color.copy(alpha = 0.95f)) }
    val midColor2 = remember { Animatable(color.copy(alpha = 0.85f)) }
    val endColor = remember { Animatable(Color.Black) }

    // Dynamic gradient animation - MULTIPLE DIRECTIONS
    // Replaces the previous `while(true) { delay(16) }` loop with a Compose
    // infinite transition.
    val gradientTransition = rememberInfiniteTransition(label = "lyricsGradient")
    val animatedAngle by gradientTransition.animateFloat(
        initialValue = -45f,
        targetValue = 45f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "lyricsGradientAngle",
    )
    val animatedOffsetX by gradientTransition.animateFloat(
        initialValue = -1500f,
        targetValue = 1500f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "lyricsGradientOffsetX",
    )
    val animatedOffsetY by gradientTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "lyricsGradientOffsetY",
    )
    val gradientAngle = animatedAngle
    val gradientOffsetX = animatedOffsetX
    val gradientOffsetY = animatedOffsetY

    // Smooth color animation based on lyrics color
    LaunchedEffect(color) {
        launch {
            startColor.animateTo(
                targetValue = color,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            )
        }
        launch {
            midColor1.animateTo(
                targetValue = color.copy(alpha = 0.95f),
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            )
        }
        launch {
            midColor2.animateTo(
                targetValue = color.copy(alpha = 0.85f),
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            )
        }
        launch {
            endColor.animateTo(
                targetValue = Color.Black,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                startColor.value,
                                midColor1.value,
                                midColor2.value,
                                endColor.value.copy(alpha = 0.9f),
                                endColor.value,
                            ),
                        start =
                            Offset(
                                x = gradientOffsetX + (cos(gradientAngle * PI.toFloat() / 180f) * 800f),
                                y = gradientOffsetY + (sin(gradientAngle * PI.toFloat() / 180f) * 800f),
                            ),
                        end =
                            Offset(
                                x = gradientOffsetX + 2500f + (cos((gradientAngle + 180f) * PI.toFloat() / 180f) * 800f),
                                y = gradientOffsetY + 2500f + (sin((gradientAngle + 180f) * PI.toFloat() / 180f) * 800f),
                            ),
                    ),
                ),
    )
}
