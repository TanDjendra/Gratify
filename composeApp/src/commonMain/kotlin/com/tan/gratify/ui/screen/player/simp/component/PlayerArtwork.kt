package com.tan.gratify.ui.screen.player.simp.component
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.CachePolicy

/** A progress tick must not construct a new request for the same artwork. */
@Composable internal fun rememberPlayerArtworkRequest(
    data: String?, cacheKey: String? = data, crossfadeMillis: Int = 0,
): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, data, cacheKey, crossfadeMillis) {
        ImageRequest.Builder(context).data(data).diskCachePolicy(CachePolicy.ENABLED)
            .diskCacheKey(cacheKey).apply {
                if (crossfadeMillis > 0) crossfade(crossfadeMillis)
            }.build()
    }
}
@Composable fun PlayerArtworkImage(
    model: Any?, contentDescription: String?, modifier: Modifier = Modifier,
    bitmap: ImageBitmap? = null, contentScale: ContentScale = ContentScale.Fit,
    placeholder: Painter? = null, error: Painter? = null, fallback: Painter? = error,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
) {
    if (bitmap != null) Image(bitmap, contentDescription, modifier, contentScale = contentScale)
    else AsyncImage(model, contentDescription, modifier, contentScale = contentScale,
        placeholder = placeholder, error = error, fallback = fallback, onSuccess = onSuccess, onError = onError)
}
