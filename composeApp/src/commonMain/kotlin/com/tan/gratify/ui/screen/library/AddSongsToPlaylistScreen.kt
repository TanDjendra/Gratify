package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.utils.toTrack
import com.tan.gratify.ui.navigation.finishPlaylistCreation
import com.tan.gratify.ui.navigation.finishPlaylistAddition
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.AddSongsToPlaylistState
import com.tan.gratify.viewModel.AddSongsToPlaylistViewModel
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddSongsToPlaylistScreen(playlistTitle: String, navController: NavController,
    viewModel: AddSongsToPlaylistViewModel = koinViewModel(), targetPlaylistId: Long? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    LaunchedEffect(playlistTitle, targetPlaylistId) { viewModel.bindTitle(playlistTitle); viewModel.bindTargetPlaylist(targetPlaylistId) }
    fun close() {
        if (state.isSaving || state.pendingTrackIds.isNotEmpty()) return
        if (targetPlaylistId != null) navController.finishPlaylistAddition(targetPlaylistId)
        else if (!state.ownerChanged && viewModel.hasUnsavedChanges()) discard = true else navController.navigateUp()
    }
    com.tan.gratify.ui.component.LayerBackHandler(true, ::close)
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text(stringResource(Res.string.playlist_builder_discard_title)) },
        text = { Text(stringResource(Res.string.playlist_builder_discard_hint)) },
        confirmButton = { TextButton(onClick = { discard = false; navController.navigateUp() }) { Text(stringResource(Res.string.playlist_builder_discard)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(Res.string.playlist_builder_keep)) } })
    LaunchedEffect(state.createdPlaylistId, state.accountBlocked, state.ownerChanged) {
        if (!state.accountBlocked && !state.ownerChanged) state.createdPlaylistId?.let {
            if (state.targetPlaylistId != null) navController.finishPlaylistAddition(it) else navController.finishPlaylistCreation(it)
        }
    }
    if (targetPlaylistId == null) CreatePlaylistContent(state, viewModel::updateTitle, viewModel::createEmptyPlaylist, ::close)
    else PlaylistSongPickerContent(state, viewModel::searchSongs, viewModel::appendTrack, ::close,
        viewModel::loadRecentSongs, viewModel::loadLikedSongs, viewModel::loadTargetPlaylist, viewModel::loadRecommendations)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistBuilderContent(state: AddSongsToPlaylistState, onTitle: (String) -> Unit, onSearch: (String) -> Unit,
    onAdd: (Track) -> Unit, onRemove: (String) -> Unit, onSave: () -> Unit, onClose: () -> Unit, onRecentRetry: () -> Unit, onMove: (String, Int) -> Unit = { _, _ -> }, onTargetRetry: () -> Unit = {}) {
    val adding = state.targetPlaylistId != null
    val editable = !state.isSaving && !state.accountBlocked && !state.ownerChanged && state.createdPlaylistId == null && !state.targetLoading && !state.targetUnavailable
    var arranging by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = Modifier.imePadding(), containerColor = GratifyColors.Background,
        topBar = { TopAppBar(title = { Text(stringResource(if (adding) Res.string.playlist_add_songs else Res.string.library_create_playlist), style = typo().titleLarge) },
            navigationIcon = { IconButton(onClick = onClose, enabled = !state.isSaving) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = GratifyColors.Background)) },
        bottomBar = {
            if (!state.accountBlocked && !state.ownerChanged) Surface(color = GratifyColors.SurfaceRaised) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(if (adding) Res.string.playlist_add_count else if (state.selectedTracks.isEmpty()) Res.string.playlist_builder_later else Res.string.playlist_builder_count,
                            state.selectedTracks.size), style = typo().bodySmall, color = GratifyColors.TextSecondary)
                        if (state.saveError != null) Text(stringResource(Res.string.playlist_builder_save_failed),
                            style = typo().bodyMedium, color = GratifyColors.Error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onClick = onSave, enabled = editable && if (adding) state.selectedTracks.isNotEmpty() else state.title.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GratifyColors.Accent, contentColor = GratifyColors.OnAccent)) {
                            if (state.isSaving) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GratifyColors.OnAccent); Spacer(Modifier.width(10.dp)) }
                            Text(stringResource(if (state.isSaving) Res.string.playlist_builder_saving else if (adding) Res.string.playlist_add_songs else Res.string.library_create_playlist))
                            }
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.ownerChanged -> PlaylistBuilderNotice(stringResource(Res.string.playlist_builder_account_changed),
                    stringResource(Res.string.playlist_builder_account_hint), stringResource(Res.string.ui_back), onClose)
                state.accountUnavailable -> PlaylistBuilderNotice(stringResource(Res.string.library_load_failed),
                    stringResource(Res.string.playlist_builder_account_hint), stringResource(Res.string.ui_back), onClose)
                state.accountBlocked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.targetLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.targetUnavailable -> PlaylistBuilderNotice(stringResource(Res.string.playlist_add_unavailable),
                    stringResource(Res.string.playlist_add_unavailable_hint), stringResource(Res.string.retry), onTargetRetry)
                else -> LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item("name") {
                        if (adding) {
                            Text(state.title, style = typo().titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(Res.string.playlist_add_hint, state.existingTrackIds.size),
                                style = typo().bodySmall, color = GratifyColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
                        } else {
                        PlaylistIdentity(state.selectedTracks.firstOrNull(), state.title)
                        OutlinedTextField(state.title, { if (it.length <= 100) onTitle(it) }, Modifier.fillMaxWidth(), enabled = editable,
                            label = { Text(stringResource(Res.string.playlist_builder_name)) }, singleLine = true, isError = state.titleInvalid,
                            supportingText = { Text(if (state.titleInvalid) stringResource(Res.string.playlist_name_cannot_be_empty)
                                else if (state.title.isBlank()) stringResource(Res.string.playlist_composer_name_hint)
                                else stringResource(Res.string.playlist_builder_name_count, state.title.length)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                        Text(stringResource(Res.string.playlist_composer_hint), Modifier.padding(top = 6.dp),
                            style = typo().bodySmall, color = GratifyColors.TextSecondary)
                        }
                    }
                    stickyHeader(key = "workspace-tabs") {
                        PrimaryTabRow(selectedTabIndex = if (arranging) 1 else 0, containerColor = GratifyColors.Background) {
                            Tab(selected = !arranging, onClick = { arranging = false },
                                text = { Text(stringResource(Res.string.playlist_composer_pick), style = typo().bodySmall) })
                            Tab(selected = arranging, onClick = { arranging = true },
                                text = { Text(stringResource(Res.string.playlist_composer_arrange, state.selectedTracks.size), style = typo().bodySmall) })
                        }
                    }
                    if (arranging) {
                        if (state.selectedTracks.isEmpty()) item("empty-arrangement") {
                            PlaylistBuilderNotice(stringResource(Res.string.playlist_composer_empty),
                                stringResource(Res.string.playlist_composer_empty_hint), stringResource(Res.string.playlist_composer_pick), { arranging = false })
                        } else {
                            item("arrange-hint") { Text(stringResource(Res.string.playlist_composer_order_hint),
                                style = typo().bodySmall, color = GratifyColors.TextSecondary) }
                            items(state.selectedTracks, key = { "arranged-${it.videoId}" }) { track ->
                                val index = state.selectedTracks.indexOfFirst { it.videoId == track.videoId }
                                PlaylistArrangementRow(track, index, state.selectedTracks.size, editable,
                                    { onMove(track.videoId, -1) }, { onMove(track.videoId, 1) }, { onRemove(track.videoId) })
                            }
                        }
                    } else {
                        item("search") {
                            OutlinedTextField(state.searchQuery, onSearch, Modifier.fillMaxWidth(), enabled = editable, singleLine = true,
                                placeholder = { Text(stringResource(Res.string.playlist_builder_search)) },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { onSearch("") }, enabled = editable) {
                                    Icon(Icons.Rounded.Close, stringResource(Res.string.playlist_builder_clear)) } },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(state.searchQuery) }))
                        }
                        if (state.searchQuery.isNotBlank()) {
                            item("results-heading") { Text(stringResource(Res.string.playlist_builder_results), Modifier.semantics { heading() }, style = typo().titleMedium) }
                            when {
                                state.isSearching -> item("search-loading") { PlaylistBuilderLoading() }
                                state.searchError -> item("search-error") { PlaylistBuilderNotice(stringResource(Res.string.playlist_builder_search_failed),
                                    stringResource(Res.string.playlist_builder_retry_hint), stringResource(Res.string.retry), { onSearch(state.searchQuery) }) }
                                state.searchResults.isEmpty() -> item("no-results") { PlaylistBuilderNotice(stringResource(Res.string.playlist_builder_no_results),
                                    stringResource(Res.string.playlist_builder_no_results_hint)) }
                                else -> items(state.searchResults, key = { "search-${it.videoId}" }) { song ->
                                    val track = song.toTrack(); val selected = state.selectedTracks.any { it.videoId == track.videoId }
                                    PlaylistSongChoice(track, selected, editable, track.videoId in state.existingTrackIds) { if (selected) onRemove(track.videoId) else onAdd(track) }
                                }
                            }
                        } else {
                            item("recent-heading") { Text(stringResource(Res.string.playlist_builder_recent), Modifier.semantics { heading() }, style = typo().titleMedium) }
                            when {
                                state.recentLoading -> item("recent-loading") { PlaylistBuilderLoading() }
                                state.recentError -> item("recent-error") { PlaylistBuilderNotice(stringResource(Res.string.playlist_builder_recent_failed),
                                    stringResource(Res.string.playlist_builder_retry_hint), stringResource(Res.string.retry), onRecentRetry) }
                                state.recentSongs.isEmpty() -> item("no-recent") { PlaylistBuilderNotice(stringResource(Res.string.playlist_builder_start),
                                    stringResource(Res.string.playlist_builder_start_hint)) }
                                else -> items(state.recentSongs, key = { "recent-${it.videoId}" }) { song ->
                                    val track = song.toTrack(); val selected = state.selectedTracks.any { it.videoId == track.videoId }
                                    PlaylistSongChoice(track, selected, editable, track.videoId in state.existingTrackIds) { if (selected) onRemove(track.videoId) else onAdd(track) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistIdentity(first: Track?, title: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaylistArtwork(first, Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.trim().ifBlank { stringResource(Res.string.playlist_composer_draft) }, style = typo().titleMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.playlist_composer_cover_hint), style = typo().bodySmall, color = GratifyColors.TextSecondary)
        }
    }
}

@Composable
private fun PlaylistArtwork(track: Track?, modifier: Modifier) {
    Box(modifier.clip(GratifyShapes.small).background(GratifyColors.SurfaceRaised), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = GratifyColors.TextSecondary)
        track?.thumbnails?.lastOrNull()?.url?.takeIf(String::isNotBlank)?.let {
            AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun PlaylistArrangementRow(track: Track, index: Int, total: Int, enabled: Boolean,
    onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Surface(color = GratifyColors.SurfaceRaised, shape = GratifyShapes.small) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaylistArtwork(track, Modifier.size(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(track.title, style = typo().bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artists?.joinToString(", ") { it.name }.orEmpty(), style = typo().bodySmall,
                        color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(Res.string.playlist_builder_remove_song, track.title))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.playlist_composer_position, index + 1, total), Modifier.weight(1f),
                    style = typo().bodySmall, color = GratifyColors.TextSecondary)
                IconButton(onClick = onUp, enabled = enabled && index > 0, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(Res.string.playlist_composer_move_up, track.title))
                }
                IconButton(onClick = onDown, enabled = enabled && index < total - 1, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.playlist_composer_move_down, track.title))
                }
            }
        }
    }
}
@Composable
private fun PlaylistSongChoice(track: Track, selected: Boolean, enabled: Boolean, alreadyAdded: Boolean = false, onToggle: () -> Unit) {
    val label = stringResource(if (alreadyAdded) Res.string.playlist_already_has_song else if (selected) Res.string.playlist_builder_remove_song else Res.string.playlist_builder_add_song, track.title)
    Row(Modifier.fillMaxWidth().clip(GratifyShapes.small).clickable(enabled = enabled && !alreadyAdded, role = Role.Checkbox, onClick = onToggle)
        .semantics { contentDescription = label; toggleableState = if(selected) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
        .heightIn(min = 68.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(GratifyShapes.small).background(GratifyColors.SurfaceRaised), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = GratifyColors.TextSecondary)
            track.thumbnails?.lastOrNull()?.url?.takeIf(String::isNotBlank)?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = typo().bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artists?.joinToString(", ") { it.name }.orEmpty(), style = typo().bodySmall, color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(if (selected || alreadyAdded) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, null,
                tint = if (selected) GratifyColors.Accent else GratifyColors.TextSecondary)
        }
    }
}

@Composable
private fun PlaylistBuilderNotice(title: String, hint: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = typo().titleMedium)
        Text(hint, style = typo().bodyMedium, color = GratifyColors.TextSecondary)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PlaylistBuilderLoading() {
    val description = stringResource(Res.string.playlist_builder_loading)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(GratifyColors.SurfaceRaised, GratifyShapes.small))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(.65f).height(14.dp).background(GratifyColors.SurfaceRaised, CircleShape))
                Box(Modifier.fillMaxWidth(.4f).height(10.dp).background(GratifyColors.SurfaceRaised, CircleShape))
            }
        } }
    }
}
