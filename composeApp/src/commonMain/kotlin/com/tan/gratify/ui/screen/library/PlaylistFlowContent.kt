package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.utils.toTrack
import com.tan.gratify.ui.screen.search.SearchCoverArtwork
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.AddSongsToPlaylistState
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreatePlaylistContent(state: AddSongsToPlaylistState, onTitle: (String) -> Unit,
    onCreate: () -> Unit, onClose: () -> Unit) {
    val ready = !state.isSaving && !state.accountBlocked && !state.ownerChanged && state.createdPlaylistId == null
    Scaffold(containerColor = GratifyColors.Background, modifier = Modifier.imePadding(),
        topBar = { PlaylistFlowTopBar("", onClose, !state.isSaving) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.accountBlocked || state.ownerChanged) PlaylistAccessNotice(state, onClose, {})
            else PlaylistNameEntry(stringResource(Res.string.playlist_flow_name_title),state.title,onTitle,ready,state.isSaving,
                onCreate,onClose,createLabel=stringResource(if(state.isSaving)Res.string.playlist_builder_saving else Res.string.library_create_playlist),
                cancelLabel=stringResource(Res.string.cancel),notice=stringResource(Res.string.playlist_flow_create_hint),
                error=when{state.titleInvalid->stringResource(Res.string.playlist_name_cannot_be_empty);state.saveError!=null->stringResource(Res.string.playlist_builder_save_failed);else->null})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistSongPickerContent(state: AddSongsToPlaylistState, onSearch: (String) -> Unit,
    onToggle: (Track) -> Unit, onClose: () -> Unit, onRecentRetry: () -> Unit,
    onLikedRetry: () -> Unit, onTargetRetry: () -> Unit, onRecommendationRetry: () -> Unit) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var source by rememberSaveable { mutableIntStateOf(0) }
    val ready = !state.accountBlocked && !state.ownerChanged && !state.targetLoading && !state.targetUnavailable
    val searching = state.searchQuery.isNotBlank()
    val tracks = if (searching) state.searchResults.map { it.toTrack() } else when (source) {
        1 -> state.recentSongs.map { it.toTrack() }
        2 -> state.likedSongs.map { it.toTrack() }
        else -> state.recommendations
    }
    val loading = if (searching) state.isSearching else when (source) { 1 -> state.recentLoading; 2 -> state.likedLoading; else -> state.recommendationsLoading }
    val error = if (searching) state.searchError else when (source) { 1 -> state.recentError; 2 -> state.likedError; else -> state.recommendationsError }
    val retry = if (searching) ({ onSearch(state.searchQuery) }) else when (source) { 1 -> onRecentRetry; 2 -> onLikedRetry; else -> onRecommendationRetry }
    Scaffold(modifier = Modifier.imePadding(), containerColor = GratifyColors.Background,
        topBar = { PlaylistFlowTopBar(stringResource(Res.string.playlist_flow_add_title), onClose, state.pendingTrackIds.isEmpty(), state.title, onDone = if (ready) onClose else null) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                OutlinedTextField(state.searchQuery, onSearch, enabled = ready, singleLine = true,
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), shape = CircleShape,
                    placeholder = { Text(stringResource(Res.string.playlist_flow_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Rounded.Close, stringResource(Res.string.clear)) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }))
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (!ready) PlaylistAccessNotice(state, onClose, onTargetRetry)
            else Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Res.string.playlist_flow_recommended, Res.string.playlist_flow_recent, Res.string.playlist_flow_liked).forEachIndexed { index, label ->
                        FilterChip(selected = !searching && source == index, onClick = { source = index; if (searching) onSearch("") },
                            label = { Text(stringResource(label)) }, shape = CircleShape,
                            modifier = Modifier.heightIn(min = 48.dp), colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GratifyColors.Accent, selectedLabelColor = GratifyColors.OnAccent))
                    }
                }
                Text(if (state.addedCount > 0) stringResource(Res.string.playlist_manage_added_count, state.existingTrackIds.size, state.addedCount)
                    else if (state.membershipChangeCount > 0) stringResource(Res.string.playlist_flow_count_saved, state.existingTrackIds.size)
                    else stringResource(Res.string.playlist_flow_saved_hint), style = typo().bodySmall,
                    color = GratifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    if (loading) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)) }
                    if (error) item("retry") {
                        Text(stringResource(if (searching) Res.string.playlist_builder_search_failed else Res.string.library_load_failed), color = GratifyColors.TextSecondary)
                        TextButton(onClick = retry) { Text(stringResource(Res.string.retry)) }
                    }
                    if (!loading && tracks.isEmpty() && !error) item("empty") {
                        Column(Modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (searching) Res.string.playlist_builder_no_results else when (source) {
                                1 -> Res.string.playlist_flow_recent_empty; 2 -> Res.string.playlist_flow_liked_empty; else -> Res.string.playlist_flow_empty
                            }), style = typo().titleLarge)
                            Text(stringResource(if (searching) Res.string.playlist_builder_no_results_hint else Res.string.playlist_flow_empty_hint), color = GratifyColors.TextSecondary)
                        }
                    }
                    items(tracks, key = Track::videoId) { track -> PlaylistImmediateSongRow(track, state, ready) { onToggle(track) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistFlowTopBar(title: String, onClose: () -> Unit, enabled: Boolean, subtitle: String = "", onDone: (() -> Unit)? = null) {
    Surface(color = GratifyColors.Background) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).heightIn(min = 64.dp)
            .padding(end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = typo().titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = typo().bodySmall, color = GratifyColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onDone != null) TextButton(onClick = onDone, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.detail_done))
            }
        }
    }
}

@Composable
private fun PlaylistAccessNotice(state: AddSongsToPlaylistState, onClose: () -> Unit, onRetry: () -> Unit) {
    when {
        state.accountUnavailable || state.ownerChanged -> Column(Modifier.padding(24.dp)) {
            Text(stringResource(if (state.ownerChanged) Res.string.playlist_builder_account_changed else Res.string.library_load_failed))
            TextButton(onClick = onClose) { Text(stringResource(Res.string.ui_back)) }
        }
        state.targetUnavailable -> Column(Modifier.padding(24.dp)) {
            Text(stringResource(Res.string.playlist_add_unavailable))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
internal fun PlaylistImmediateSongRow(track: Track, state: AddSongsToPlaylistState, enabled: Boolean = true, onToggle: () -> Unit) {
    val added = track.videoId in state.existingTrackIds
    val pending = track.videoId in state.pendingTrackIds
    val failed = track.videoId in state.failedTrackIds
    val actionLabel = if (added) stringResource(Res.string.playlist_flow_already_added) else stringResource(Res.string.playlist_builder_add_song, track.title)
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(GratifyShapes.small).background(GratifyColors.SurfaceRaised)) {
            SearchCoverArtwork(track.title, Modifier.fillMaxSize(), track.thumbnails?.lastOrNull { it.url.isNotBlank() }?.url)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = typo().bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artists.orEmpty().joinToString(", ") { it.name }, style = typo().bodySmall, color = GratifyColors.TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (failed) Text(stringResource(if (added) Res.string.playlist_flow_remove_failed else Res.string.playlist_flow_add_failed), style = typo().bodySmall,
                color = GratifyColors.Error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        IconToggleButton(checked = added, onCheckedChange = { onToggle() }, enabled = enabled && !pending && !added,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = actionLabel
            }) {
            if (pending) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(if (added) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                null,
                tint = if (added) GratifyColors.Accent else GratifyColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun PlaylistRecommendationsContent(state: AddSongsToPlaylistState, onToggle: (Track) -> Unit, onRetry: () -> Unit) {
    if (state.accountBlocked || state.ownerChanged || state.targetUnavailable || state.targetLoading) return
    val tracks = state.recommendations.filter { it.videoId !in state.existingTrackIds || it.videoId in state.sessionAddedTrackIds }.take(6)
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(Res.string.playlist_flow_recommended), style = typo().titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp))
        if (state.recommendationsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        tracks.forEach { track -> PlaylistImmediateSongRow(track, state) { onToggle(track) } }
        if (tracks.isEmpty() && !state.recommendationsLoading) {
            Text(stringResource(Res.string.playlist_flow_empty_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
            if (state.recommendationsError) TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}
