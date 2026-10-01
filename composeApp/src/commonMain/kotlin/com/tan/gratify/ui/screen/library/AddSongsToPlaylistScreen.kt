package com.tan.gratify.ui.screen.library

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.utils.toTrack
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.AddSongsToPlaylistViewModel
import org.koin.compose.koinInject

private const val MIN_SONGS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistScreen(
    playlistTitle: String,
    navController: NavController,
    viewModel: AddSongsToPlaylistViewModel = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(uiState.createdPlaylistId) {
        uiState.createdPlaylistId?.let { id ->
            navController.navigate(LocalPlaylistDestination(id = id)) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
        }
    }

    Scaffold(
        containerColor = GratifyColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tambah lagu",
                        style = typo().titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GratifyColors.Background),
            )
        },
        bottomBar = {
            Surface(
                color = GratifyColors.Surface,
                tonalElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${uiState.selectedTracks.size}/$MIN_SONGS lagu minimum",
                        style = typo().bodyMedium,
                        color = if (uiState.selectedTracks.size >= MIN_SONGS) GratifyColors.Accent else GratifyColors.TextSecondary,
                    )
                    Button(
                        onClick = { viewModel.savePlaylist(playlistTitle) },
                        enabled = uiState.selectedTracks.size >= MIN_SONGS && !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GratifyColors.Accent,
                            contentColor = Color.Black,
                            disabledContainerColor = GratifyColors.Accent.copy(alpha = 0.3f),
                            disabledContentColor = Color.Black.copy(alpha = 0.3f),
                        ),
                        shape = RoundedCornerShape(50),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black,
                            )
                        } else {
                            Text(
                                text = "Buat Playlist",
                                style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    if (query.isEmpty()) {
                        viewModel.clearSearch()
                    }
                },
                placeholder = {
                    Text("Cari lagu", style = typo().bodyLarge)
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = GratifyColors.TextSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus", tint = GratifyColors.TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = GratifyShapes.small,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GratifyColors.SurfaceRaised,
                    unfocusedContainerColor = GratifyColors.SurfaceRaised,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedPlaceholderColor = GratifyColors.TextSecondary,
                    focusedPlaceholderColor = GratifyColors.TextSecondary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotEmpty()) {
                            viewModel.searchSongs(searchQuery)
                        }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Selected tracks count
            AnimatedVisibility(
                visible = uiState.selectedTracks.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = "${uiState.selectedTracks.size} lagu dipilih",
                        style = typo().bodySmall,
                        color = if (uiState.selectedTracks.size >= MIN_SONGS) GratifyColors.Accent else GratifyColors.TextSecondary,
                    )
                }
            }

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Selected tracks section
                if (uiState.selectedTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Dipilih",
                            style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(uiState.selectedTracks, key = { "selected_${it.videoId}" }) { track ->
                        SongSelectItem(
                            title = track.title,
                            artist = track.artists?.joinToString(", ") { it.name } ?: "",
                            thumbnailUrl = track.thumbnails?.lastOrNull()?.url ?: "",
                            isSelected = true,
                            onClick = { viewModel.removeTrack(track.videoId) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Search results or recent songs
                if (uiState.isSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = GratifyColors.Accent)
                        }
                    }
                } else if (uiState.searchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Hasil pencarian",
                            style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(uiState.searchResults, key = { "search_${it.videoId}" }) { song ->
                        val track = song.toTrack()
                        val isSelected = uiState.selectedTracks.any { it.videoId == track.videoId }
                        SongSelectItem(
                            title = track.title,
                            artist = track.artists?.joinToString(", ") { it.name } ?: "",
                            thumbnailUrl = track.thumbnails?.lastOrNull()?.url ?: "",
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    viewModel.removeTrack(track.videoId)
                                } else {
                                    viewModel.addTrack(track)
                                }
                            },
                        )
                    }
                } else {
                    // Recent songs as recommendations
                    if (uiState.recentSongs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Rekomendasi untukmu",
                                style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(uiState.recentSongs, key = { "recent_${it.videoId}" }) { song ->
                            val track = song.toTrack()
                            val isSelected = uiState.selectedTracks.any { it.videoId == track.videoId }
                            SongSelectItem(
                                title = song.title,
                                artist = song.artistName?.joinToString(", ") ?: "",
                                thumbnailUrl = song.thumbnails ?: "",
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        viewModel.removeTrack(song.videoId)
                                    } else {
                                        viewModel.addTrack(track)
                                    }
                                },
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Cari lagu untuk ditambahkan ke playlist",
                                    style = typo().bodyMedium,
                                    color = GratifyColors.TextSecondary,
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
private fun SongSelectItem(
    title: String,
    artist: String,
    thumbnailUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(GratifyShapes.extraSmall),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = typo().bodyLarge,
                color = if (isSelected) GratifyColors.Accent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = typo().bodySmall,
                color = GratifyColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (isSelected) "Hapus" else "Tambah",
                tint = if (isSelected) GratifyColors.Accent else Color.White,
            )
        }
    }
}
