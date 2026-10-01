package com.tan.gratify

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDestination
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.ui.screen.MiniPlayerSurface
import com.tan.gratify.ui.screen.MainScreen
import com.tan.gratify.ui.screen.home.QuickPicksContent
import com.tan.domain.data.model.home.Content
import com.tan.domain.data.model.home.HomeItem as HomeSection
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.gratify.ui.screen.library.LibraryListItem
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder

/** Visual smoke-test host. This activity is excluded from release builds. */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        val showLiveApp = intent.getBooleanExtra("live", false)
        setContent { AppTheme { if (showLiveApp) MainScreen() else DesignPreview() } }
    }
}

@Composable
private fun DesignPreview() {
    val navController = rememberNavController()
    var isPlaying by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(true) }
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayerSurface(
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                    title = "Ruang untuk mendengarkan", artist = "Gratify · Pratinjau desain",
                    artworkUrl = sampleArtwork(0), isPlaying = isPlaying, isLiked = isLiked,
                    onOpen = {}, onPlayPause = { isPlaying = !isPlaying }, onToggleLike = { isLiked = !isLiked },
                )
                AppBottomNavigationBar(navController = navController)
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = HomeDestination) {
            composable<HomeDestination> { PreviewHome(padding) }
            composable<SearchDestination> { PreviewPage("Cari", padding, "Jelajahi musik", search = true) }
            composable<LibraryDestination> { PreviewPage("Koleksi", padding, "Musikmu", library = true) }
            composable<FriendsDestination> { PreviewPage("Teman", padding, "Aktivitas teman") }
        }
    }
}

/** Uses the production home sections, rather than the library's vertical playlist list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewHome(padding: PaddingValues) {
    val songs = remember {
        listOf("Ruang tenang" to "Senja", "Perjalanan pulang" to "Langit Kota",
            "Hari yang baru" to "Nara", "Pelan-pelan" to "Sore")
            .mapIndexed { index, (title, artist) -> sampleContent(title, artist, "sample-song-$index", artworkIndex = index) }
    }
    val playlists = remember {
        listOf("Santai sejenak", "Indie untukmu", "Teman perjalanan")
            .mapIndexed { index, title -> sampleContent(title, "Gratify", playlistId = "sample-playlist-$index", artworkIndex = index) }
    }
    Column {
        ScreenHeader("Beranda", "Tan", null, onOpenProfile = {})
        var selectedParams by remember { mutableStateOf<String?>(null) }
        HomeMoodFilters(selectedParams, onSelect = { selectedParams = it })
        LazyColumn(
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Box(Modifier.padding(horizontal = 15.dp)) {
                    QuickPicksContent(HomeSection(contents = songs, title = "Pilihan cepat"), onPlay = {})
                }
            }
            item {
                Column(Modifier.padding(horizontal = 15.dp)) {
                    HomeSectionHeading(title = "Dibuat untuk kamu", subtitle = "Temukan suasana yang pas")
                    LazyRow {
                        items(playlists) { HomeItemContentPlaylist(onClick = {}, data = it) }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 15.dp)) {
                    HomeSectionHeading(title = "Dengarkan lagi")
                    LazyRow {
                        items(songs) { HomeItemSong(onClick = {}, onLongClick = {}, data = it) }
                    }
                }
            }
        }
    }
}

private fun sampleArtwork(index: Int): String {
    val resourceId = listOf(R.drawable.home_sample_green, R.drawable.home_sample_coral, R.drawable.home_sample_blue)[index % 3]
    return "android.resource://com.tan.gratify.dev/$resourceId"
}

private fun sampleContent(title: String, artist: String, videoId: String? = null, playlistId: String? = null, artworkIndex: Int = 0) = Content(
    album = null, artists = listOf(Artist(id = null, name = artist)), description = "Playlist · $artist",
    isExplicit = false, playlistId = playlistId, browseId = null,
    thumbnails = listOf(Thumbnail(height = 160, width = 160, url = sampleArtwork(artworkIndex))),
    title = title, videoId = videoId, views = null,
)

@Composable
private fun PreviewPage(title: String, padding: PaddingValues, section: String, search: Boolean = false, library: Boolean = false) {
    Column {
        ScreenHeader(title, "Tan", null, onOpenProfile = {}, actions = {
            IconButton(onClick = {}) { Icon(if (library) Icons.Rounded.Add else Icons.Rounded.Search, "Cari") }
        })
        LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
            if (search) item {
                OutlinedTextField(
                    value = "", onValueChange = {}, placeholder = { Text("Apa yang ingin kamu dengarkan?") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.small,
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(isSelected = true, text = if (library) "Playlist" else "Semua", onClick = {})
                    Chip(text = "Musik", onClick = {})
                    Chip(text = "Artis", onClick = {})
                }
            }
            item {
                Text(section, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            }
            items(6) { index ->
                val titles = listOf("Lagu yang Disukai", "Putar lagi", "Santai sejenak", "Teman perjalanan", "Fokus hari ini", "Penemuan baru")
                val colors = listOf(Color(0xFF5D3CB4), Color(0xFF22716C), Color(0xFF9F3F38), Color(0xFF3B607F), Color(0xFF88622E), GratifyColors.AccentContainer)
                LibraryListItem(
                    title = titles[index], subtitle = "Playlist · Koleksi musikmu", placeholderRes = Res.drawable.holder,
                    gradientColors = listOf(colors[index], colors[index].copy(alpha = 0.25f)), onClick = {},
                )
            }
        }
    }
}
