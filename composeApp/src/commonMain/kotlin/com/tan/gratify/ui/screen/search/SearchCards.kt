package com.tan.gratify.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.searchResult.albums.AlbumsResult
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.data.model.searchResult.videos.VideosResult
import com.tan.domain.data.type.SearchResultType
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.ui.component.UserAvatar
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class SearchCardKind { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, PODCAST }

data class SearchCardInfo(
    val id: String,
    val title: String,
    val kind: SearchCardKind,
    val creator: String? = null,
    val detail: String? = null,
    val artwork: String? = null,
    val explicit: Boolean = false,
) {
    val playable get() = kind == SearchCardKind.SONG || kind == SearchCardKind.VIDEO
}

private fun String?.present() = this?.trim()?.takeIf { it.isNotEmpty() }

fun SearchResultType.searchCardInfo(): SearchCardInfo = when (this) {
    is SongsResult -> SearchCardInfo(videoId, title.orEmpty().trim(), SearchCardKind.SONG,
        artists?.mapNotNull { it.name.present() }?.joinToString(" · ").present(), duration.present(),
        thumbnails?.lastOrNull { it.url.isNotBlank() }?.url, isExplicit == true)
    is VideosResult -> SearchCardInfo(videoId, title.trim(), SearchCardKind.VIDEO,
        artists?.mapNotNull { it.name.present() }?.joinToString(" · ").present(), duration.present(),
        thumbnails?.lastOrNull { it.url.isNotBlank() }?.url)
    is AlbumsResult -> SearchCardInfo(browseId, title.trim(), SearchCardKind.ALBUM,
        artists.mapNotNull { it.name.present() }.joinToString(" · ").present(), year.present(),
        thumbnails.lastOrNull { it.url.isNotBlank() }?.url, isExplicit)
    is ArtistsResult -> SearchCardInfo(browseId, artist.trim(), SearchCardKind.ARTIST,
        artwork = thumbnails.lastOrNull { it.url.isNotBlank() }?.url)
    is PlaylistsResult -> SearchCardInfo(browseId, title.trim(),
        if (resultType == "Podcast") SearchCardKind.PODCAST else SearchCardKind.PLAYLIST,
        author.present(), artwork = thumbnails.lastOrNull { it.url.isNotBlank() }?.url)
    else -> SearchCardInfo("", "", SearchCardKind.SONG)
}

/** Minimum readable width grows with the user's font setting, including split-screen layouts. */
fun searchBrowseColumns(width: Dp, fontScale: Float): Int =
    ((width.value + 12f) / (172f * fontScale.coerceAtLeast(1f) + 12f)).toInt().coerceIn(1, 4)

@Composable
fun SearchResultCard(
    info: SearchCardInfo,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    downloaded: Boolean = false,
    onClick: () -> Unit,
    onQueue: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val title = info.title.ifBlank { stringResource(Res.string.search_untitled) }
    val kind = stringResource(when (info.kind) {
        SearchCardKind.SONG -> Res.string.song
        SearchCardKind.VIDEO -> Res.string.search_kind_video
        SearchCardKind.ALBUM -> Res.string.search_kind_album
        SearchCardKind.ARTIST -> Res.string.search_kind_artist
        SearchCardKind.PLAYLIST -> Res.string.search_kind_playlist
        SearchCardKind.PODCAST -> Res.string.search_kind_podcast
    })
    val currentLabel = stringResource(Res.string.search_current_track)
    val enabled = info.id.isNotBlank()
    val hasActions = info.playable && (onQueue != null || onMore != null)
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 4.dp)
        .clip(GratifyShapes.medium)
        .background(if (isCurrent) GratifyColors.AccentContainer else GratifyColors.Surface)
        .then(if (isCurrent) Modifier.border(1.dp, GratifyColors.Accent.copy(alpha = .45f), GratifyShapes.medium) else Modifier)
        .semantics { if (isCurrent) stateDescription = currentLabel }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(12.dp)) {
        val stackActions = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        @Composable fun Actions() {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onQueue?.let { callback -> IconButton(onClick = callback, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(Res.drawable.baseline_queue_music_24), stringResource(Res.string.search_queue_track, title), tint = GratifyColors.TextPrimary)
                } }
                onMore?.let { callback -> IconButton(onClick = callback, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(Res.drawable.baseline_more_vert_24), stringResource(Res.string.search_more_track, title), tint = GratifyColors.TextPrimary)
                } }
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchArtwork(info.artwork, title = title, artist = info.kind == SearchCardKind.ARTIST)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = typo().titleSmall, fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) GratifyColors.Accent else GratifyColors.TextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    info.creator?.present()?.let { Text(it, style = typo().bodySmall, color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text(listOfNotNull(kind, info.detail.present(), if (info.explicit) stringResource(Res.string.search_explicit) else null,
                        if (downloaded) stringResource(Res.string.downloaded) else null).joinToString(" · "),
                        style = typo().labelSmall, color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (hasActions && !stackActions) Actions()
            }
            if (hasActions && stackActions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Actions() }
        }
    }
}

@Composable
private fun SearchArtwork(url: String?, title: String, artist: Boolean = false) {
    Box(Modifier.size(56.dp).clip(if (artist) CircleShape else GratifyShapes.small).background(GratifyColors.SurfaceHighest), contentAlignment = Alignment.Center) {
        SearchCoverArtwork(title, Modifier.fillMaxSize(), url)
    }
}

@Composable
fun SearchPersonCard(id: String, name: String?, avatar: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(GratifyShapes.medium)
        .background(GratifyColors.Surface).clickable(enabled = id.isNotBlank(), role = Role.Button, onClick = onClick)
        .heightIn(min = 80.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UserAvatar(imageUrl = avatar, name = name, modifier = Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name?.present() ?: stringResource(Res.string.search_user_fallback), style = typo().titleSmall,
                color = GratifyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.search_user_fallback), style = typo().bodySmall, color = GratifyColors.TextSecondary)
        }
        Icon(painterResource(Res.drawable.baseline_arrow_outward_24), null, tint = GratifyColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** Artwork has its own space; long titles and large fonts never overlap it. */
@Composable
fun SearchBrowseCard(
    title: String,
    gradient: Brush,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    imageUrl: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(modifier.clip(GratifyShapes.medium).background(gradient)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 156.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(72.dp).clip(GratifyShapes.small)) {
                SearchCoverArtwork(title, Modifier.fillMaxSize(), imageUrl)
            }
            Icon(painterResource(Res.drawable.baseline_arrow_outward_24), null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.ifBlank { stringResource(Res.string.search_untitled) }, style = typo().titleMedium, fontWeight = FontWeight.Bold,
                color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            subtitle?.present()?.let { Text(it, style = typo().bodySmall, color = Color.White.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
