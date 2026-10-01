package com.tan.gratify.ui.screen.library

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.domain.utils.LocalResource
import com.tan.gratify.extension.copy
import com.tan.gratify.extension.isScrollingUp
import com.tan.gratify.ui.component.EndOfPage
import com.tan.gratify.ui.navigation.destination.home.RecentlySongsDestination
import com.tan.gratify.ui.navigation.destination.library.AddSongsToPlaylistDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.LibraryViewModel
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.tan.gratify.viewModel.SharedViewModel
import gratify.composeapp.generated.resources.*
import com.tan.gratify.extension.angledGradientBackground

import org.jetbrains.compose.resources.DrawableResource
import com.tan.domain.manager.DataStoreManager

enum class LibrarySortOption(val title: String) {
    TERAKHIR("Terakhir"),
    BARU_DITAMBAHKAN("Baru ditambahkan"),
    ABJAD("Abjad")
}

data class LibraryItemModel(
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val placeholderRes: DrawableResource,
    val gradientColors: List<Color>,
    val titleColor: Color = Color.White,
    val isCircle: Boolean = false,
    val onClick: () -> Unit,
    val type: String,
    val addedAt: Long = 0L,
    val playlistId: Long? = null,
    val isPinned: Boolean = false,
    val canLongPress: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
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

    val loggedIn by viewModel.youtubeLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val downloadedPlaylist by viewModel.downloadedPlaylist.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()
    val yourLocalPlaylist by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    val youTubePlaylist by viewModel.youTubePlaylist.collectAsStateWithLifecycle()
    val accountThumbnail by viewModel.accountThumbnail.collectAsStateWithLifecycle()
    
    val dataStoreManager: DataStoreManager = koinInject()
    val appProfileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "Tan.")
    val libraryOwner = appProfileName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.you)

    var topAppBarHeight by remember { mutableStateOf(0.dp) }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedChip by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(LibrarySortOption.TERAKHIR) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionSheet by remember { mutableStateOf(false) }
    var selectedPlaylistForOption by remember { mutableStateOf<LibraryItemModel?>(null) }

    LaunchedEffect(Unit) {
        viewModel.getRecentlyAdded()
        viewModel.getLocalPlaylist()
        viewModel.getPlaylistFavorite()
        viewModel.getDownloadedPlaylist()
        viewModel.getChartPlaylists()
        viewModel.getFavoritePodcasts()
        viewModel.getFollowedArtists()
        if (viewModel.getYouTubeLoggedIn()) {
            viewModel.getYouTubePlaylist()
            viewModel.getYouTubeMixedForYou()
        }
    }

    val state = rememberLazyGridState()
    val isScrollingUp by state.isScrollingUp()
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }

    // Modal Bottom Sheet logic for creating playlist
    val coroutineScope = rememberCoroutineScope()
    if (showAddSheet) {
        var newTitle by remember { mutableStateOf("") }
        val showAddSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val hideEditTitleBottomSheet: () -> Unit = {
            coroutineScope.launch {
                showAddSheetState.hide()
                showAddSheet = false
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = showAddSheetState,
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                colors = CardDefaults.cardColors().copy(containerColor = GratifyColors.SurfaceRaised),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Card(
                        modifier = Modifier.width(60.dp).height(4.dp),
                        colors = CardDefaults.cardColors().copy(containerColor = GratifyColors.SurfaceHighest),
                        shape = RoundedCornerShape(50),
                    ) {}
                    Spacer(modifier = Modifier.height(5.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { s -> newTitle = s },
                        label = { Text(text = stringResource(Res.string.playlist_name)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    TextButton(
                        onClick = {
                            if (newTitle.isBlank()) {
                                viewModel.makeToast(runBlocking { getString(Res.string.playlist_name_cannot_be_empty) })
                            } else {
                                hideEditTitleBottomSheet()
                                navController.navigate(AddSongsToPlaylistDestination(playlistTitle = newTitle))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(Res.string.create))
                    }
                }
            }
        }
    }

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
                    text = "Urutkan menurut",
                    style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                HorizontalDivider(color = GratifyColors.SurfaceHighest)
                
                LibrarySortOption.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortOption = option
                                coroutineScope.launch { showSortSheetState.hide(); showSortSheet = false }
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.title,
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
                        text = "Hapus playlist",
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
                                viewModel.togglePinPlaylist(id)
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
                        text = if (selectedPlaylistForOption!!.isPinned) "Lepas sematan" else "Sematkan playlist",
                        style = typo().bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }

    val allItems = remember(
        selectedChip,
        yourLocalPlaylist,
        followedArtists,
        youTubePlaylist,
        libraryOwner
    ) {
        val list = mutableListOf<LibraryItemModel>()
        
        if (selectedChip == null || selectedChip == "Playlist") {
            list.add(LibraryItemModel(
                title = "Lagu yang Disukai",
                subtitle = "Playlist • $libraryOwner",
                placeholderRes = Res.drawable.baseline_favorite_24,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6)),
                titleColor = GratifyColors.Accent,
                onClick = { navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Favorite.toStringParams())) },
                type = "Playlist",
                addedAt = 1000L
            ))
        }

        if (selectedChip == null || selectedChip == "Artis") {
            list.add(LibraryItemModel(
                title = "Tambahkan artis",
                subtitle = "",
                placeholderRes = Res.drawable.baseline_add_24,
                gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                isCircle = true,
                onClick = { navController.navigate(com.tan.gratify.ui.navigation.destination.library.ArtistSelectionDestination) },
                type = "Artis",
                addedAt = 900L
            ))
        }

        if (selectedChip == null || selectedChip == "Playlist") {
            list.add(LibraryItemModel(
                title = "Buat playlist",
                subtitle = "",
                placeholderRes = Res.drawable.baseline_add_24,
                gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                onClick = { showAddSheet = true },
                type = "Playlist",
                addedAt = 800L
            ))
            
            list.add(LibraryItemModel(
                title = "Musik Di Unduh",
                subtitle = "Playlist • $libraryOwner",
                placeholderRes = Res.drawable.baseline_downloaded,
                gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                onClick = { navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Downloaded.toStringParams())) },
                type = "Playlist",
                addedAt = 700L
            ))
            
            list.add(LibraryItemModel(
                title = "Riwayat Putar",
                subtitle = "Playlist • $libraryOwner",
                placeholderRes = Res.drawable.baseline_history_24,
                gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                onClick = { navController.navigate(RecentlySongsDestination) },
                type = "Playlist",
                addedAt = 600L
            ))
        }

        if (selectedChip == null || selectedChip == "Playlist") {
            if (yourLocalPlaylist is LocalResource.Success && yourLocalPlaylist.data != null) {
                yourLocalPlaylist.data!!.forEachIndexed { index, playlist ->
                    list.add(LibraryItemModel(
                        title = playlist.title,
                        subtitle = "Playlist • ${playlist.tracks?.size ?: 0} lagu",
                        placeholderRes = Res.drawable.round_library_music_24,
                        gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                        onClick = { navController.navigate(LocalPlaylistDestination(id = playlist.id)) },
                        type = "Playlist",
                        addedAt = playlist.id,
                        imageUrl = playlist.thumbnail,
                        playlistId = playlist.id,
                        canLongPress = true
                    ))
                }
            }
        }

        if (selectedChip == null || selectedChip == "Artis") {
            if (followedArtists is LocalResource.Success && followedArtists.data != null) {
                followedArtists.data!!.forEachIndexed { index, artist ->
                    list.add(LibraryItemModel(
                        title = artist.name ?: "Artist",
                        subtitle = "Artis",
                        imageUrl = artist.thumbnails,
                        placeholderRes = Res.drawable.holder,
                        gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                        isCircle = true,
                        onClick = { navController.navigate(com.tan.gratify.ui.navigation.destination.list.ArtistDestination(channelId = artist.channelId)) },
                        type = "Artis",
                        addedAt = 500L - index
                    ))
                }
            }
        }

        if (selectedChip == null || selectedChip == "Playlist") {
            if (youTubePlaylist is LocalResource.Success && youTubePlaylist.data != null) {
                youTubePlaylist.data!!.forEachIndexed { index, playlist ->
                    list.add(LibraryItemModel(
                        title = playlist.title ?: "Playlist",
                        subtitle = "Playlist • YouTube",
                        placeholderRes = Res.drawable.baseline_queue_music_24,
                        gradientColors = listOf(GratifyColors.Divider, GratifyColors.Divider),
                        onClick = { navController.navigate(PlaylistDestination(playlistId = playlist.browseId.removePrefix("VL"), isYourYouTubePlaylist = true)) },
                        type = "Playlist",
                        addedAt = 400L - index,
                        imageUrl = playlist.thumbnails.lastOrNull()?.url
                    ))
                }
            }
        }

        list
    }

    val sortedItems = remember(allItems, sortOption) {
        when (sortOption) {
            LibrarySortOption.TERAKHIR -> allItems 
            LibrarySortOption.BARU_DITAMBAHKAN -> allItems.sortedByDescending { it.addedAt }
            LibrarySortOption.ABJAD -> allItems.sortedBy { it.title.lowercase() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (isGridView) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
            contentPadding = innerPadding.copy(top = topAppBarHeight),
            state = state,
            modifier = Modifier.fillMaxSize()
        ) {
            // Chips row
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.tan.gratify.ui.component.Chip(isSelected = selectedChip == null, text = stringResource(Res.string.all)) {
                        selectedChip = null
                    }
                    com.tan.gratify.ui.component.Chip(isSelected = selectedChip == "Playlist", text = stringResource(Res.string.playlists)) {
                        selectedChip = if (selectedChip == "Playlist") null else "Playlist"
                    }
                    com.tan.gratify.ui.component.Chip(isSelected = selectedChip == "Artis", text = stringResource(Res.string.artists)) {
                        selectedChip = if (selectedChip == "Artis") null else "Artis"
                    }
                }
            }

            // Sort row
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showSortSheet = true }) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(sortOption.title, color = Color.White, style = typo().labelLarge)
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = if (isGridView) "List view" else "Grid view",
                            tint = GratifyColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            items(sortedItems, key = { "${it.type}-${it.playlistId}-${it.title}-${it.imageUrl}" }) { item ->
                LibraryListItem(
                    title = item.title,
                    subtitle = item.subtitle,
                    placeholderRes = item.placeholderRes,
                    gradientColors = item.gradientColors,
                    onClick = item.onClick,
                    isCircle = item.isCircle,
                    titleColor = item.titleColor,
                    imageUrl = item.imageUrl,
                    isGridView = isGridView,
                    onLongClick = if (item.canLongPress) {
                        { selectedPlaylistForOption = item; showPlaylistOptionSheet = true }
                    } else null
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                EndOfPage()
            }
        }
    }

    // Header (Top App Bar) with Haze
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
                IconButton(onClick = { navController.navigate(SearchDestination) }) {
                    Icon(Icons.Rounded.Search, "Search", tint = Color.White)
                }
                IconButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Rounded.Add, "Add Playlist", tint = Color.White)
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
    titleColor: Color = Color.White,
    imageUrl: String? = null,
    isGridView: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    if (isGridView) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Square
                    .clip(if (isCircle) CircleShape else GratifyShapes.extraSmall)
                    .angledGradientBackground(gradientColors, 45f)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(placeholderRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp) // Larger icon for grid
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = typo().bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(if (isCircle) CircleShape else GratifyShapes.extraSmall)
                    .angledGradientBackground(gradientColors, 45f)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(placeholderRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typo().bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = typo().bodyMedium,
                        color = GratifyColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
