package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.domain.data.entities.ArtistEntity
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.ArtistViewModel
import com.tan.gratify.viewModel.SearchScreenUIState
import com.tan.gratify.viewModel.SearchViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSelectionScreen(
    navController: NavController,
    searchViewModel: SearchViewModel = koinInject(),
    artistViewModel: ArtistViewModel = koinInject()
) {
    val searchScreenState by searchViewModel.searchScreenState.collectAsStateWithLifecycle()
    val searchUIState by searchViewModel.searchScreenUIState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    
    // Track followed artists locally for immediate UI update in this screen
    val followedArtistsIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        if (searchQuery.isEmpty()) {
            searchViewModel.fetchRecommendedArtists()
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212), // Dark background mimicking the screenshot
        floatingActionButton = {
            if (followedArtistsIds.isNotEmpty()) {
                Button(
                    onClick = { navController.navigateUp() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        "Selesai", 
                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black), 
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pilih artis lain yang kamu suka.",
                    style = typo().headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        searchQuery = ""
                        searchViewModel.fetchRecommendedArtists()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Segarkan Rekomendasi",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            TextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    if (it.isEmpty()) {
                        searchViewModel.fetchRecommendedArtists()
                    }
                },
                placeholder = { 
                    Text("Cari", style = typo().bodyLarge.copy(fontWeight = FontWeight.SemiBold)) 
                },
                leadingIcon = { 
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Black) 
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchViewModel.fetchRecommendedArtists()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus", tint = Color.Black)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedPlaceholderColor = Color.DarkGray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotEmpty()) {
                            searchViewModel.searchArtists(searchQuery)
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (searchUIState) {
                    is SearchScreenUIState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                    }
                    is SearchScreenUIState.Success -> {
                        val artists = searchScreenState.searchArtistsResult
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(artists, key = { it.browseId }) { artist ->
                                val isFollowed = followedArtistsIds.contains(artist.browseId)
                                ArtistSelectionItem(
                                    artist = artist,
                                    isFollowed = isFollowed,
                                    onClick = {
                                        if (isFollowed) {
                                            followedArtistsIds.remove(artist.browseId)
                                            artistViewModel.updateFollowed(0, artist.browseId)
                                        } else {
                                            followedArtistsIds.add(artist.browseId)
                                            artistViewModel.insertArtist(
                                                ArtistEntity(
                                                    channelId = artist.browseId,
                                                    name = artist.artist,
                                                    thumbnails = artist.thumbnails.lastOrNull()?.url,
                                                    followed = true
                                                )
                                            )
                                            artistViewModel.updateFollowed(1, artist.browseId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    is SearchScreenUIState.Error -> {
                        Text(
                            "Terjadi kesalahan. Coba lagi.",
                            color = Color.Red,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ArtistSelectionItem(
    artist: ArtistsResult,
    isFollowed: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val borderWidth = if (isFollowed) 3.dp else 0.dp
        val borderColor = if (isFollowed) Color.White else Color.Transparent
        
        AsyncImage(
            model = artist.thumbnails.lastOrNull()?.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .border(borderWidth, borderColor, CircleShape)
                .background(Color.DarkGray)
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = artist.artist,
            style = typo().bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
