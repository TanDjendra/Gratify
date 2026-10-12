package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.Chip
import com.tan.domain.repository.YouTubeConnectionStatus
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The same collection surface is used by the app and visual/interaction checks. */
@Composable
fun LibraryCollectionContent(
    items: List<LibraryItemModel>,
    selectedFilter: String?,
    isGridView: Boolean,
    sortOption: LibrarySortOption,
    onFilter: (String?) -> Unit,
    onToggleLayout: () -> Unit,
    onSort: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onAddArtist: () -> Unit,
    onLiked: () -> Unit,
    onDownloaded: () -> Unit,
    onRecentlyAdded: () -> Unit,
    onOptions: (LibraryItemModel) -> Unit,
    loading: Boolean = false,
    hasError: Boolean = false,
    onRetry: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyGridState = rememberLazyGridState(),
    footer: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = if (isGridView) GridCells.Adaptive(148.dp) else GridCells.Fixed(1),
            modifier = Modifier.widthIn(max = 1060.dp).fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isGridView) 20.dp else 4.dp),
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(isSelected = selectedFilter == null, text = stringResource(Res.string.all)) { onFilter(null) }
                    Chip(isSelected = selectedFilter == "Playlist", text = stringResource(Res.string.playlists)) {
                        onFilter(if (selectedFilter == "Playlist") null else "Playlist")
                    }
                    Chip(isSelected = selectedFilter == "Artis", text = stringResource(Res.string.artists)) {
                        onFilter(if (selectedFilter == "Artis") null else "Artis")
                    }
                }
            }
            if (selectedFilter != "Artis") {
                item(key = "shortcuts", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryShortcut(stringResource(Res.string.library_liked_songs), Res.drawable.baseline_favorite_24,
                            onLiked, Modifier.weight(1f), highlighted = true)
                        LibraryShortcut(stringResource(Res.string.downloaded_music), Res.drawable.baseline_downloaded,
                            onDownloaded, Modifier.weight(1f))
                        LibraryShortcut(stringResource(Res.string.recently_added), Res.drawable.baseline_history_24,
                            onRecentlyAdded, Modifier.weight(1f))
                    }
                }
            }
            item(key = "collection-heading", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).semantics { heading() }) {
                            Text(stringResource(when (selectedFilter) {
                                "Playlist" -> Res.string.playlists
                                "Artis" -> Res.string.library_followed_artists
                                else -> Res.string.library_your_collection
                            }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (!loading && !hasError) Text(stringResource(if (items.size == 1) Res.string.library_collection_count_one else Res.string.library_collection_count, items.size),
                                style = MaterialTheme.typography.bodySmall, color = GratifyColors.TextSecondary)
                        }
                        TextButton(onClick = if (selectedFilter == "Artis") onAddArtist else onCreatePlaylist) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(if (selectedFilter == "Artis") Res.string.library_add_artist else Res.string.create))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onSort, contentPadding = PaddingValues(end = 12.dp)) {
                            Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp), tint = GratifyColors.TextSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(sortOption.label), color = GratifyColors.TextSecondary)
                        }
                        IconButton(onClick = onToggleLayout) {
                            Icon(if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                stringResource(if (isGridView) Res.string.library_show_list else Res.string.library_show_grid),
                                Modifier.size(22.dp), tint = GratifyColors.TextSecondary)
                        }
                    }
                }
            }
            items(items, key = { it.key }, contentType = { if (isGridView) "grid-item" else "list-item" }) { item ->
                LibraryListItem(title = item.title, subtitle = item.subtitle, placeholderRes = item.placeholderRes,
                    gradientColors = item.gradientColors, onClick = item.onClick, isCircle = item.isCircle,
                    titleColor = item.titleColor, imageUrl = item.imageUrl, isGridView = isGridView,
                    isPinned = item.isPinned, artworkKey=item.artworkKey,folderIcon=item.folderIcon,
                    automaticTracks=item.automaticTracks,automaticImages=item.automaticImages,
                    onLongClick = if (item.canLongPress) ({ onOptions(item) }) else null)
            }
            if (loading) {
                items(if (isGridView) 4 else 3, key = { "loading-$it" }) { LibraryLoadingItem(isGridView) }
            }
            if (hasError) {
                item(key = "collection-error", span = { GridItemSpan(maxLineSpan) }) {
                    LibraryCollectionMessage(stringResource(Res.string.library_load_failed),
                        stringResource(Res.string.library_retry_hint), stringResource(Res.string.retry), onRetry)
                }
            } else if (!loading && items.isEmpty()) {
                item(key = "collection-empty", span = { GridItemSpan(maxLineSpan) }) {
                    val artists = selectedFilter == "Artis"
                    LibraryCollectionMessage(stringResource(if (artists) Res.string.library_no_artists else Res.string.library_no_playlists),
                        stringResource(if (artists) Res.string.library_no_artists_hint else Res.string.library_no_playlists_hint),
                        stringResource(if (artists) Res.string.library_add_artist else Res.string.library_create_playlist),
                        if (artists) onAddArtist else onCreatePlaylist)
                }
            }
            item(key = "youtube-footer", span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}

@Composable
internal fun LibraryYouTubeConnection(status: YouTubeConnectionStatus, onConnect: () -> Unit, onRetry: () -> Unit) {
    if (status == YouTubeConnectionStatus.CONNECTED || status == YouTubeConnectionStatus.CONFIGURATION_REQUIRED) return
    Surface(shape = GratifyShapes.medium, color = GratifyColors.SurfaceRaised, contentColor = GratifyColors.TextPrimary) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.library_youtube_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when (status) {
                YouTubeConnectionStatus.LOADING -> Res.string.library_youtube_connecting
                YouTubeConnectionStatus.RECONNECT -> Res.string.library_youtube_reconnect
                YouTubeConnectionStatus.ERROR -> Res.string.library_youtube_error
                else -> Res.string.library_youtube_connect_hint
            }), style = MaterialTheme.typography.bodySmall, color = GratifyColors.TextSecondary)
            if (status == YouTubeConnectionStatus.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            else TextButton(onClick = if (status == YouTubeConnectionStatus.ERROR) onRetry else onConnect,
                contentPadding = PaddingValues(end = 12.dp)) {
                Text(stringResource(if (status == YouTubeConnectionStatus.ERROR) Res.string.retry else Res.string.library_youtube_connect))
            }
        }
    }
}

@Composable
private fun LibraryShortcut(title: String, icon: DrawableResource, onClick: () -> Unit,
    modifier: Modifier, highlighted: Boolean = false) {
    Surface(onClick = onClick, modifier = modifier, shape = GratifyShapes.medium,
        color = if (highlighted) GratifyColors.AccentContainer else GratifyColors.SurfaceRaised,
        contentColor = GratifyColors.TextPrimary) {
        Column(Modifier.heightIn(min = 104.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp),
                tint = if (highlighted) GratifyColors.Accent else GratifyColors.TextPrimary)
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryCollectionMessage(title: String, description: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(32.dp), tint = GratifyColors.TextSecondary)
        Text(title, style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = GratifyColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 6.dp)) { Text(action) }
    }
}

@Composable
private fun LibraryLoadingItem(grid: Boolean) {
    val label = stringResource(Res.string.library_loading)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = label }) {
        if (grid) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(GratifyShapes.small).background(GratifyColors.SurfaceRaised))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(.7f).height(16.dp).clip(GratifyShapes.extraSmall).background(GratifyColors.SurfaceRaised))
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(GratifyShapes.small).background(GratifyColors.SurfaceRaised))
                Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(140.dp).height(16.dp).clip(GratifyShapes.extraSmall).background(GratifyColors.SurfaceRaised))
                    Box(Modifier.width(90.dp).height(12.dp).clip(GratifyShapes.extraSmall).background(GratifyColors.SurfaceRaised))
                }
            }
        }
    }
}
