package com.tan.gratifymusic.ui.screen.other

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.common.Config
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.albums.AlbumsResult
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.data.model.searchResult.videos.VideosResult
import com.tan.domain.data.type.SearchResultType
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.utils.connectArtists
import com.tan.domain.utils.toSongEntity
import com.tan.domain.utils.toTrack
import com.tan.gratifymusic.extension.getStringBlocking
import com.tan.gratifymusic.ui.component.ArtistFullWidthItems
import com.tan.gratifymusic.ui.component.Chip
import com.tan.gratifymusic.ui.component.EndOfPage
import com.tan.gratifymusic.ui.component.NowPlayingBottomSheet
import com.tan.gratifymusic.ui.component.PlaylistFullWidthItems
import com.tan.gratifymusic.ui.component.ShimmerSearchItem
import com.tan.gratifymusic.ui.component.GratifyMusicChartButton
import com.tan.gratifymusic.ui.component.SongFullWidthItems
import com.tan.gratifymusic.ui.navigation.destination.list.AlbumDestination
import com.tan.gratifymusic.ui.navigation.destination.list.ArtistDestination
import com.tan.gratifymusic.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratifymusic.ui.navigation.destination.list.PodcastDestination
import com.tan.gratifymusic.ui.navigation.destination.home.MoodDestination
import com.tan.gratifymusic.ui.theme.typo
import com.tan.gratifymusic.viewModel.HomeViewModel
import com.tan.gratifymusic.viewModel.SearchScreenUIState
import com.tan.gratifymusic.viewModel.SearchType
import com.tan.gratifymusic.viewModel.SearchViewModel
import com.tan.gratifymusic.viewModel.SharedViewModel
import com.tan.gratifymusic.viewModel.toStringRes
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import gratifymusic.composeapp.generated.resources.Res
import gratifymusic.composeapp.generated.resources.albums
import gratifymusic.composeapp.generated.resources.artists
import gratifymusic.composeapp.generated.resources.baseline_arrow_outward_24
import gratifymusic.composeapp.generated.resources.baseline_close_24
import gratifymusic.composeapp.generated.resources.baseline_history_24
import gratifymusic.composeapp.generated.resources.baseline_search_24
import gratifymusic.composeapp.generated.resources.clear_search_history
import gratifymusic.composeapp.generated.resources.error_occurred
import gratifymusic.composeapp.generated.resources.everything_you_need
import gratifymusic.composeapp.generated.resources.holder
import gratifymusic.composeapp.generated.resources.in_search
import gratifymusic.composeapp.generated.resources.no_results_found
import gratifymusic.composeapp.generated.resources.playlists
import gratifymusic.composeapp.generated.resources.podcasts
import gratifymusic.composeapp.generated.resources.retry
import gratifymusic.composeapp.generated.resources.search_for
import gratifymusic.composeapp.generated.resources.search_for_songs_artists_albums_playlists_and_more
import gratifymusic.composeapp.generated.resources.song
import gratifymusic.composeapp.generated.resources.videos
import gratifymusic.composeapp.generated.resources.what_do_you_want_to_listen_to

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = koinInject(),
    sharedViewModel: SharedViewModel = koinInject(),
    homeViewModel: HomeViewModel = koinInject(),
    navController: NavController,
) {
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val searchScreenState by searchViewModel.searchScreenState.collectAsStateWithLifecycle()
    val uiState by searchViewModel.searchScreenUIState.collectAsStateWithLifecycle()
    val searchHistory by searchViewModel.searchHistory.collectAsStateWithLifecycle()
    val exploreMoodItem by homeViewModel.exploreMoodItem.collectAsStateWithLifecycle()

    var searchUIType by rememberSaveable { mutableStateOf(SearchUIType.EMPTY) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var isSearchSubmitted by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    var isFocused by rememberSaveable { mutableStateOf(false) }

    val searchForString = stringResource(Res.string.search_for)
    val songString = stringResource(Res.string.song).lowercase()
    val artistString = stringResource(Res.string.artists).lowercase()
    val albumString = stringResource(Res.string.albums).lowercase()
    val playlistString = stringResource(Res.string.playlists).lowercase()
    val videoString = stringResource(Res.string.videos).lowercase()
    val podcastString = stringResource(Res.string.podcasts).lowercase()

    // Animated Placeholder
    val placeholderTexts =
        remember {
            listOf(
                "$searchForString $songString...",
                "$searchForString $artistString...",
                "$searchForString $albumString...",
                "$searchForString $playlistString...",
                "$searchForString $videoString...",
                "$searchForString $podcastString...",
            )
        }

    var currentPlaceholderIndex by remember { mutableIntStateOf(0) }

    // Animate placeholder - pause when focused
    LaunchedEffect(isFocused) {
        while (!isFocused) {
            delay(3000) // Change every 3 seconds
            currentPlaceholderIndex = (currentPlaceholderIndex + 1) % placeholderTexts.size
        }
    }

    var sheetSong by remember { mutableStateOf<SongEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val currentVideoId by searchViewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val chipRowState = rememberScrollState()
    val pullToRefreshState = rememberPullToRefreshState()

    val onMoreClick: (SongEntity) -> Unit = { song ->
        sheetSong = song
        showBottomSheet = true
    }

    LaunchedEffect(searchText) {
        if (isFocused) {
            isSearchSubmitted = false
            isExpanded = true
        }
        if (searchText.isNotEmpty() && isFocused) {
            searchViewModel.suggestQuery(searchText)
        }
    }

    LaunchedEffect(isSearchSubmitted) {
        if (isSearchSubmitted) {
            isExpanded = false
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            isExpanded = true
        }
    }

    LaunchedEffect(isExpanded, searchText, isFocused) {
        searchUIType =
            if (searchText.isNotEmpty() && isExpanded) {
                SearchUIType.SEARCH_SUGGESTIONS
            } else if (isFocused && isExpanded) {
                SearchUIType.SEARCH_HISTORY
            } else if (searchText.isEmpty()) {
                SearchUIType.EMPTY
            } else {
                SearchUIType.SEARCH_RESULTS
            }
    }

    if (showBottomSheet) {
        NowPlayingBottomSheet(
            onDismiss = {
                showBottomSheet = false
                sheetSong = null
            },
            navController = navController,
            song = sheetSong,
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(vertical = 10.dp),
    ) {
        // Search Bar with Animated Placeholder
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchText,
                    onQueryChange = { newText ->
                        searchText = newText
                    },
                    onSearch = { query ->
                        if (query.isNotEmpty()) {
                            isSearchSubmitted = true
                            focusManager.clearFocus()
                            searchViewModel.insertSearchHistory(query)
                            when (searchScreenState.searchType) {
                                SearchType.ALL -> searchViewModel.searchAll(query)
                                SearchType.SONGS -> searchViewModel.searchSongs(query)
                                SearchType.VIDEOS -> searchViewModel.searchVideos(query)
                                SearchType.ALBUMS -> searchViewModel.searchAlbums(query)
                                SearchType.ARTISTS -> searchViewModel.searchArtists(query)
                                SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(query)
                                SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(query)
                                SearchType.PODCASTS -> searchViewModel.searchPodcast(query)
                            }
                        }
                    },
                    expanded = false,
                    onExpandedChange = {},
                    enabled = true,
                    placeholder = {
                        // Animated placeholder text
                        AnimatedContent(
                            targetState = currentPlaceholderIndex,
                            transitionSpec = {
                                (
                                    fadeIn(animationSpec = tween(500)) +
                                        slideInVertically { height -> height }
                                ).togetherWith(
                                    fadeOut(animationSpec = tween(500)) +
                                        slideOutVertically { height -> -height },
                                )
                            },
                            label = "placeholder_animation",
                        ) { index ->
                            Text(
                                text = placeholderTexts[index],
                                style = typo().labelMedium,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_search_24),
                            contentDescription = "Search",
                        )
                    },
                    trailingIcon = {
                        // X button only shows when there's text
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                modifier = Modifier.clip(CircleShape),
                                onClick = {
                                    searchText = ""
                                    isSearchSubmitted = false
                                },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.baseline_close_24),
                                    contentDescription = "Clear search",
                                )
                            }
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                    }.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            content = {},
        )

        Crossfade(targetState = searchUIType) {
            when (it) {
                SearchUIType.SEARCH_SUGGESTIONS -> {
                    LazyColumn(
                        Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 10.dp,
                        ),
                    ) {
                        items(searchScreenState.suggestYTItems) { item ->
                            SuggestItemRow(
                                searchResult = item,
                                onItemClick = { item ->
                                    when (item) {
                                        is SongsResult, is VideosResult -> {
                                            val firstTrack: Track = (item as? SongsResult)?.toTrack() ?: (item as VideosResult).toTrack()
                                            searchViewModel.setQueueData(
                                                QueueData.Data(
                                                    listTracks = arrayListOf(firstTrack),
                                                    firstPlayedTrack = firstTrack,
                                                    playlistId = "RDAMVM${firstTrack.videoId}",
                                                    playlistName = "\"${searchText}\" ${getStringBlocking(Res.string.in_search)}",
                                                    playlistType = PlaylistType.RADIO,
                                                    continuation = null,
                                                ),
                                            )
                                            searchViewModel.loadMediaItem(firstTrack, type = Config.SONG_CLICK)
                                        }

                                        is ArtistsResult -> {
                                            navController.navigate(
                                                ArtistDestination(item.browseId),
                                            )
                                        }

                                        is AlbumsResult -> {
                                            navController.navigate(
                                                AlbumDestination(item.browseId),
                                            )
                                        }

                                        is PlaylistsResult -> {
                                            navController.navigate(
                                                PlaylistDestination(
                                                    item.browseId,
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        items(searchScreenState.suggestQueries) { suggestion ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            onClick = {
                                                searchText = suggestion
                                                focusManager.clearFocus()
                                                isSearchSubmitted = true
                                                searchViewModel.insertSearchHistory(suggestion)
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchViewModel.searchAll(suggestion)
                                                    SearchType.SONGS -> searchViewModel.searchSongs(suggestion)
                                                    SearchType.VIDEOS -> searchViewModel.searchVideos(suggestion)
                                                    SearchType.ALBUMS -> searchViewModel.searchAlbums(suggestion)
                                                    SearchType.ARTISTS -> searchViewModel.searchArtists(suggestion)
                                                    SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(suggestion)
                                                    SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(suggestion)
                                                    SearchType.PODCASTS -> searchViewModel.searchPodcast(suggestion)
                                                }
                                            },
                                        ).padding(horizontal = 12.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = suggestion,
                                    style = typo().bodyMedium,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        searchText = suggestion
                                        focusRequester.requestFocus()
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.baseline_arrow_outward_24),
                                        contentDescription = "Search suggestion",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                        item {
                            EndOfPage(
                                withoutCredit = true,
                            )
                        }
                    }
                }

                SearchUIType.SEARCH_HISTORY -> {
                    // Search history state
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp,
                                ),
                    ) {
                        LazyColumn {
                            stickyHeader {
                                Crossfade(
                                    targetState = searchHistory.isNotEmpty(),
                                ) {
                                    if (it) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black),
                                        ) {
                                            TextButton(
                                                onClick = { searchViewModel.deleteSearchHistory() },
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.clear_search_history),
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            items(searchHistory) { historyItem ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchText = historyItem
                                                focusManager.clearFocus()
                                                isSearchSubmitted = true
                                                searchViewModel.insertSearchHistory(historyItem)
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchViewModel.searchAll(historyItem)
                                                    SearchType.SONGS -> searchViewModel.searchSongs(historyItem)
                                                    SearchType.VIDEOS -> searchViewModel.searchVideos(historyItem)
                                                    SearchType.ALBUMS -> searchViewModel.searchAlbums(historyItem)
                                                    SearchType.ARTISTS -> searchViewModel.searchArtists(historyItem)
                                                    SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(historyItem)
                                                    SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(historyItem)
                                                    SearchType.PODCASTS -> searchViewModel.searchPodcast(historyItem)
                                                }
                                            }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.baseline_history_24),
                                        contentDescription = "Search history",
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 12.dp))
                                    Text(
                                        text = historyItem,
                                        style = typo().bodyMedium,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            searchText = historyItem
                                            focusRequester.requestFocus()
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.baseline_arrow_outward_24),
                                            contentDescription = "Search suggestion",
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                            item {
                                EndOfPage(
                                    withoutCredit = true,
                                )
                            }
                        }
                    }
                }

                SearchUIType.EMPTY -> {
                    val curatedGradients = remember {
                        listOf(
                            Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFF9C27B0))), // Pink -> Purple
                            Brush.linearGradient(listOf(Color(0xFF009688), Color(0xFF00796B))), // Teal
                            Brush.linearGradient(listOf(Color(0xFF673AB7), Color(0xFF512DA8))), // Purple
                            Brush.linearGradient(listOf(Color(0xFF3F51B5), Color(0xFF303F9F))), // Indigo
                            Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFE64A19))), // Orange
                            Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFF57C00))), // Amber
                            Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF388E3C))), // Green
                            Brush.linearGradient(listOf(Color(0xFF03A9F4), Color(0xFF0288D1))), // Light Blue
                            Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE040FB))), // Purple -> Magenta
                            Brush.linearGradient(listOf(Color(0xFF00BCD4), Color(0xFF0097A7))), // Cyan
                            Brush.linearGradient(listOf(Color(0xFF607D8B), Color(0xFF455A64)))  // Blue Grey
                        )
                    }

                    val startExploring = remember {
                        listOf(
                            FallbackCategory("Musik", HomeViewModel.HOME_PARAMS_RELAX, "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Podcast", HomeViewModel.HOME_PARAMS_FOCUS, "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Acara Langsung", HomeViewModel.HOME_PARAMS_PARTY, "https://images.unsplash.com/photo-1506157786151-b8491531f063?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("K-Pop Hub", HomeViewModel.HOME_PARAMS_ENERGIZE, "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=200&auto=format&fit=crop&q=60")
                        )
                    }

                    val fallbackGenres = remember {
                        listOf(
                            FallbackCategory("Indie", HomeViewModel.HOME_PARAMS_RELAX, "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Musik Indonesia", HomeViewModel.HOME_PARAMS_RELAX, "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("K-pop", HomeViewModel.HOME_PARAMS_ENERGIZE, "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Tangga Lagu", HomeViewModel.HOME_PARAMS_PARTY, "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Peringkat Podcast", HomeViewModel.HOME_PARAMS_FOCUS, "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Rilis Baru Podcast", HomeViewModel.HOME_PARAMS_FEEL_GOOD, "https://images.unsplash.com/photo-1487180142328-054b783fc471?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Video Podcast", HomeViewModel.HOME_PARAMS_COMMUTE, "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Di mobil", HomeViewModel.HOME_PARAMS_COMMUTE, "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Anak-anak & Keluarga", HomeViewModel.HOME_PARAMS_SLEEP, "https://images.unsplash.com/photo-1511295742364-92767fa62d9f?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Hip Hop", HomeViewModel.HOME_PARAMS_PARTY, "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Rock", HomeViewModel.HOME_PARAMS_ENERGIZE, "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Temukan", HomeViewModel.HOME_PARAMS_FEEL_GOOD, "https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Pop", HomeViewModel.HOME_PARAMS_FEEL_GOOD, "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Jazz", HomeViewModel.HOME_PARAMS_RELAX, "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Romance", HomeViewModel.HOME_PARAMS_ROMANCE, "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=200&auto=format&fit=crop&q=60"),
                            FallbackCategory("Workout", HomeViewModel.HOME_PARAMS_WORKOUT, "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=200&auto=format&fit=crop&q=60")
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                    ) {
                        item {
                            Text(
                                text = "Mulai jelajahi",
                                style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        val chunkedExplore = startExploring.chunked(2)
                        items(chunkedExplore.size) { rowIndex ->
                            val rowItems = chunkedExplore[rowIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEachIndexed { colIndex, item ->
                                    val gradIndex = (rowIndex * 2 + colIndex) % curatedGradients.size
                                    ExploreCard(
                                        title = item.title,
                                        gradient = curatedGradients[gradIndex],
                                        imageUrl = item.imageUrl,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            navController.navigate(MoodDestination(item.params))
                                        }
                                    )
                                }
                                if (rowItems.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Temukan sesuatu yang lain",
                                style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                            )

                            val moments = exploreMoodItem?.moodsMoments
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (!moments.isNullOrEmpty()) {
                                    items(moments.size) { index ->
                                        val item = moments[index]
                                        val gradIndex = (index + 4) % curatedGradients.size
                                        MoodMomentRowItem(
                                            title = item.title,
                                            params = item.params,
                                            gradient = curatedGradients[gradIndex],
                                            imageUrl = getUnsplashImageForGenre(item.title),
                                            navController = navController
                                        )
                                    }
                                } else {
                                    val fallbackMoods = listOf(
                                        FallbackCategory("Relax", HomeViewModel.HOME_PARAMS_RELAX, "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=200&auto=format&fit=crop&q=60"),
                                        FallbackCategory("Sleep", HomeViewModel.HOME_PARAMS_SLEEP, "https://images.unsplash.com/photo-1511295742364-92767fa62d9f?w=200&auto=format&fit=crop&q=60"),
                                        FallbackCategory("Energize", HomeViewModel.HOME_PARAMS_ENERGIZE, "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&auto=format&fit=crop&q=60"),
                                        FallbackCategory("Workout", HomeViewModel.HOME_PARAMS_WORKOUT, "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=200&auto=format&fit=crop&q=60"),
                                        FallbackCategory("Focus", HomeViewModel.HOME_PARAMS_FOCUS, "https://images.unsplash.com/photo-1488190211105-8b0e65b80b4e?w=200&auto=format&fit=crop&q=60"),
                                        FallbackCategory("Romance", HomeViewModel.HOME_PARAMS_ROMANCE, "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=200&auto=format&fit=crop&q=60")
                                    )
                                    items(fallbackMoods.size) { index ->
                                        val item = fallbackMoods[index]
                                        val gradIndex = (index + 4) % curatedGradients.size
                                        MoodMomentRowItem(
                                            title = item.title,
                                            params = item.params,
                                            gradient = curatedGradients[gradIndex],
                                            imageUrl = item.imageUrl,
                                            navController = navController
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Jelajahi semua",
                                style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                            )
                        }

                        val genres = exploreMoodItem?.genres
                        if (!genres.isNullOrEmpty()) {
                            val chunkedGenres = genres.chunked(2)
                            items(chunkedGenres.size) { rowIndex ->
                                val rowItems = chunkedGenres[rowIndex]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEachIndexed { colIndex, item ->
                                        val gradIndex = (rowIndex * 2 + colIndex + 2) % curatedGradients.size
                                        ExploreCard(
                                            title = item.title,
                                            gradient = curatedGradients[gradIndex],
                                            imageUrl = getUnsplashImageForGenre(item.title),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                navController.navigate(MoodDestination(item.params))
                                            }
                                        )
                                    }
                                    if (rowItems.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            val chunkedFallbackGenres = fallbackGenres.chunked(2)
                            items(chunkedFallbackGenres.size) { rowIndex ->
                                val rowItems = chunkedFallbackGenres[rowIndex]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEachIndexed { colIndex, item ->
                                        val gradIndex = (rowIndex * 2 + colIndex + 2) % curatedGradients.size
                                        ExploreCard(
                                            title = item.title,
                                            gradient = curatedGradients[gradIndex],
                                            imageUrl = item.imageUrl,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                navController.navigate(MoodDestination(item.params))
                                            }
                                        )
                                    }
                                    if (rowItems.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            GratifyMusicChartButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                uriHandler.openUri("https://chart.gratifymusic.org")
                            }
                        }
                    }
                }

                SearchUIType.SEARCH_RESULTS -> {
                    // Content area
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Filter chips
                        Row(
                            modifier =
                                Modifier
                                    .horizontalScroll(chipRowState)
                                    .padding(top = 10.dp)
                                    .padding(horizontal = 12.dp),
                        ) {
                            SearchType.entries.forEach { id ->
                                val isSelected = id == searchScreenState.searchType
                                Spacer(modifier = Modifier.width(4.dp))
                                Chip(
                                    isAnimated = uiState is SearchScreenUIState.Loading,
                                    isSelected = isSelected,
                                    text = stringResource(id.toStringRes()),
                                ) {
                                    searchViewModel.setSearchType(id)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        PullToRefreshBox(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 10.dp),
                            state = pullToRefreshState,
                            onRefresh = {
                                val query = searchText.trim()
                                if (query.isNotEmpty()) {
                                    isSearchSubmitted = true
                                    searchViewModel.insertSearchHistory(query)
                                    when (searchScreenState.searchType) {
                                        SearchType.ALL -> searchViewModel.searchAll(query)
                                        SearchType.SONGS -> searchViewModel.searchSongs(query)
                                        SearchType.VIDEOS -> searchViewModel.searchVideos(query)
                                        SearchType.ALBUMS -> searchViewModel.searchAlbums(query)
                                        SearchType.ARTISTS -> searchViewModel.searchArtists(query)
                                        SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(query)
                                        SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(query)
                                        SearchType.PODCASTS -> searchViewModel.searchPodcast(query)
                                    }
                                }
                            },
                            isRefreshing = uiState is SearchScreenUIState.Loading,
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    state = pullToRefreshState,
                                    isRefreshing = uiState is SearchScreenUIState.Loading,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    containerColor = PullToRefreshDefaults.indicatorContainerColor,
                                    color = PullToRefreshDefaults.indicatorColor,
                                    maxDistance = PullToRefreshDefaults.PositionalThreshold - 5.dp,
                                )
                            },
                        ) {
                            Crossfade(targetState = uiState) { uiState ->
                                when (uiState) {
                                    is SearchScreenUIState.Loading -> {
                                        // Loading state
                                        LazyColumn {
                                            items(10) {
                                                ShimmerSearchItem()
                                            }
                                        }
                                    }

                                    is SearchScreenUIState.Success -> {
                                        // Success state with results
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            // Search Results List
                                            val currentResults =
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchScreenState.searchAllResult
                                                    SearchType.SONGS -> searchScreenState.searchSongsResult
                                                    SearchType.VIDEOS -> searchScreenState.searchVideosResult
                                                    SearchType.ALBUMS -> searchScreenState.searchAlbumsResult
                                                    SearchType.ARTISTS -> searchScreenState.searchArtistsResult
                                                    SearchType.PLAYLISTS -> searchScreenState.searchPlaylistsResult
                                                    SearchType.FEATURED_PLAYLISTS -> searchScreenState.searchFeaturedPlaylistsResult
                                                    SearchType.PODCASTS -> searchScreenState.searchPodcastsResult
                                                }

                                            Crossfade(targetState = currentResults.isNotEmpty()) {
                                                if (it) {
                                                    LazyColumn(
                                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                                        state = rememberLazyListState(),
                                                    ) {
                                                        items(currentResults) { result ->
                                                            when (result) {
                                                                is SongsResult -> {
                                                                    SongFullWidthItems(
                                                                        track = result.toTrack(),
                                                                        isPlaying = result.videoId == currentVideoId,
                                                                        modifier = Modifier,
                                                                        onMoreClickListener = {
                                                                            onMoreClick(result.toTrack().toSongEntity())
                                                                        },
                                                                        onClickListener = {
                                                                            val firstTrack = result.toTrack()
                                                                            searchViewModel.setQueueData(
                                                                                QueueData.Data(
                                                                                    listTracks = arrayListOf(firstTrack),
                                                                                    firstPlayedTrack = firstTrack,
                                                                                    playlistId = "RDAMVM${result.videoId}",
                                                                                    playlistName =
                                                                                        "\"${searchText}\" ${
                                                                                            getStringBlocking(
                                                                                                Res.string.in_search,
                                                                                            )
                                                                                        }",
                                                                                    playlistType = PlaylistType.RADIO,
                                                                                    continuation = null,
                                                                                ),
                                                                            )
                                                                            searchViewModel.loadMediaItem(firstTrack, Config.SONG_CLICK)
                                                                        },
                                                                        onAddToQueue = {
                                                                            sharedViewModel.addListToQueue(
                                                                                arrayListOf(result.toTrack()),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is VideosResult -> {
                                                                    SongFullWidthItems(
                                                                        track = result.toTrack(),
                                                                        isPlaying = result.videoId == currentVideoId,
                                                                        modifier = Modifier,
                                                                        onMoreClickListener = {
                                                                            onMoreClick(result.toTrack().toSongEntity())
                                                                        },
                                                                        onClickListener = {
                                                                            val firstTrack = result.toTrack()
                                                                            searchViewModel.setQueueData(
                                                                                QueueData.Data(
                                                                                    listTracks = arrayListOf(firstTrack),
                                                                                    firstPlayedTrack = firstTrack,
                                                                                    playlistId = "RDAMVM${result.videoId}",
                                                                                    playlistName =
                                                                                        "\"${searchText}\" ${
                                                                                            getStringBlocking(
                                                                                                Res.string.in_search,
                                                                                            )
                                                                                        }",
                                                                                    playlistType = PlaylistType.RADIO,
                                                                                    continuation = null,
                                                                                ),
                                                                            )
                                                                            searchViewModel.loadMediaItem(firstTrack, Config.VIDEO_CLICK)
                                                                        },
                                                                        onAddToQueue = {
                                                                            sharedViewModel.addListToQueue(
                                                                                arrayListOf(result.toTrack()),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is AlbumsResult -> {
                                                                    PlaylistFullWidthItems(
                                                                        data = result,
                                                                        onClickListener = {
                                                                            navController.navigate(
                                                                                AlbumDestination(
                                                                                    result.browseId,
                                                                                ),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is ArtistsResult -> {
                                                                    ArtistFullWidthItems(
                                                                        data = result,
                                                                        onClickListener = {
                                                                            navController.navigate(
                                                                                ArtistDestination(
                                                                                    result.browseId,
                                                                                ),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is PlaylistsResult -> {
                                                                    PlaylistFullWidthItems(
                                                                        data = result,
                                                                        onClickListener = {
                                                                            if (result.resultType == "Podcast") {
                                                                                navController.navigate(
                                                                                    PodcastDestination(
                                                                                        result.browseId,
                                                                                    ),
                                                                                )
                                                                            } else {
                                                                                navController.navigate(
                                                                                    PlaylistDestination(
                                                                                        result.browseId,
                                                                                    ),
                                                                                )
                                                                            }
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        // Space at bottom to account for bottom navigation and mini player
                                                        item { Spacer(modifier = Modifier.height(150.dp)) }
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = stringResource(Res.string.no_results_found),
                                                            style = typo().titleMedium,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth(),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is SearchScreenUIState.Error -> {
                                        Box {
                                            // Error state
                                            Column(
                                                modifier = Modifier.align(Alignment.Center),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.error_occurred),
                                                    style = typo().titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(onClick = {
                                                    if (searchText.isNotEmpty()) {
                                                        searchViewModel.searchAll(searchText)
                                                    }
                                                }) {
                                                    Text(text = stringResource(Res.string.retry))
                                                }
                                            }
                                        }
                                    }

                                    SearchScreenUIState.Empty -> {
                                        // Empty state
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.no_results_found),
                                                style = typo().titleMedium,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
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
fun SuggestItemRow(
    searchResult: SearchResultType,
    onItemClick: (SearchResultType) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onItemClick(searchResult) }
                .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val url =
            when (searchResult) {
                is SongsResult -> {
                    searchResult.thumbnails?.lastOrNull()?.url
                }

                is AlbumsResult -> {
                    searchResult.thumbnails.lastOrNull()?.url
                }

                is ArtistsResult -> {
                    searchResult.thumbnails.lastOrNull()?.url
                }

                is PlaylistsResult -> {
                    searchResult.thumbnails.lastOrNull()?.url
                }

                is VideosResult -> {
                    searchResult.thumbnails?.lastOrNull()?.url
                }

                else -> {
                    null
                }
            }

        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(url)
                        .crossfade(true)
                        .build(),
                placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(
                            if (searchResult is ArtistsResult) {
                                CircleShape
                            } else {
                                RoundedCornerShape(4.dp)
                            },
                        ),
            )
        }

        Spacer(modifier = Modifier.padding(horizontal = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val title =
                when (searchResult) {
                    is SongsResult -> {
                        searchResult.title
                    }

                    is AlbumsResult -> {
                        searchResult.title
                    }

                    is ArtistsResult -> {
                        searchResult.artist
                    }

                    is PlaylistsResult -> {
                        searchResult.title
                    }

                    is VideosResult -> {
                        searchResult.title
                    }

                    else -> {
                        null
                    }
                } ?: "Unknown"

            Text(
                text = title,
                style = typo().labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))

            val subtitle =
                when (searchResult) {
                    is SongsResult -> searchResult.artists?.map { it.name }?.connectArtists()
                    is AlbumsResult -> searchResult.artists.map { it.name }.connectArtists()
                    is PlaylistsResult -> searchResult.author.ifEmpty { "YouTube Music" }
                    is ArtistsResult -> stringResource(Res.string.artists)
                    is VideosResult -> searchResult.artists?.map { it.name }?.connectArtists()
                    else -> null
                } ?: "Unknown"

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

enum class SearchUIType {
    EMPTY,
    SEARCH_HISTORY,
    SEARCH_SUGGESTIONS,
    SEARCH_RESULTS,
}

@Composable
fun ExploreCard(
    title: String,
    gradient: Brush,
    imageUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            modifier = Modifier.align(Alignment.TopStart)
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    rotationZ = -20f
                    translationX = 15f
                    translationY = 15f
                }
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MoodMomentRowItem(
    title: String,
    params: String,
    gradient: Brush,
    imageUrl: String,
    navController: NavController
) {
    Box(
        modifier = Modifier
            .width(130.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable {
                navController.navigate(MoodDestination(params))
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageUrl)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            placeholder = painterResource(Res.drawable.holder),
            error = painterResource(Res.drawable.holder),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.6f
        )
        Text(
            text = "#${title.lowercase().replace(" ", "").replace("&", "")}",
            style = typo().bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

fun getUnsplashImageForGenre(title: String): String {
    val lower = title.lowercase()
    return when {
        lower.contains("pop") -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=200&auto=format&fit=crop&q=60"
        lower.contains("rock") -> "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=200&auto=format&fit=crop&q=60"
        lower.contains("hip") || lower.contains("rap") -> "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=200&auto=format&fit=crop&q=60"
        lower.contains("jazz") -> "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=200&auto=format&fit=crop&q=60"
        lower.contains("classic") -> "https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?w=200&auto=format&fit=crop&q=60"
        lower.contains("electron") || lower.contains("dance") || lower.contains("edm") -> "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=200&auto=format&fit=crop&q=60"
        lower.contains("r&b") || lower.contains("rnb") -> "https://images.unsplash.com/photo-1487180142328-054b783fc471?w=200&auto=format&fit=crop&q=60"
        lower.contains("chill") || lower.contains("relax") -> "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=200&auto=format&fit=crop&q=60"
        lower.contains("workout") || lower.contains("gym") || lower.contains("energi") -> "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=200&auto=format&fit=crop&q=60"
        lower.contains("focus") || lower.contains("study") -> "https://images.unsplash.com/photo-1488190211105-8b0e65b80b4e?w=200&auto=format&fit=crop&q=60"
        lower.contains("sleep") -> "https://images.unsplash.com/photo-1511295742364-92767fa62d9f?w=200&auto=format&fit=crop&q=60"
        lower.contains("romance") || lower.contains("love") -> "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=200&auto=format&fit=crop&q=60"
        lower.contains("podcast") -> "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=200&auto=format&fit=crop&q=60"
        lower.contains("indie") -> "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&auto=format&fit=crop&q=60"
        lower.contains("indonesia") -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&auto=format&fit=crop&q=60"
        lower.contains("tangga") || lower.contains("chart") -> "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=200&auto=format&fit=crop&q=60"
        else -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=200&auto=format&fit=crop&q=60"
    }
}

data class FallbackCategory(
    val title: String,
    val params: String,
    val imageUrl: String
)