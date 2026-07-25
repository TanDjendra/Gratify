package com.tan.gratify.ui.screen.library

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
        containerColor = Color(0xFF121212),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF1E1E1E),
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
                        color = if (uiState.selectedTracks.size >= MIN_SONGS) Color(0xFF1DB954) else Color.Gray,
                    )
                    Button(
                        onClick = { viewModel.savePlaylist(playlistTitle) },
                        enabled = uiState.selectedTracks.size >= MIN_SONGS && !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF1DB954).copy(alpha = 0.3f),
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
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedPlaceholderColor = Color.Gray,
                    focusedPlaceholderColor = Color.Gray,
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
                        color = if (uiState.selectedTracks.size >= MIN_SONGS) Color(0xFF1DB954) else Color(0xFFB3B3B3),
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
                            CircularProgressIndicator(color = Color(0xFF1DB954))
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
                                    color = Color.Gray,
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
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = typo().bodyLarge,
                color = if (isSelected) Color(0xFF1DB954) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = typo().bodySmall,
                color = Color(0xFFB3B3B3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (isSelected) "Hapus" else "Tambah",
                tint = if (isSelected) Color(0xFF1DB954) else Color.White,
            )
        }
    }
}
