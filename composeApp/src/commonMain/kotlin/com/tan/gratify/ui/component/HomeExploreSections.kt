package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.browse.artist.ResultPlaylist
import com.tan.domain.data.model.home.chart.Chart
import com.tan.domain.data.model.home.chart.ItemArtist
import com.tan.domain.data.model.mood.Mood
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Shared by the live feed and previews so spacing is verified on the actual components. */
@Composable
fun HomeExploreCategories(mood: Mood, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GratifySpacing.Section)) {
        if (mood.moodsMoments.isNotEmpty()) {
            HomeCategoryShelf(
                title = stringResource(Res.string.moods_amp_moment),
                subtitle = stringResource(Res.string.let_s_pick_a_playlist_for_you),
                entries = mood.moodsMoments.map { it.title to it.params },
                onOpen = onOpen,
            )
        }
        if (mood.genres.isNotEmpty()) {
            HomeCategoryShelf(
                title = stringResource(Res.string.genre),
                entries = mood.genres.map { it.title to it.params },
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun HomeCategoryShelf(
    title: String,
    entries: List<Pair<String, String>>,
    subtitle: String? = null,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeSectionHeading(title, subtitle)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Two rows, with a small glimpse of the next column to signal horizontal browsing.
            val minimumWidth = 144.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = ((maxWidth - 16.dp + 12.dp) / (minimumWidth + 12.dp)).toInt().coerceIn(1, 4)
            val cardWidth = (maxWidth - 12.dp * (columns - 1) - 16.dp) / columns
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(entries.chunked(2)) { columnIndex, column ->
                    Column(Modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        column.forEachIndexed { rowIndex, (label, params) ->
                            val tint = GratifyColors.BrowseArtwork[(columnIndex * 2 + rowIndex) % GratifyColors.BrowseArtwork.size]
                            Row(
                                Modifier.fillMaxWidth().clip(GratifyShapes.medium)
                                    .background(GratifyColors.SurfaceRaised)
                                    .clickable(role = Role.Button) { onOpen(params) }
                                    .heightIn(min = 88.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(5.dp).height(48.dp).background(tint))
                                Text(
                                    label,
                                    style = typo().titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = GratifyColors.TextPrimary,
                                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeChartRegionPicker(selected: String, regions: List<String>, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            color = GratifyColors.SurfaceRaised,
            contentColor = GratifyColors.TextPrimary,
            shape = GratifyShapes.small,
        ) {
            Row(
                Modifier.heightIn(min = GratifySpacing.TouchTarget).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(selected, style = typo().labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false },
            containerColor = GratifyColors.SurfaceRaised) {
            regions.forEach { region ->
                DropdownMenuItem(
                    text = { Text(region, color = if (region == selected) GratifyColors.Accent else GratifyColors.TextPrimary) },
                    onClick = { expanded = false; onSelect(region) },
                )
            }
        }
    }
}

@Composable
fun HomeChartShelves(chart: Chart, onPlaylist: (String) -> Unit, onArtist: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GratifySpacing.Section)) {
        chart.listChartItem.filter { it.playlists.isNotEmpty() }.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(section.title, style = typo().titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = GratifyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val textScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
                    val coverWidth = (maxWidth * 0.42f).coerceIn(132.dp * textScale, 176.dp * textScale)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(GratifySpacing.Item)) {
                        itemsIndexed(section.playlists, key = { index, item -> "${item.id}-$index" }) { _, playlist ->
                            HomeChartPlaylistCard(playlist, Modifier.width(coverWidth)) { onPlaylist(playlist.id) }
                        }
                    }
                }
            }
        }
        if (chart.artists.itemArtists.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeSectionHeading(stringResource(Res.string.top_artists))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val textScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
                    val portraitWidth = (maxWidth * 0.36f).coerceIn(116.dp * textScale, 152.dp * textScale)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(chart.artists.itemArtists, key = { index, item -> "${item.browseId}-$index" }) { _, artist ->
                            HomeChartArtistCard(artist, Modifier.width(portraitWidth)) { onArtist(artist.browseId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeChartPlaylistCard(playlist: ResultPlaylist, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(GratifyShapes.small).clickable(role = Role.Button, onClick = onClick)) {
        HomeExploreArtwork(playlist.thumbnails.lastOrNull()?.url,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(GratifyShapes.medium))
        Text(playlist.title, style = typo().titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = GratifyColors.TextPrimary, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
        Text(playlist.author, style = typo().bodySmall, color = GratifyColors.TextSecondary,
            minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HomeChartArtistCard(artist: ItemArtist, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(GratifyShapes.small).clickable(role = Role.Button, onClick = onClick)) {
        Box {
            HomeExploreArtwork(artist.thumbnails.lastOrNull()?.url,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape))
            Surface(Modifier.align(Alignment.BottomStart), shape = CircleShape,
                color = GratifyColors.SurfaceRaised, contentColor = GratifyColors.TextPrimary) {
                Text(artist.rank, style = typo().labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Text(artist.title, style = typo().titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = GratifyColors.TextPrimary, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
        if (artist.subscribers.isNotBlank()) {
            Text(artist.subscribers, style = typo().bodySmall, color = GratifyColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun HomeExploreArtwork(url: String?, modifier: Modifier) {
    AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop,
        placeholder = painterResource(Res.drawable.holder), error = painterResource(Res.drawable.holder),
        fallback = painterResource(Res.drawable.holder), modifier = modifier.background(GratifyColors.SurfaceRaised))
}
