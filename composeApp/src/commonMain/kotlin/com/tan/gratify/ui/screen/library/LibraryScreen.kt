package com.tan.gratify.ui.screen.library

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*


import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.rounded.MoreHoriz



import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.StringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.domain.utils.LocalResource
import com.tan.gratify.extension.copy
import com.tan.gratify.extension.isScrollingUp
import com.tan.gratify.ui.navigation.destination.home.RecentlySongsDestination

import com.tan.gratify.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.theme.typo
import com.tan.domain.repository.YouTubeConnectionStatus
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.navigation.destination.list.YouTubePlaylistDestination
import com.tan.gratify.viewModel.LibraryViewModel
import com.tan.gratify.viewModel.suggestedPlaylistName
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.tan.gratify.viewModel.SharedViewModel
import gratify.composeapp.generated.resources.*
import com.tan.gratify.extension.angledGradientBackground

import org.jetbrains.compose.resources.DrawableResource
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.ui.navigation.openMainTab
import com.tan.gratify.ui.navigation.openPlaylistBuilder

enum class LibrarySortOption(val label: StringResource) {
    TERAKHIR(Res.string.library_sort_default),
    BARU_DITAMBAHKAN(Res.string.library_sort_added),
    ABJAD(Res.string.library_sort_alphabetical)
}

internal fun sortLibraryItems(items: List<LibraryItemModel>, option: LibrarySortOption): List<LibraryItemModel> {
    val sorted = when (option) {
        LibrarySortOption.TERAKHIR -> items
        LibrarySortOption.BARU_DITAMBAHKAN -> items.sortedByDescending { it.addedAt }
        LibrarySortOption.ABJAD -> items.sortedBy { it.title.lowercase() }
    }
    return sorted.sortedByDescending { it.isPinned }
}

data class LibraryItemModel(
    val key: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val placeholderRes: DrawableResource,
    val gradientColors: List<Color>,
    val titleColor: Color = GratifyColors.TextPrimary,
    val isCircle: Boolean = false,
    val onClick: () -> Unit,
    val type: String,
    val addedAt: Long = 0L,
    val playlistId: Long? = null,
    val isPinned: Boolean = false,
    val canLongPress: Boolean = false,
    val artworkKey: String? = null,
    val folderIcon: Boolean = false,
    val automaticTracks: List<String> = emptyList(),
    val automaticImages: List<String>? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    innerPadding: PaddingValues,
    viewModel: LibraryViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val density = LocalDensity.current

    val connection by viewModel.officialYouTube.connection.collectAsStateWithLifecycle()
    val youtubePage by viewModel.youtubeCollections.state.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()
    val yourLocalPlaylist by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    
    val dataStoreManager: DataStoreManager = koinInject()

    var topAppBarHeight by remember { mutableStateOf(0.dp) }

    val savedFilter by remember { dataStoreManager.getString("library_filter") }
        .collectAsStateWithLifecycle(initialValue = null)
    val savedLayout by remember { dataStoreManager.getString("library_layout") }
        .collectAsStateWithLifecycle(initialValue = null)
    val savedSort by remember { dataStoreManager.getString("library_sort") }
        .collectAsStateWithLifecycle(initialValue = null)
    val selectedChip = savedFilter?.takeIf { it == "Playlist" || it == "Artis" }
    val isGridView = savedLayout == "grid"
    val sortOption = LibrarySortOption.entries.firstOrNull { it.name == savedSort } ?: LibrarySortOption.TERAKHIR
    val pinnedPreference by remember { dataStoreManager.getString("library_pinned_playlists") }
        .collectAsStateWithLifecycle(initialValue = null)
    val pinnedIds = remember(pinnedPreference) { pinnedPreference.orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet() }
    val preferencesScope = rememberCoroutineScope()
    fun savePreference(key: String, value: String) {
        preferencesScope.launch { dataStoreManager.putString(key, value) }
    }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateMenu by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val workspaceModel: com.tan.gratify.viewModel.PlaylistWorkspaceViewModel? =
        if(com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled) koinViewModel() else null
    val workspaceState by (workspaceModel?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.tan.gratify.viewModel.PlaylistWorkspaceState()) }).collectAsStateWithLifecycle()
    if(workspaceModel!=null) androidx.lifecycle.compose.LifecycleStartEffect(workspaceModel) {
        workspaceModel.visible(); onStopOrDispose { workspaceModel.hidden() }
    }
    if(showCreateMenu) PlaylistCreateMenu({showCreateMenu=false}) { kind ->
        showCreateMenu=false
        preferencesScope.launch {
            val title=workspaceModel?.nextName(kind) ?: suggestedPlaylistName("personal",(yourLocalPlaylist as? LocalResource.Success)?.data.orEmpty().map{it.title})
            if(!com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled || kind=="personal"&&!workspaceState.accountReady) navController.openPlaylistBuilder(title)
            else navController.navigate(com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination(kind,title=title))
        }
    }
    var showPlaylistOptionSheet by remember { mutableStateOf(false) }
    var selectedPlaylistForOption by remember { mutableStateOf<LibraryItemModel?>(null) }

    LaunchedEffect(Unit) {
        viewModel.getLocalPlaylist()
        viewModel.getFollowedArtists()
    }

    val state = rememberLazyGridState()
    val reselect by sharedViewModel.reloadDestination.collectAsStateWithLifecycle()
    LaunchedEffect(reselect) {
        if (reselect == com.tan.gratify.ui.navigation.destination.library.LibraryDestination::class) {
            state.animateScrollToItem(0)
            sharedViewModel.reloadDestinationDone()
        }
    }
    val isScrollingUp by state.isScrollingUp()
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect {
                if (it.first <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }

    val coroutineScope = rememberCoroutineScope()
    if (showSortSheet) {
        val showSortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = showSortSheetState,
            containerColor = GratifyColors.SurfaceRaised,
            contentColor = Color.White,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.width(40.dp).height(4.dp),
                    colors = CardDefaults.cardColors().copy(containerColor = GratifyColors.SurfaceHighest),
                    shape = RoundedCornerShape(50),
                ) {}
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.library_sort_heading),
                    style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                HorizontalDivider(color = GratifyColors.SurfaceHighest)
                
                LibrarySortOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                savePreference("library_sort", option.name)
                                coroutineScope.launch { showSortSheetState.hide(); showSortSheet = false }
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(option.label),
                            style = typo().bodyLarge,
                            color = if (sortOption == option) com.tan.gratify.ui.theme.GratifyColors.Accent else Color.White
                        )
                        if (sortOption == option) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = com.tan.gratify.ui.theme.GratifyColors.Accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Playlist long-press option bottom sheet
    if (showPlaylistOptionSheet && selectedPlaylistForOption != null) {
        val optionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionSheet = false; selectedPlaylistForOption = null },
            sheetState = optionSheetState,
            containerColor = GratifyColors.SurfaceRaised,
            contentColor = Color.White,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.width(40.dp).height(4.dp),
                    colors = CardDefaults.cardColors().copy(containerColor = GratifyColors.SurfaceHighest),
                    shape = RoundedCornerShape(50),
                ) {}
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = selectedPlaylistForOption!!.title,
                    style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                HorizontalDivider(color = GratifyColors.SurfaceHighest, modifier = Modifier.padding(top = 12.dp))

                // Hapus Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val id = selectedPlaylistForOption!!.playlistId
                            if (id != null) {
                                viewModel.deleteLocalPlaylist(id)
                            }
                            coroutineScope.launch { optionSheetState.hide() }
                            showPlaylistOptionSheet = false
                            selectedPlaylistForOption = null
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.baseline_delete_24),
                        contentDescription = null,
                        tint = GratifyColors.Error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(Res.string.delete_playlist),
                        style = typo().bodyLarge,
                        color = GratifyColors.Error
                    )
                }

                // Sematkan Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val id = selectedPlaylistForOption!!.playlistId
                            if (id != null) {
                                val updated = if (id in pinnedIds) pinnedIds - id else pinnedIds + id
                                savePreference("library_pinned_playlists", updated.sorted().joinToString(","))
                            }
                            coroutineScope.launch { optionSheetState.hide() }
                            showPlaylistOptionSheet = false
                            selectedPlaylistForOption = null
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(if (selectedPlaylistForOption!!.isPinned) Res.string.library_unpin else Res.string.library_pin),
                        style = typo().bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }

    val songCountLabel = stringResource(Res.string.library_song_count)
    val artistLabel = stringResource(Res.string.artists)
    val playlistLabel = stringResource(Res.string.playlists)
    val youtubeLikedLabel = stringResource(Res.string.library_youtube_liked)
    val allItems = remember(selectedChip, yourLocalPlaylist, followedArtists, youtubePage.items, connection, pinnedIds,
        songCountLabel, artistLabel, playlistLabel, youtubeLikedLabel) {
        buildList {
            if (selectedChip != "Artis" && yourLocalPlaylist is LocalResource.Success) {
                yourLocalPlaylist.data.orEmpty().forEach { playlist ->
                    add(LibraryItemModel(key = "local-${playlist.id}", title = playlist.title,
                        subtitle = "${playlist.tracks?.size ?: 0} $songCountLabel",
                        imageUrl = playlist.thumbnail.takeUnless{com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail(it,playlist.youtubePlaylistId)},
                        automaticTracks=if(com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail(playlist.thumbnail,playlist.youtubePlaylistId))playlist.tracks.orEmpty().take(4) else emptyList(),
                        placeholderRes = Res.drawable.round_library_music_24,
                        gradientColors = listOf(GratifyColors.SurfaceRaised, GratifyColors.SurfaceHighest),
                        onClick = { navController.navigate(LocalPlaylistDestination(id = playlist.id)) }, type = "Playlist",
                        addedAt = playlist.inLibrary.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
                        playlistId = playlist.id, isPinned = playlist.id in pinnedIds, canLongPress = true))
                }
            }
            if (selectedChip != "Playlist" && followedArtists is LocalResource.Success) {
                followedArtists.data.orEmpty().forEach { artist ->
                    add(LibraryItemModel(key = "artist-${artist.channelId}", title = artist.name, subtitle = artistLabel,
                        imageUrl = artist.thumbnails, placeholderRes = Res.drawable.holder, isCircle = true,
                        gradientColors = listOf(GratifyColors.SurfaceRaised, GratifyColors.SurfaceHighest),
                        onClick = { navController.navigate(com.tan.gratify.ui.navigation.destination.list.ArtistDestination(channelId = artist.channelId)) },
                        type = "Artis", addedAt = (artist.followedAt ?: artist.inLibrary)
                            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()))
                }
            }
            if (selectedChip != "Artis" && connection.status == YouTubeConnectionStatus.CONNECTED) {
                val owner = connection.ownerId.orEmpty()
                (listOf(com.tan.domain.repository.YouTubeLibraryItem("liked", youtubeLikedLabel, "YouTube")) + youtubePage.items)
                    .distinctBy { it.id }.forEach { playlist ->
                        add(LibraryItemModel(key = "youtube-$owner-${playlist.id}", title = playlist.title,
                            subtitle = "$playlistLabel · YouTube", imageUrl = playlist.thumbnail,
                            placeholderRes = Res.drawable.baseline_queue_music_24,
                            gradientColors = listOf(GratifyColors.SurfaceRaised, GratifyColors.SurfaceHighest),
                            onClick = { navController.navigate(YouTubePlaylistDestination(owner, playlist.id, playlist.title, playlist.thumbnail)) },
                            type = "Playlist", addedAt = Long.MIN_VALUE))
                    }
            }
        }
    }
    val sortedItems = remember(allItems, sortOption, workspaceState.library, workspaceState.localPlaylists, selectedChip) {
        if(!com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled) return@remember sortLibraryItems(allItems,sortOption)
        val moved=workspaceState.library.placements.map{it.ref}.toSet()
        val rootItems=allItems.filter { item ->
            val local=workspaceState.localPlaylists.find{it.id==item.playlistId}
            local?.folderReference() !in moved
        }
        val extra=if(selectedChip=="Artis") emptyList() else buildList {
            workspaceState.library.folders.filter{it.parentId==null}.forEach { folder -> add(LibraryItemModel("folder-${folder.id}",folder.title,
                "Folder • ${workspaceState.library.placements.count{it.folderId==folder.id}} playlist • ${workspaceState.library.folders.count{it.parentId==folder.id}} subfolder",placeholderRes=Res.drawable.round_library_music_24,folderIcon=true,
                gradientColors=listOf(GratifyColors.SurfaceRaised,GratifyColors.SurfaceHighest),
                onClick={navController.navigate(com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination("folder_detail",folder.id))},type="Playlist")) }
            workspaceState.library.playlists.filter{p->"workspace:${p.id}" !in moved && workspaceState.localPlaylists.none{it.canonicalPlaylistId==p.id}}.forEach { p -> add(LibraryItemModel("workspace-${p.id}",p.title,
                if(p.kind=="blend") "Blend" else "Playlist",imageUrl=p.coverUrl,artworkKey=p.coverPath?.let{"workspace-cover:${workspaceState.owner}:$it"},placeholderRes=Res.drawable.round_library_music_24,
                automaticImages=if(p.coverPath==null)p.items.take(4).map{it.thumbnail?:"https://i.ytimg.com/vi/${it.videoId}/mqdefault.jpg"} else null,
                gradientColors=listOf(GratifyColors.SurfaceRaised,GratifyColors.SurfaceHighest),
                onClick={navController.navigate(com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination("detail",p.id))},type="Playlist")) }
        }
        sortLibraryItems(rootItems+extra,sortOption)
    }
    val loading = (selectedChip != "Artis" && yourLocalPlaylist is LocalResource.Loading) ||
        (selectedChip != "Playlist" && followedArtists is LocalResource.Loading)
    val hasError = (selectedChip != "Artis" && yourLocalPlaylist is LocalResource.Error) ||
        (selectedChip != "Playlist" && followedArtists is LocalResource.Error)
    LibraryCollectionContent(items = sortedItems, selectedFilter = selectedChip, isGridView = isGridView, sortOption = sortOption,
        onFilter = { savePreference("library_filter", it ?: "all") },
        onToggleLayout = { savePreference("library_layout", if (isGridView) "list" else "grid") },
        onSort = { showSortSheet = true }, onCreatePlaylist = { showCreateMenu = true },
        onAddArtist = { navController.navigate(com.tan.gratify.ui.navigation.destination.library.ArtistSelectionDestination) },
        onLiked = { navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Favorite.toStringParams())) },
        onDownloaded = { navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Downloaded.toStringParams())) },
        onRecentlyAdded = { navController.navigate(RecentlySongsDestination) },
        onOptions = { selectedPlaylistForOption = it; showPlaylistOptionSheet = true },
        loading = loading, hasError = hasError, onRetry = { viewModel.getLocalPlaylist(); viewModel.getFollowedArtists() },
        contentPadding = innerPadding.copy(top = topAppBarHeight), state = state,
        footer = {
            if(workspaceModel!=null)WorkspaceFeedback(workspaceState,{workspaceModel.refresh()})
            if (selectedChip != "Artis") Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryYouTubeConnection(connection.status, { navController.navigate(LoginDestination) }, viewModel.youtubeCollections::refreshConnection)
                if (youtubePage.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (youtubePage.error != null) Text(stringResource(Res.string.library_youtube_error), color = GratifyColors.TextSecondary)
                if (youtubePage.error != null || youtubePage.nextPageToken != null)
                    TextButton(onClick = viewModel.youtubeCollections::loadMore, enabled = !youtubePage.loading) {
                        Text(stringResource(if (youtubePage.error != null) Res.string.retry else Res.string.library_youtube_more))
                    }
            }
        })

    // Keep the header fixed while browsing the collection.
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background).onGloballyPositioned { coordinates ->
                topAppBarHeight = with(density) { coordinates.size.height.toDp() }
            },
    ) {
        val headerImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")
        val headerName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
        com.tan.gratify.ui.component.ScreenHeader(
            title = stringResource(Res.string.library), profileName = headerName,
            profileImage = headerImage, onOpenProfile = onOpenDrawer,
            actions = {
                IconButton(onClick = { navController.openMainTab(SearchDestination) }) {
                    Icon(Icons.Rounded.Search, stringResource(Res.string.search), tint = GratifyColors.TextPrimary)
                }
                IconButton(onClick = { showCreateMenu = true }) {
                    Icon(Icons.Rounded.Add, stringResource(Res.string.library_create_playlist), tint = GratifyColors.TextPrimary)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListItem(
    title: String,
    subtitle: String,
    placeholderRes: DrawableResource,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    isCircle: Boolean = false,
    titleColor: Color = GratifyColors.TextPrimary,
    imageUrl: String? = null,
    isGridView: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    isPinned: Boolean = false,
    artworkKey: String? = null,
    folderIcon: Boolean = false,
    automaticTracks: List<String> = emptyList(),
    automaticImages: List<String>? = null,
) {
    val optionsLabel = stringResource(Res.string.library_item_options, title)
    val itemModifier = Modifier.fillMaxWidth().clip(GratifyShapes.small)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button,
            onLongClickLabel = if (onLongClick != null) optionsLabel else null)
    val artwork: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.clip(if (isCircle) CircleShape else GratifyShapes.small)
            .angledGradientBackground(gradientColors, 45f), contentAlignment = Alignment.Center) {
            // Keep a fallback visible while loading, for empty URLs, and after an image failure.
            if (folderIcon) Icon(Icons.Rounded.Folder, null, Modifier.size(if (isGridView) 48.dp else 28.dp), tint = GratifyColors.TextSecondary)
            else if (isCircle) Icon(Icons.Rounded.Person, null, Modifier.size(if (isGridView) 48.dp else 28.dp), tint = GratifyColors.TextSecondary)
            else Icon(painterResource(placeholderRes), null, Modifier.size(if (isGridView) 48.dp else 28.dp), tint = GratifyColors.TextSecondary)
            if (!imageUrl.isNullOrBlank()) AsyncImage(model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current).data(imageUrl)
                .memoryCacheKey(artworkKey?:imageUrl).diskCacheKey(artworkKey?:imageUrl).build(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if(automaticImages!=null)com.tan.gratify.ui.component.PlaylistAutomaticArtwork(automaticImages,Modifier.fillMaxSize(),title)
            else if(automaticTracks.isNotEmpty())com.tan.gratify.ui.component.PlaylistCollageThumbnail(automaticTracks,Modifier.fillMaxSize(),title)
        }
    }
    val caption: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) {
                    Icon(Icons.Default.PushPin, stringResource(Res.string.library_pinned), Modifier.size(12.dp), tint = GratifyColors.Accent)
                    Spacer(Modifier.width(4.dp))
                }
                Text(title, style = if (isGridView) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, color = titleColor, maxLines = if (isGridView) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = GratifyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
    if (isGridView) {
        Column(itemModifier.padding(bottom = 4.dp)) {
            artwork(Modifier.fillMaxWidth().aspectRatio(1f))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                caption()
                if (onLongClick != null) IconButton(onClick = onLongClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, optionsLabel, Modifier.size(20.dp), tint = GratifyColors.TextSecondary)
                }
            }
        }
    } else {
        Row(itemModifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            artwork(Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            caption()
            if (onLongClick != null) IconButton(onClick = onLongClick) {
                Icon(Icons.Rounded.MoreHoriz, optionsLabel, Modifier.size(22.dp), tint = GratifyColors.TextSecondary)
            }
        }
    }
}
