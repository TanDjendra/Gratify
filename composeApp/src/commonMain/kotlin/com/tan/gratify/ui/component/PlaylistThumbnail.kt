package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.tan.gratify.ui.theme.typo
import com.tan.logger.Logger
import org.jetbrains.compose.resources.painterResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.monochrome
import kotlin.math.abs

@Composable
fun PlaylistThumbnail(
    title: String,
    modifier: Modifier = Modifier,
) {
    val gradientColors = generateGradientFromTitle(title)
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = gradientColors
                )
            )
    ) {

        // Title in bottom right
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

// Generate deterministic gradient colors from title
private fun generateGradientFromTitle(title: String): List<Color> {
    val hash = title.hashCode()

    // Extract RGB components from hash
    val hue1 = ((hash and 0xFF) / 255f) * 360f
    val hue2 = (((hash shr 8) and 0xFF) / 255f) * 360f

    // Convert HSV to RGB for vibrant colors
    val color1 = hsvToColor(hue1, 0.7f, 0.9f)
    val color2 = hsvToColor(hue2, 0.7f, 0.85f)

    return listOf(color1, color2)
}

/**
 * Deterministic placeholder gradient colors for a playlist title — matches the
 * [PlaylistThumbnail] / [painterPlaylistThumbnail] placeholder, so a screen can tint
 * its background to match a local playlist that has no real artwork.
 */
fun playlistTitleGradient(title: String): List<Color> = generateGradientFromTitle(title)

// HSV to RGB conversion
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = hue / 60f
    val c = value * saturation
    val x = c * (1 - abs((h % 2) - 1))
    val m = value - c

    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r + m),
        green = (g + m),
        blue = (b + m),
        alpha = 1f
    )
}

private class PlaylistThumbnailPainter(
    private val size: Size,
    private val title: String,
    private val textLayoutResult: TextLayoutResult,
    private val iconPainter: Painter
): Painter() {
    override val intrinsicSize: Size
        get() = size

    override fun DrawScope.onDraw() {
        val colors = generateGradientFromTitle(title)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = colors
            ),
            size = size,
            cornerRadius = CornerRadius(16f, 16f),
        )
        drawText(textLayoutResult, topLeft = calculateTopLeft(
            alignment = Alignment.BottomStart,
            textSize = IntSize(
                width = textLayoutResult.size.width,
                height = textLayoutResult.size.height,
            ),
            containerSize = IntSize(
                size.width.toInt(),
                size.height.toInt(),
            ),
        ))
        Logger.d("PlaylistThumbnailPainter", "Drawing icon $title")
        Logger.d("PlaylistThumbnailPainter", "Colors: ${colors.map { it.toArgb() }}")
        val centerX = size.width * 0.9f
        val centerY = size.height * 0.1f
        val circleRadius = size.width * 0.1f / 2

        drawCircle(
            center = Offset(centerX, centerY),
            color = Color.White,
            radius = circleRadius
        )

        translate(
            left = centerX - size.width * 0.15f / 2,
            top = centerY - size.height * 0.15f / 2,
        ) {
            with(iconPainter) {
                draw(size * 0.15f, alpha = 0.2f)
            }
        }
    }
}

private fun calculateTopLeft(
    alignment: Alignment,
    textSize: IntSize,
    containerSize: IntSize,
): Offset {
    val alignmentOffset =
        alignment.align(
            size = textSize.let { (width, height) ->
                IntSize(
                    width = width,
                    height = height,
                )
            },
            space = containerSize,
            layoutDirection = LayoutDirection.Ltr,
        )

    return Offset(
        x = alignmentOffset.x.toFloat() + 16f,
        y = alignmentOffset.y.toFloat() - 16f,
    )
}

@Composable
fun painterPlaylistThumbnail(
    title: String,
    style: TextStyle,
    sizeDp: Pair<Dp, Dp>,
): Painter {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult =
        remember(title, style, sizeDp) {
            textMeasurer.measure(
                title,
                style.copy(
                    color = Color.White,
                    textAlign = TextAlign.Start
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                layoutDirection = LayoutDirection.Ltr,
                constraints =
                    Constraints(
                        maxWidth = with(density) { (sizeDp.first - 32.dp).toPx().toInt() },
                        maxHeight = with(density) { (sizeDp.second - 32.dp).toPx().toInt() },
                    ),
            )
        }
    val painterRes = painterResource(Res.drawable.monochrome)
    return PlaylistThumbnailPainter(
        size = Size(
            width = with(density) { sizeDp.first.toPx() },
            height = with(density) { sizeDp.second.toPx() },
        ),
        title = title,
        textLayoutResult = textLayoutResult,
        iconPainter = painterRes
    )
}

fun isAutoAssignedOrNullThumbnail(thumb: String?, ytPlaylistId: String? = null): Boolean {
    if (thumb.isNullOrBlank()) return true
    if (ytPlaylistId == null && (thumb.contains("i.ytimg.com") || thumb.contains("googleusercontent.com") || thumb.contains("ggpht.com"))) {
        return true
    }
    return false
}

@Composable
fun PlaylistCollageThumbnail(
    tracks: List<String>,
    modifier: Modifier = Modifier,
    placeholderTitle: String = "",
    onSuccessFirstCell: ((SuccessResult) -> Unit)? = null,
) {
    val topTracks = remember(tracks) { tracks.take(4) }
    Box(modifier = modifier) {
        if (topTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(generateGradientFromTitle(placeholderTitle))),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = placeholderTitle,
                    color = Color.White,
                    style = typo().labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else if (topTracks.size == 1) {
            CollageCellImage(videoId = topTracks[0], modifier = Modifier.fillMaxSize(), onSuccess = onSuccessFirstCell)
        } else if (topTracks.size == 2) {
            Row(modifier = Modifier.fillMaxSize()) {
                CollageCellImage(videoId = topTracks[0], modifier = Modifier.weight(1f).fillMaxHeight(), onSuccess = onSuccessFirstCell)
                CollageCellImage(videoId = topTracks[1], modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else if (topTracks.size == 3) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageCellImage(videoId = topTracks[0], modifier = Modifier.weight(1f).fillMaxHeight(), onSuccess = onSuccessFirstCell)
                    CollageCellImage(videoId = topTracks[1], modifier = Modifier.weight(1f).fillMaxHeight())
                }
                CollageCellImage(videoId = topTracks[2], modifier = Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageCellImage(videoId = topTracks[0], modifier = Modifier.weight(1f).fillMaxHeight(), onSuccess = onSuccessFirstCell)
                    CollageCellImage(videoId = topTracks[1], modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageCellImage(videoId = topTracks[2], modifier = Modifier.weight(1f).fillMaxHeight())
                    CollageCellImage(videoId = topTracks[3], modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun CollageCellImage(
    videoId: String,
    modifier: Modifier = Modifier,
    onSuccess: ((SuccessResult) -> Unit)? = null,
) {
    val url = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(url)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCacheKey(url)
            .memoryCacheKey(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { res -> onSuccess?.invoke(res.result) },
        modifier = modifier
    )
}