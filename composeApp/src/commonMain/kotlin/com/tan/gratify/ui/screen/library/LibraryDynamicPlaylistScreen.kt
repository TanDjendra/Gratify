package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.rounded.Close
import com.tan.gratify.ui.component.*
import com.tan.gratify.viewModel.UIEvent
import gratify.composeapp.generated.resources.*

import com.tan.gratify.ui.theme.GratifyColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.common.Config
import com.tan.domain.data.entities.ArtistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.utils.LocalResource
import com.tan.domain.utils.toArrayListTrack
import com.tan.domain.utils.toTrack
import com.tan.logger.Logger
import com.tan.gratify.extension.getStringBlocking
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.AnalyticsViewModel
import com.tan.gratify.viewModel.LibraryDynamicPlaylistViewModel
import com.tan.gratify.viewModel.SharedViewModel
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ExperimentalMaterial3Api
fun LibraryDynamicPlaylistScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    type: String,
    viewModel: LibraryDynamicPlaylistViewModel = koinViewModel(),
    analyticsViewModel: AnalyticsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val nowPlayingVideoId by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()

    var chosenSong: SongEntity? by remember { mutableStateOf(null) }
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val favorite by viewModel.listFavoriteSong.collectAsStateWithLifecycle()
    var tempFavorite by remember { mutableStateOf(emptyList<SongEntity>()) }
    val followed by viewModel.listFollowedArtist.collectAsStateWithLifecycle()
    var tempFollowed by remember { mutableStateOf(emptyList<ArtistEntity>()) }
    val mostPlayed by viewModel.listMostPlayedSong.collectAsStateWithLifecycle()
    var tempMostPlayed by remember { mutableStateOf(emptyList<SongEntity>()) }
    val downloaded by viewModel.listDownloadedSong.collectAsStateWithLifecycle()
    var tempDownloaded by remember { mutableStateOf(emptyList<SongEntity>()) }
    val analyticsUIState by analyticsViewModel.analyticsUIState.collectAsStateWithLifecycle()
    var tempTopTracks by remember { mutableStateOf(analyticsUIState.topTracks.data ?: emptyList()) }
    var tempTopArtists by remember { mutableStateOf(analyticsUIState.topArtists.data ?: emptyList()) }
    var tempTopAlbums by remember { mutableStateOf(analyticsUIState.topAlbums.data ?: emptyList()) }
    LaunchedEffect(query, favorite, followed, mostPlayed, downloaded, analyticsUIState) {
        Logger.w("LibraryDynamicPlaylistScreen", "Check query: $query")
        tempFavorite = favorite.filter { it.title.contains(query, ignoreCase = true) }
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempFavorite: $tempFavorite")
        tempFollowed = followed.filter { it.name.contains(query, ignoreCase = true) }
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempFollowed: $tempFollowed")
        tempMostPlayed = mostPlayed.filter { it.title.contains(query, ignoreCase = true) }
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempMostPlayed: $tempMostPlayed")
        tempDownloaded = downloaded.filter { it.title.contains(query, ignoreCase = true) }
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempDownloaded: $tempDownloaded")
        tempTopTracks =
            analyticsUIState.topTracks.data
                ?.filter { it.second.title.contains(query, ignoreCase = true) }
                ?: emptyList()
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempTopTracks: $tempTopTracks")
        tempTopArtists =
            analyticsUIState.topArtists.data
                ?.filter { it.second.name.contains(query, ignoreCase = true) }
                ?: emptyList()
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempTopArtists: $tempTopArtists")
        tempTopAlbums =
            analyticsUIState.topAlbums.data
                ?.filter { it.second.title.contains(query, ignoreCase = true) }
                ?: emptyList()
        Logger.w("LibraryDynamicPlaylistScreen", "Check tempTopAlbums: $tempTopAlbums")
    }

    val collectionType = LibraryDynamicPlaylistType.toType(type)
    val isSongCollection = collectionType != LibraryDynamicPlaylistType.Followed && collectionType != LibraryDynamicPlaylistType.TopArtists && collectionType != LibraryDynamicPlaylistType.TopAlbums
    val collectionSongs = when (collectionType) {
        LibraryDynamicPlaylistType.Favorite -> favorite
        LibraryDynamicPlaylistType.Downloaded -> downloaded
        LibraryDynamicPlaylistType.MostPlayed -> mostPlayed
        LibraryDynamicPlaylistType.TopTracks -> analyticsUIState.topTracks.data.orEmpty().map { it.second }
        else -> emptyList()
    }
    val title = stringResource(collectionType.name())
    val queueData by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val collectionQueueName = if (collectionType == LibraryDynamicPlaylistType.TopTracks) title else stringResource(Res.string.playlist) + " " + title
    val collectionIsPlaying = controller.isPlaying && queueData?.data?.playlistName == collectionQueueName
    val startTopTracks: (Boolean) -> Unit = { shuffle ->
        val songs = if (shuffle) collectionSongs.shuffled() else collectionSongs
        songs.firstOrNull()?.let { first ->
            sharedViewModel.setQueueData(QueueData.Data(listTracks = songs.toArrayListTrack(), firstPlayedTrack = first.toTrack(),
                playlistId = null, playlistName = title, playlistType = PlaylistType.RADIO, continuation = null))
            sharedViewModel.loadMediaItem(first.toTrack(), Config.PLAYLIST_CLICK, 0)
        }
    }
    val lazyState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { lazyState.firstVisibleItemIndex > 0 } }
    LazyColumn(
        modifier = Modifier.background(GratifyColors.Background),
        state = lazyState,
        contentPadding = if (isSongCollection) PaddingValues(bottom = innerPadding.calculateBottomPadding()) else innerPadding,
    ) {
        item(key = "collection-header") {
            if (isSongCollection && !showSearchBar) {
                CollectionDetailHeader(title = title, kind = stringResource(Res.string.playlist), subtitle = stringResource(Res.string.your_playlist),
                    metadata = stringResource(Res.string.album_length, collectionSongs.size.toString(), ""),
                    accent = playlistTitleGradient(title).first(), onBack = { navController.navigateUp() },
                    isPlaying = collectionIsPlaying, canPlay = collectionSongs.isNotEmpty(),
                    onPlay = {
                        if (queueData?.data?.playlistName == collectionQueueName) sharedViewModel.onUIEvent(UIEvent.PlayPause)
                        else if (collectionType == LibraryDynamicPlaylistType.TopTracks) startTopTracks(false) else viewModel.playAll(collectionType)
                    },
                    onShuffle = { if (collectionType == LibraryDynamicPlaylistType.TopTracks) startTopTracks(true) else viewModel.shuffle(collectionType) },
                    artwork = { modifier -> Image(painterPlaylistThumbnail(title, style = typo().headlineLarge, 250.dp to 250.dp), title, modifier) },
                    actions = { CollectionDetailAction(Icons.Rounded.Search, stringResource(Res.string.detail_search_tracks), onClick = { showSearchBar = true }) })
                if (collectionSongs.isEmpty()) CollectionDetailEmpty()
            } else Spacer(Modifier.height(64.dp))
        }
        item {
            AnimatedVisibility(showSearchBar) {
                Spacer(Modifier.height(55.dp))
            }
        }
        val type = LibraryDynamicPlaylistType.toType(type)
        if (type == LibraryDynamicPlaylistType.Followed) {
            items(
                if (query.isNotEmpty() && showSearchBar) {
                    tempFollowed
                } else {
                    followed
                },
                key = { it.channelId },
            ) { artist ->
                ArtistFullWidthItems(
                    artist,
                    onClickListener = {
                        navController.navigate(
                            ArtistDestination(
                                channelId = artist.channelId,
                            ),
                        )
                    },
                )
            }
        } else if (type == LibraryDynamicPlaylistType.TopArtists) {
            when (analyticsUIState.topArtists) {
                is LocalResource.Success if (!analyticsUIState.topArtists.data.isNullOrEmpty()) -> {
                    val data = analyticsUIState.topArtists.data ?: emptyList()
                    items(
                        if (query.isNotEmpty() && showSearchBar) {
                            tempTopArtists
                        } else {
                            data
                        },
                        key = { it.first.hashCode() },
                    ) { artist ->
                        ArtistFullWidthItems(
                            artist.second,
                            rightView = {
                                Box(Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = "${artist.first.playCount} ${stringResource(Res.string.lower_plays)}",
                                        style = typo().bodySmall,
                                    )
                                }
                            },
                            onClickListener = {
                                navController.navigate(
                                    ArtistDestination(
                                        channelId = artist.second.channelId,
                                    ),
                                )
                            },
                        )
                    }
                }

                else -> {}
            }
        } else if (type == LibraryDynamicPlaylistType.TopAlbums) {
            when (analyticsUIState.topAlbums) {
                is LocalResource.Success if (!analyticsUIState.topAlbums.data.isNullOrEmpty()) -> {
                    val data = analyticsUIState.topAlbums.data ?: emptyList()
                    items(
                        if (query.isNotEmpty() && showSearchBar) {
                            tempTopAlbums
                        } else {
                            data
                        },
                        key = { it.first.hashCode() },
                    ) { album ->
                        PlaylistFullWidthItems(
                            album.second,
                            rightView = {
                                Box(Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = "${album.first.playCount} ${stringResource(Res.string.lower_plays)}",
                                        style = typo().bodySmall,
                                    )
                                }
                            },
                            onClickListener = {
                                navController.navigate(
                                    AlbumDestination(
                                        browseId = album.second.browseId,
                                    ),
                                )
                            },
                        )
                    }
                }

                else -> {}
            }
        } else if (type == LibraryDynamicPlaylistType.TopTracks) {
            when (analyticsUIState.topTracks) {
                is LocalResource.Success if (!analyticsUIState.topTracks.data.isNullOrEmpty()) -> {
                    val data = analyticsUIState.topTracks.data ?: emptyList()
                    items(
                        if (query.isNotEmpty() && showSearchBar) {
                            tempTopTracks
                        } else {
                            data
                        },
                        key = { it.hashCode() },
                    ) { song ->
                        SongFullWidthItems(collectionStyle = true,
                            songEntity = song.second,
                            isPlaying = controller.isPlaying && song.second.videoId == nowPlayingVideoId,
                            modifier = Modifier.fillMaxWidth(),
                            onMoreClickListener = {
                                chosenSong = song.second
                                showBottomSheet = true
                            },
                            onClickListener = { videoId ->
                                val targetList = data.map { it.second }
                                val playTrack = song.second
                                with(sharedViewModel) {
                                    setQueueData(
                                        QueueData.Data(
                                            listTracks = targetList.toArrayListTrack(),
                                            firstPlayedTrack = playTrack.toTrack(),
                                            playlistId = null,
                                            playlistName = getStringBlocking(Res.string.your_top_tracks),
                                            playlistType = PlaylistType.RADIO,
                                            continuation = null,
                                        ),
                                    )
                                    loadMediaItem(
                                        playTrack.toTrack(),
                                        Config.PLAYLIST_CLICK,
                                        targetList.indexOf(playTrack).coerceAtLeast(0),
                                    )
                                }
                            },
                            onAddToQueue = {
                                sharedViewModel.addListToQueue(
                                    arrayListOf(song.second.toTrack()),
                                )
                            },
                            rightView = {
                                Column(
                                    modifier = Modifier.wrapContentWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${song.first.totalListeningTime} ${stringResource(Res.string.seconds)}",
                                        style = typo().bodySmall,
                                    )
                                    Text(
                                        text = "${song.first.playCount} ${stringResource(Res.string.lower_plays)}",
                                        style = typo().bodySmall,
                                    )
                                }
                            },
                        )
                    }
                }

                else -> {}
            }
        } else {
            items(
                when (type) {
                    LibraryDynamicPlaylistType.Downloaded -> {
                        if (query.isNotEmpty() && showSearchBar) {
                            tempDownloaded
                        } else {
                            downloaded
                        }
                    }

                    LibraryDynamicPlaylistType.Favorite -> {
                        if (query.isNotEmpty() && showSearchBar) {
                            tempFavorite
                        } else {
                            favorite
                        }
                    }

                    LibraryDynamicPlaylistType.MostPlayed -> {
                        if (query.isNotEmpty() && showSearchBar) {
                            tempMostPlayed
                        } else {
                            mostPlayed
                        }
                    }
                },
                key = { it.hashCode() },
            ) { song ->
                SongFullWidthItems(collectionStyle = true,
                    songEntity = song,
                    isPlaying = controller.isPlaying && song.videoId == nowPlayingVideoId,
                    modifier = Modifier.fillMaxWidth(),
                    onMoreClickListener = {
                        chosenSong = song
                        showBottomSheet = true
                    },
                    onClickListener = { videoId ->
                        viewModel.playSong(videoId, type = type)
                    },
                    onAddToQueue = {
                        sharedViewModel.addListToQueue(
                            arrayListOf(song.toTrack()),
                        )
                    },
                )
            }
        }
        item {
            EndOfPage()
        }
    }
    if (showBottomSheet) {
        NowPlayingBottomSheet(
            onDismiss = {
                showBottomSheet = false
                chosenSong = null
            },
            navController = navController,
            song = chosenSong ?: return,
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = !isSongCollection || collapsed || showSearchBar) {
            CollectionDetailTopBar(title, onBack = { navController.navigateUp() }, actions = {
                CollectionDetailAction(if (showSearchBar) Icons.Rounded.Close else Icons.Rounded.Search,
                    stringResource(if (showSearchBar) Res.string.detail_close_search else Res.string.search), onClick = { showSearchBar = !showSearchBar })
            })
        }
        androidx.compose.animation.AnimatedVisibility(visible = showSearchBar) {
            SearchBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .padding(horizontal = 12.dp),
                inputField = {
                    CompositionLocalProvider(LocalTextStyle provides typo().bodySmall) {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { showSearchBar = false },
                            expanded = showSearchBar,
                            onExpandedChange = { showSearchBar = it },
                            placeholder = {
                                Text(
                                    stringResource(Res.string.search),
                                    style = typo().bodySmall,
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        )
                    }
                },
                expanded = false,
                onExpandedChange = {},
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
            }
        }
    }
}

sealed class LibraryDynamicPlaylistType {
    data object Favorite : LibraryDynamicPlaylistType()

    data object Followed : LibraryDynamicPlaylistType()

    data object MostPlayed : LibraryDynamicPlaylistType()

    data object Downloaded : LibraryDynamicPlaylistType()

    data object TopTracks : LibraryDynamicPlaylistType()

    data object TopArtists : LibraryDynamicPlaylistType()

    data object TopAlbums : LibraryDynamicPlaylistType()

    fun name(): StringResource =
        when (this) {
            Favorite -> Res.string.favorite
            Followed -> Res.string.followed
            MostPlayed -> Res.string.most_played
            Downloaded -> Res.string.downloaded
            TopAlbums -> Res.string.your_top_albums
            TopArtists -> Res.string.your_top_artists
            TopTracks -> Res.string.your_top_tracks
        }

    // For serialization and navigation
    fun toStringParams(): String =
        when (this) {
            Favorite -> "favorite"
            Followed -> "followed"
            MostPlayed -> "most_played"
            Downloaded -> "downloaded"
            TopAlbums -> "top_albums"
            TopArtists -> "top_artists"
            TopTracks -> "top_tracks"
        }

    companion object {
        fun toType(input: String): LibraryDynamicPlaylistType =
            when (input) {
                "favorite" -> Favorite
                "followed" -> Followed
                "most_played" -> MostPlayed
                "downloaded" -> Downloaded
                "top_albums" -> TopAlbums
                "top_artists" -> TopArtists
                "top_tracks" -> TopTracks
                else -> throw IllegalArgumentException("Unknown type: $this")
            }
    }
}
