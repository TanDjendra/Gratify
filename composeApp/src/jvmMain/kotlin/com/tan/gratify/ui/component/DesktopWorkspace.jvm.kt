package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.utils.LocalResource
import com.tan.domain.utils.connectArtists
import com.tan.domain.utils.toListName
import com.tan.gratify.expect.toggleMiniPlayer
import com.tan.gratify.ui.navigation.destination.home.*
import com.tan.gratify.ui.navigation.openMainTab
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.navigation.destination.library.*
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.player.FullscreenDestination
import com.tan.gratify.ui.screen.library.LibraryDynamicPlaylistType
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.*
import gratify.composeapp.generated.resources.*
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun DesktopWorkspace(
    navController: NavHostController,
    sharedViewModel: SharedViewModel,
    profileName: String?,
    profileImage: String?,
    chromeVisible: Boolean,
    content: @Composable () -> Unit,
) {
    val library: LibraryViewModel = koinViewModel()
    val handler: MediaPlayerHandler = koinInject()
    LaunchedEffect(library) { library.getLocalPlaylist() }
    val playlists by library.yourLocalPlaylist.collectAsStateWithLifecycle()
    val controls by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val song by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val details by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val queue by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val selectedPlaylistId = if (destination?.hasRoute<LocalPlaylistDestination>() == true) entry?.toRoute<LocalPlaylistDestination>()?.id else null
    val activeStack by navController.currentBackStack.collectAsState()
    val selected = (activeStack.asReversed().firstNotNullOfOrNull {
        com.tan.gratify.ui.navigation.MainTab.forDestination(it.destination)
    } ?: com.tan.gratify.ui.navigation.MainTab.HOME).name.lowercase()
    var panel by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedLibrary by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    var songOptions by remember { mutableStateOf(false) }
    var queueOptions by remember { mutableStateOf(false) }
    var songInfo by remember { mutableStateOf(false) }
    // Open once when playback starts; closing the panel remains respected across tracks.
    val hasSong = song?.isNotEmpty() == true
    LaunchedEffect(hasSong) { if (hasSong) panel = 0 }
    fun openTab(route: Any) {
        navController.openMainTab(route, onRootReselect = sharedViewModel::reloadDestination)
    }
    if (createPlaylist) {
        var title by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { createPlaylist = false },
            title = { Text(stringResource(Res.string.desktop_create_playlist)) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, singleLine = true,
                label = { Text(stringResource(Res.string.playlist_name)) }) },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
                library.createPlaylist(title.trim()); createPlaylist = false
            }) { Text(stringResource(Res.string.desktop_create_playlist)) } },
            dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text(stringResource(Res.string.cancel)) } })
    }
    val track = song?.songEntity
    if (songOptions && track != null) NowPlayingBottomSheet(
        onDismiss = { songOptions = false }, navController = navController, song = track,
        setSleepTimerEnable = true, changeMainLyricsProviderEnable = true,
    )
    if (queueOptions) QueueBottomSheet(onDismiss = { queueOptions = false }, sharedViewModel = sharedViewModel)
    if (songInfo) InfoPlayerBottomSheet(onDismiss = { songInfo = false }, sharedViewModel = sharedViewModel)
    val player = details.copy(
        nowPlayingTitle = track?.title ?: details.nowPlayingTitle,
        artistName = track?.artistName?.connectArtists() ?: details.artistName,
        thumbnailURL = track?.thumbnails ?: details.thumbnailURL,
    )
    DesktopWorkspaceSurface(
        profileName = profileName, profileImage = profileImage, selected = selected,
        playlists = playlists.data.orEmpty(), loadingLibrary = playlists is LocalResource.Loading,
        libraryFailed = playlists is LocalResource.Error, expandedLibrary = expandedLibrary,
        onExpandLibrary = { expandedLibrary = it }, onCreatePlaylist = { createPlaylist = true },
        onPlaylist = { navController.navigate(LocalPlaylistDestination(it.id)) },
        onHome = { openTab(HomeDestination) }, onSearch = { openTab(SearchDestination) },
        onLibrary = { openTab(LibraryDestination) }, onFriends = { openTab(FriendsDestination) },
        onFavorite = { navController.navigate(LibraryDynamicPlaylistDestination(LibraryDynamicPlaylistType.Favorite.toStringParams())) },
        onDownloads = { navController.navigate(LibraryDynamicPlaylistDestination(LibraryDynamicPlaylistType.Downloaded.toStringParams())) },
        onProfile = { navController.navigate(ProfileDestination) }, onSettings = { navController.navigate(SettingsDestination) },
        onRecent = { navController.navigate(RecentlySongsDestination) }, onNotifications = { navController.navigate(NotificationDestination) },
        onBack = { navController.popBackStack() }, canGoBack = navController.previousBackStackEntry != null,
        player = player, controls = controls, hasSong = hasSong, timeline = sharedViewModel.timeline,
        onPlayerEvent = sharedViewModel::onUIEvent, panel = panel, onPanel = { panel = it },
        queue = queue?.data?.listTracks.orEmpty(), currentVideoId = track?.videoId,
        onQueueTrack = handler::playMediaItemInMediaSource,
        onVideo = { navController.navigate(FullscreenDestination) },
        onMiniPlayer = { toggleMiniPlayer() },
        onArtist = { song?.track?.artists?.firstOrNull()?.id?.let { navController.navigate(ArtistDestination(it)) } },
        onAlbum = { song?.track?.album?.id?.let { navController.navigate(AlbumDestination(it)) } },
        hasArtist = song?.track?.artists?.firstOrNull()?.id != null,
        albumName = song?.track?.album?.name,
        onSongOptions = { if (panel == 1) queueOptions = true else songOptions = true },
        onSongInfo = { songInfo = true },
        selectedPlaylistId = selectedPlaylistId,
        chromeVisible = chromeVisible, content = content,
    )
}

/** Production desktop layout. Stateless data makes resizing and controls independently renderable. */
@Composable
fun DesktopWorkspaceSurface(
    profileName: String?, profileImage: String?, selected: String,
    playlists: List<LocalPlaylistEntity>, loadingLibrary: Boolean = false, libraryFailed: Boolean = false,
    expandedLibrary: Boolean?, onExpandLibrary: (Boolean) -> Unit, onCreatePlaylist: () -> Unit,
    onPlaylist: (LocalPlaylistEntity) -> Unit, onHome: () -> Unit, onSearch: () -> Unit, onLibrary: () -> Unit,
    onFriends: () -> Unit, onFavorite: () -> Unit, onDownloads: () -> Unit,
    onProfile: () -> Unit, onSettings: () -> Unit, onRecent: () -> Unit, onNotifications: () -> Unit,
    onBack: () -> Unit, canGoBack: Boolean,
    player: NowPlayingScreenData, controls: ControlState, hasSong: Boolean, timeline: StateFlow<TimeLine>,
    onPlayerEvent: (UIEvent) -> Unit, panel: Int?, onPanel: (Int?) -> Unit,
    queue: List<Track>, currentVideoId: String?, onQueueTrack: (Int) -> Unit,
    onVideo: () -> Unit, onMiniPlayer: () -> Unit,
    onArtist: () -> Unit = {}, onAlbum: () -> Unit = {}, hasArtist: Boolean = false, albumName: String? = null,
    onSongOptions: () -> Unit = {}, onSongInfo: () -> Unit = {},
    selectedPlaylistId: Long? = null,
    chromeVisible: Boolean = true, content: @Composable () -> Unit,
) {
    if (!chromeVisible) { content(); return }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        when {
            event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.K -> { onSearch(); true }
            event.type == KeyEventType.KeyDown && event.isAltPressed && event.key == Key.DirectionLeft && canGoBack -> { onBack(); true }
            else -> false
        }
    }) {
        val workspaceWidth = maxWidth
        val expanded = expandedLibrary ?: (workspaceWidth >= 1160.dp)
        val libraryWidth = if (expanded) 248.dp else 72.dp
        val panelWidth = if (maxWidth >= 1400.dp) 320.dp else 288.dp
        Column(Modifier.fillMaxSize()) {
            DesktopToolbar(profileName, profileImage, selected, onHome, onSearch, onFriends,
                onProfile, onSettings, onRecent, onNotifications, onBack, canGoBack)
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopLibrary(Modifier.width(libraryWidth).fillMaxHeight(), expanded, playlists, loadingLibrary, libraryFailed,
                    { if (!expanded && workspaceWidth < 1160.dp) onPanel(null); onExpandLibrary(!expanded) },
                    onCreatePlaylist, onPlaylist, onLibrary, onFavorite, onDownloads, selectedPlaylistId)
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(GratifyColors.Background)) { content() }
                if (panel != null && hasSong) {
                    DesktopSongPanel(Modifier.width(panelWidth).fillMaxHeight().clip(RoundedCornerShape(8.dp)),
                        player, controls, timeline, panel, onPanel, onPlayerEvent, queue, currentVideoId, onQueueTrack,
                        onVideo, onArtist, hasArtist, onAlbum, albumName, onSongOptions, onSongInfo)
                }
            }
            DesktopPlayerBar(player, controls, hasSong, timeline, onPlayerEvent, panel, onPanel, onMiniPlayer)
        }
    }
}

@Composable
private fun DesktopToolbar(
    name: String?, image: String?, selected: String,
    onHome: () -> Unit, onSearch: () -> Unit, onFriends: () -> Unit,
    onProfile: () -> Unit, onSettings: () -> Unit, onRecent: () -> Unit, onNotifications: () -> Unit,
    onBack: () -> Unit, canGoBack: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.circle_app_icon), null, Modifier.size(32.dp), tint = Color.Unspecified)
        Spacer(Modifier.width(20.dp))
        DesktopIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back), enabled = canGoBack, onClick = onBack)
        Spacer(Modifier.weight(1f))
        DesktopIcon(Icons.Rounded.Home, stringResource(Res.string.home), selected = selected == "home", onClick = onHome)
        Spacer(Modifier.width(8.dp))
        Surface(onClick = onSearch, shape = CircleShape, color = if (selected == "search") GratifyColors.SurfaceHighest else GratifyColors.SurfaceRaised,
            modifier = Modifier.widthIn(max = 400.dp).weight(1.5f).height(46.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, stringResource(Res.string.search), tint = GratifyColors.TextSecondary)
                Text(stringResource(Res.string.desktop_search_prompt), Modifier.padding(start = 12.dp),
                    color = GratifyColors.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(1f))
        DesktopIcon(Icons.Rounded.PeopleOutline, stringResource(Res.string.friends), selected = selected == "friends", onClick = onFriends)
        DesktopIcon(Icons.Rounded.NotificationsNone, stringResource(Res.string.notification), onClick = onNotifications)
        Box {
            val profileLabel = stringResource(Res.string.ui_open_profile)
            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = profileLabel }) {
                UserAvatar(imageUrl = image, name = name, modifier = Modifier.size(32.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(name.orEmpty().ifBlank { stringResource(Res.string.ui_open_profile) }) }, onClick = { menu = false; onProfile() })
                DropdownMenuItem(text = { Text(stringResource(Res.string.recently)) }, onClick = { menu = false; onRecent() })
                DropdownMenuItem(text = { Text(stringResource(Res.string.settings)) }, onClick = { menu = false; onSettings() })
            }
        }
    }
}

@Composable
private fun DesktopLibrary(
    modifier: Modifier, expanded: Boolean, playlists: List<LocalPlaylistEntity>, loading: Boolean, failed: Boolean,
    onExpand: () -> Unit, onCreate: () -> Unit, onPlaylist: (LocalPlaylistEntity) -> Unit,
    onLibrary: () -> Unit, onFavorite: () -> Unit, onDownloads: () -> Unit,
    selectedPlaylistId: Long?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val createLabel = stringResource(Res.string.desktop_create_playlist)
    val filtered = remember(playlists, query) { playlists.filter { it.title.contains(query, ignoreCase = true) } }
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(GratifyColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopIcon(Icons.Rounded.LibraryMusic, stringResource(Res.string.library), onClick = onLibrary)
            if (expanded) {
                Text(stringResource(Res.string.library), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                DesktopIcon(Icons.Rounded.ChevronLeft, stringResource(Res.string.desktop_collapse_library), onClick = onExpand)
            }
        }
        if (expanded) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCreate, modifier = Modifier.semantics { contentDescription = createLabel }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(createLabel, fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(Res.string.desktop_library_search), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), shape = RoundedCornerShape(6.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = GratifyColors.SurfaceRaised,
                        unfocusedContainerColor = GratifyColors.Surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            }
        } else {
            DesktopIcon(Icons.Rounded.ChevronRight, stringResource(Res.string.desktop_expand_library), onClick = onExpand)
            DesktopIcon(Icons.Rounded.Add, stringResource(Res.string.desktop_create_playlist), onClick = onCreate)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            item { LibraryEntry(stringResource(Res.string.favorite), stringResource(Res.string.playlist), null, Icons.Rounded.Favorite, expanded, onFavorite, Color(0xFF53409C)) }
            item { LibraryEntry(stringResource(Res.string.downloaded), stringResource(Res.string.playlist), null, Icons.Rounded.Download, expanded, onDownloads, GratifyColors.AccentContainer) }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
            items(filtered, key = { it.id }) { playlist ->
                LibraryEntry(playlist.title, playlist.creatorName?.takeIf(String::isNotBlank) ?: stringResource(Res.string.playlist), playlist.thumbnail,
                    Icons.Rounded.MusicNote, expanded, { onPlaylist(playlist) }, selected = playlist.id == selectedPlaylistId)
            }
            if (expanded && !loading && filtered.isEmpty()) item {
                Text(stringResource(if (failed) Res.string.detail_load_failed else if (query.isNotBlank()) Res.string.detail_no_matches else Res.string.no_playlists_added),
                    Modifier.padding(12.dp), color = GratifyColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LibraryEntry(title: String, subtitle: String, image: String?, icon: ImageVector, expanded: Boolean, onClick: () -> Unit, color: Color = GratifyColors.SurfaceRaised, selected: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (selected) GratifyColors.SurfaceHighest else if (hovered) GratifyColors.SurfaceRaised else Color.Transparent)
        .hoverable(interaction).clickable(onClick = onClick).semantics { contentDescription = title }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(color), contentAlignment = Alignment.Center) {
            if (image.isNullOrBlank()) Icon(icon, title, Modifier.size(22.dp), tint = GratifyColors.TextPrimary)
            else DesktopArtwork(image, title, Modifier.fillMaxSize())
        }
        if (expanded) Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = GratifyColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DesktopSongPanel(
    modifier: Modifier, player: NowPlayingScreenData, controls: ControlState, timeline: StateFlow<TimeLine>, panel: Int,
    onPanel: (Int?) -> Unit, onPlayerEvent: (UIEvent) -> Unit, queue: List<Track>, videoId: String?, onQueueTrack: (Int) -> Unit,
    onVideo: () -> Unit, onArtist: () -> Unit, hasArtist: Boolean, onAlbum: () -> Unit, albumName: String?,
    onSongOptions: () -> Unit, onSongInfo: () -> Unit,
) {
    Column(modifier.background(GratifyColors.Background)) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(player.playlistName.ifBlank { stringResource(Res.string.now_playing) }, Modifier.weight(1f),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            DesktopIcon(Icons.Rounded.Info, stringResource(Res.string.desktop_song_details), onClick = onSongInfo)
            DesktopIcon(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = onSongOptions)
            DesktopIcon(Icons.Rounded.Close, stringResource(Res.string.desktop_close_panel), onClick = { onPanel(null) })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Res.string.now_playing, Res.string.queue, Res.string.lyrics).forEachIndexed { index, label ->
                TextButton(onClick = { onPanel(index) }, colors = ButtonDefaults.textButtonColors(contentColor = if (panel == index) GratifyColors.TextPrimary else GratifyColors.TextSecondary)) {
                    Text(stringResource(label), fontSize = 11.sp, fontWeight = if (panel == index) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        when (panel) {
            1 -> {
                if (queue.isEmpty()) PanelEmpty(stringResource(Res.string.desktop_empty_queue))
                else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(queue, key = { index, track -> "$index:${track.videoId}" }) { index, track ->
                        val active = track.videoId == videoId
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onQueueTrack(index) }.semantics { contentDescription = track.title }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DesktopArtwork(track.thumbnails?.lastOrNull()?.url, track.title, Modifier.size(40.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = if (active) GratifyColors.Accent else GratifyColors.TextPrimary)
                                Text(track.artists?.toListName()?.connectArtists().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = GratifyColors.TextSecondary)
                            }
                            if (active) Icon(Icons.Rounded.GraphicEq, stringResource(Res.string.now_playing), Modifier.size(18.dp), tint = GratifyColors.Accent)
                        }
                    }
                }
            }
            2 -> {
                val lyrics = player.lyricsData
                if (lyrics == null || lyrics.lyrics.lines.isNullOrEmpty()) PanelEmpty(stringResource(Res.string.desktop_no_lyrics))
                else LyricsView(lyrics, timeline, { onPlayerEvent(UIEvent.UpdateProgress(it)) },
                    Modifier.weight(1f).fillMaxWidth().padding(16.dp), backgroundColor = GratifyColors.Background)
            }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { DesktopArtwork(player.thumbnailURL, player.nowPlayingTitle, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(player.nowPlayingTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(player.artistName, Modifier.padding(top = 6.dp).clickable(enabled = hasArtist, onClick = onArtist), fontSize = 14.sp, color = GratifyColors.TextSecondary)
                        }
                        DesktopIcon(if (controls.isLiked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                            stringResource(Res.string.favorite), selected = controls.isLiked, onClick = { onPlayerEvent(UIEvent.ToggleLike) })
                    }
                }
                if (!albumName.isNullOrBlank()) item {
                    Surface(onClick = onAlbum, color = GratifyColors.SurfaceRaised, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(Res.string.album), fontSize = 12.sp, color = GratifyColors.TextSecondary)
                            Text(albumName, Modifier.padding(top = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    Surface(color = GratifyColors.SurfaceRaised, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(Res.string.lyrics), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(player.lyricsData?.lyrics?.lines?.firstOrNull { it.words.isNotBlank() }?.words
                                ?: stringResource(Res.string.desktop_no_lyrics), Modifier.padding(vertical = 12.dp),
                                fontSize = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = GratifyColors.TextSecondary)
                            TextButton(onClick = { onPanel(2) }) { Text(stringResource(Res.string.lyrics)) }
                        }
                    }
                }
                if (player.isVideo) item { OutlinedButton(onClick = onVideo, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.SmartDisplay, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.desktop_video))
                } }
            }
        }
    }
}

@Composable
private fun PanelEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = GratifyColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DesktopPlayerBar(
    player: NowPlayingScreenData, controls: ControlState, hasSong: Boolean, timeline: StateFlow<TimeLine>,
    onEvent: (UIEvent) -> Unit, panel: Int?, onPanel: (Int?) -> Unit, onMiniPlayer: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(96.dp).background(Color.Black)) {
        val wide = maxWidth >= 1160.dp
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(if (wide) 280.dp else 210.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopArtwork(player.thumbnailURL, player.nowPlayingTitle, Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).clickable(enabled = hasSong) { onPanel(if (panel == 0) null else 0) })
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (hasSong) player.nowPlayingTitle else stringResource(Res.string.app_name), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(if (hasSong) player.artistName else stringResource(Res.string.desktop_empty_player), fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = GratifyColors.TextSecondary)
                }
                if (hasSong && wide) DesktopIcon(if (controls.isLiked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                    stringResource(Res.string.favorite), selected = controls.isLiked, onClick = { onEvent(UIEvent.ToggleLike) })
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Box(Modifier.widthIn(max = 300.dp).fillMaxWidth()) {
                        // An empty player has no actionable transport controls.
                        if (hasSong) PlayerControlLayout(controls, isSmallSize = true, onUIEvent = onEvent, desktop = true)
                        else Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = {}, enabled = false) { Icon(Icons.Rounded.PlayCircle, stringResource(Res.string.ui_play), Modifier.size(36.dp)) }
                        }
                    }
                    DesktopSeekBar(timeline, hasSong, onEvent)
                }
            }
            Row(Modifier.width(if (wide) 248.dp else 200.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                DesktopIcon(Icons.Rounded.Mic, stringResource(Res.string.lyrics), selected = panel == 2, enabled = hasSong, onClick = { onPanel(if (panel == 2) null else 2) })
                DesktopIcon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(Res.string.queue), selected = panel == 1, enabled = hasSong, onClick = { onPanel(if (panel == 1) null else 1) })
                var lastVolume by remember { mutableFloatStateOf(1f) }
                LaunchedEffect(controls.volume) { if (controls.volume > 0f) lastVolume = controls.volume }
                DesktopIcon(if (controls.volume > 0f) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, stringResource(Res.string.ui_volume),
                    onClick = { onEvent(UIEvent.UpdateVolume(if (controls.volume > 0f) 0f else lastVolume)) })
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Slider(controls.volume.coerceIn(0f, 1f), { onEvent(UIEvent.UpdateVolume(it)) }, modifier = Modifier.width(if (wide) 72.dp else 52.dp),
                        thumb = { Canvas(Modifier.size(8.dp)) { drawCircle(GratifyColors.TextPrimary) } },
                        track = { state -> SliderDefaults.Track(state, modifier = Modifier.height(4.dp), thumbTrackGapSize = 0.dp,
                            drawStopIndicator = null, colors = SliderDefaults.colors(activeTrackColor = GratifyColors.TextPrimary, inactiveTrackColor = GratifyColors.SurfaceHighest)) },
                        colors = SliderDefaults.colors(thumbColor = GratifyColors.TextPrimary, activeTrackColor = GratifyColors.TextPrimary))
                }
                if (wide) DesktopIcon(Icons.Rounded.PictureInPictureAlt, stringResource(Res.string.open_miniplayer), onClick = onMiniPlayer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopSeekBar(timeline: StateFlow<TimeLine>, hasSong: Boolean, onEvent: (UIEvent) -> Unit) {
    val time by timeline.collectAsStateWithLifecycle()
    var seeking by remember { mutableStateOf<Float?>(null) }
    val progress = if (time.total > 0) (time.current.toDouble() / time.total).toFloat().coerceIn(0f, 1f) else 0f
    val seekLabel = stringResource(Res.string.desktop_seek)
    fun format(ms: Long): String = "${ms.coerceAtLeast(0) / 60_000}:${(ms.coerceAtLeast(0) / 1000 % 60).toString().padStart(2, '0')}"
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(format(if (seeking != null) (seeking!! * time.total).toLong() else time.current), Modifier.width(36.dp), fontSize = 10.sp, color = GratifyColors.TextSecondary)
        Slider(value = seeking ?: progress, onValueChange = { seeking = it }, enabled = hasSong && time.total > 0,
            onValueChangeFinished = { seeking?.let { onEvent(UIEvent.UpdateProgress(it * 100f)) }; seeking = null },
            modifier = Modifier.weight(1f).height(20.dp).semantics { contentDescription = seekLabel }, colors = SliderDefaults.colors(thumbColor = GratifyColors.TextPrimary,
                activeTrackColor = GratifyColors.TextPrimary, inactiveTrackColor = GratifyColors.SurfaceHighest),
            thumb = { Canvas(Modifier.size(8.dp)) { drawCircle(GratifyColors.TextPrimary) } },
            track = { state -> SliderDefaults.Track(state, modifier = Modifier.height(4.dp), enabled = hasSong && time.total > 0,
                thumbTrackGapSize = 0.dp, drawStopIndicator = null,
                colors = SliderDefaults.colors(activeTrackColor = GratifyColors.TextPrimary, inactiveTrackColor = GratifyColors.SurfaceHighest)) })
        Text(format(time.total), Modifier.width(36.dp).padding(start = 6.dp), fontSize = 10.sp, color = GratifyColors.TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopIcon(icon: ImageVector, description: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(description) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = if (selected) GratifyColors.Accent else GratifyColors.TextSecondary,
                disabledContentColor = GratifyColors.Outline, containerColor = if (selected) GratifyColors.SurfaceRaised else Color.Transparent)) {
            Icon(icon, description, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DesktopArtwork(url: String?, title: String, modifier: Modifier) {
    AsyncImage(model = url, contentDescription = title, modifier = modifier,
        contentScale = ContentScale.Crop, placeholder = painterResource(Res.drawable.holder),
        error = painterResource(Res.drawable.holder), fallback = painterResource(Res.drawable.holder))
}
