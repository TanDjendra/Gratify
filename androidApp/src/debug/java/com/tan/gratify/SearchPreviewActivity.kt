@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tan.gratify.ui.component.ScreenHeader
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.tan.gratify.ui.screen.search.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.flow.first

/** Debug-only sample queries exercising production controls, without network or account writes. */
class SearchPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val failed = intent.getBooleanExtra("error", false)
        val empty = intent.getBooleanExtra("empty", false)
        val cards = intent.getBooleanExtra("cards", false)
        val browse = intent.getBooleanExtra("browse", false)
        val landing = intent.getBooleanExtra("landing", false)
        if (landing) enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val initialType = SearchType.entries.firstOrNull { it.name == intent.getStringExtra("filter") } ?: SearchType.ALL
        setContent { AppTheme {
            if (intent.getBooleanExtra("real_artwork", false)) {
                YouTubeArtworkPreview()
                return@AppTheme
            }
            if (landing) {
                SearchLandingPreview()
                return@AppTheme
            }
            var query by remember { mutableStateOf("") }
            var type by remember { mutableStateOf(initialType) }
            var submitted by remember { mutableStateOf(failed) }
            var error by remember { mutableStateOf(failed) }
            var history by remember { mutableStateOf(if (empty) emptyList() else listOf("Musik untuk perjalanan pulang", "Nara", "Indie Indonesia")) }
            fun submit(value: String) { query = value; submitted = true }
            Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
                Text("Search · debug preview", Modifier.padding(16.dp))
                SearchControls(query, type, { query = it; submitted = false }, { submit(query) }, { type = it; submitted = false })
                Box(Modifier.weight(1f)) {
                    if (cards) SearchCardsPreview(browse)
                    else if (submitted || type == SearchType.USERS) SearchResultStatus(
                        if (error) SearchScreenUIState.Error else if (query.isBlank()) SearchScreenUIState.Empty else SearchScreenUIState.Success,
                        query, type, onRetry = { error = false }, onAll = { type = SearchType.ALL })
                    else SearchQueryList(if (query.isBlank()) history else listOf(query, "$query acoustic"), history = query.isBlank(),
                        onSubmit = { submit(it) }, onFill = { query = it; submitted = false }, onRemove = { history = history - it }, onClear = { history = emptyList() })
                }
            }
        } }
    }
}

/** Read-only live metadata preview: artwork and category names both originate in YouTube Music. */
@Composable
private fun YouTubeArtworkPreview() {
    val home = remember { org.koin.core.context.GlobalContext.get().get<com.tan.domain.repository.HomeRepository>() }
    val search = remember { org.koin.core.context.GlobalContext.get().get<com.tan.domain.repository.SearchRepository>() }
    val scope = rememberCoroutineScope()
    val loader = remember { BrowseArtworkLoader(scope, home, search) }
    val covers by loader.covers.collectAsState()
    var mood by remember { mutableStateOf<com.tan.domain.data.model.mood.Mood?>(null) }
    var failed by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val result = kotlinx.coroutines.withTimeout(20_000) { home.getMoodAndMomentsData().first() }
            mood = (result as? com.tan.domain.utils.Resource.Success)?.data
            failed = mood == null
            problem = (result as? com.tan.domain.utils.Resource.Error)?.message.orEmpty()
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { failed = true; problem = "Metadata timeout" }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { failed = true; problem = e.message.orEmpty() }
    }
    val categories = searchBrowseCategories(mood).take(12)
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).safeDrawingPadding()) {
        Text("Cari", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            val rows = categories.chunked(2)
            items(rows.size) { row ->
                LaunchedEffect(rows[row].map { it.id }) { rows[row].forEach { loader.category(it.id) } }
                SearchCategoryRow(rows[row].map { it.copy(imageUrl = covers[it.id]) }, 2) {}
            }
        }
        Text(if (failed) "YouTube Music metadata unavailable: $problem" else "YouTube Music covers: ${covers.size}", Modifier.padding(16.dp))
    }
}

/** Local categories exercise the production tile, header, input and source switching. */
@Composable
private fun SearchLandingPreview() {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SearchType.ALL) }
    var focused by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var opened by remember { mutableIntStateOf(0) }
    var profile by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    androidx.activity.compose.BackHandler(focused) { focusManager.clearFocus(); focused = false }
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).navigationBarsPadding().imePadding()) {
        ScreenHeader(stringResource(Res.string.search), "Tan", null, { profile++ },
            titleStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold))
        SearchScrollingControls(query.isNotEmpty() || focused || list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset <= 16) {
        SearchControls(query, type, { query = it; submitted = false }, { submitted = true; focusManager.clearFocus() },
            { type = it }, requester, { focused = it }, landing = query.isEmpty() && !focused)
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val columns = searchBrowseColumns(maxWidth - 32.dp, LocalDensity.current.fontScale)
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                if (query.isEmpty() && !focused) {
                    item { Text(stringResource(Res.string.search_public_title), Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)) }
                    item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(SearchCategoryEntry("public:sore", "Playlist sore", subtitle = "Ari & Nara"),
                            SearchCategoryEntry("public:indie", "Indie Indonesia", subtitle = "Tan")).chunked(columns).forEach { row ->
                            SearchCategoryRow(row, columns) { category = it.id; opened++ }
                        }
                    } }
                    item {
                        val shortcuts = listOf(SearchCategoryEntry("music", stringResource(Res.string.search_browse_music)),
                            SearchCategoryEntry("podcast", stringResource(Res.string.podcasts)))
                        Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                            shortcuts.chunked(columns).forEach { row -> SearchCategoryRow(row, columns, compact = true) {
                                type = if (it.id == "podcast") SearchType.PODCASTS else SearchType.SONGS
                                requester.requestFocus()
                            } }
                        }
                    }
                    item { Text(stringResource(Res.string.search_explore_all), Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
                    val rows = listOf("Pop", "Indie", "Musik Indonesia", "K-pop", "Jazz", "Rock", "Hip-Hop", "Fokus & belajar",
                        "Santai sore", "Musik untuk perjalanan pulang yang tenang").map { SearchCategoryEntry(it, it) }.chunked(columns)
                    items(rows.size) { row -> SearchCategoryRow(rows[row], columns) { category = it.id; opened++ } }
                } else if (submitted) {
                    item { SearchResultCard(SearchCardInfo("nara", "Nara", SearchCardKind.SONG, "Ari & Nara", "4:12"), onClick = { opened++ }) }
                } else {
                    item { Text(stringResource(Res.string.search_recent_title), Modifier.padding(vertical = 16.dp)) }
                    item { Text("Nara", Modifier.padding(vertical = 16.dp)) }
                }
                item { Text("Selected: ${type.name} · Opened: $opened · Profile: $profile · Category: $category · Submitted: $submitted",
                    Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Real production cards, with local sample data and counters for native touch verification. */
@Composable
private fun SearchCardsPreview(browse: Boolean) {
    var opened by remember { mutableIntStateOf(0) }
    var queued by remember { mutableIntStateOf(0) }
    var more by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Opened: $opened · Queued: $queued · More: $more", Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (browse) {
                item {
                    BoxWithConstraints {
                        val columns = searchBrowseColumns(maxWidth, LocalDensity.current.fontScale)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Musik untuk perjalanan pulang yang tenang", "Indie Indonesia", "Jazz", "Fokus & belajar").chunked(columns).forEachIndexed { row, titles ->
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    titles.forEachIndexed { index, title -> SearchBrowseCard(title,
                                        Brush.linearGradient(listOf(GratifyColors.BrowseArtwork[(row * columns + index) % 6], GratifyColors.Background)),
                                        modifier = Modifier.weight(1f).fillMaxHeight(), subtitle = if (row == 0 && index == 0) "Ari & Nara" else "Jelajahi genre", onClick = { opened++ }) }
                                    repeat(columns - titles.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            } else {
                item { SearchResultCard(SearchCardInfo("nara", "Nara", SearchCardKind.SONG, "Ari & Nara", "4:12", explicit = true),
                    isCurrent = true, onClick = { opened++ }, onQueue = { queued++ }, onMore = { more++ }) }
                item { SearchResultCard(SearchCardInfo("video", "Musik untuk perjalanan pulang yang tenang", SearchCardKind.VIDEO, "Nara", "5:24"),
                    onClick = { opened++ }, onQueue = { queued++ }, onMore = { more++ }) }
                item { SearchResultCard(SearchCardInfo("album", "Ruang untuk bernapas", SearchCardKind.ALBUM, "Nara", "2026"), onClick = { opened++ }) }
                item { SearchResultCard(SearchCardInfo("artist", "Nara", SearchCardKind.ARTIST), onClick = { opened++ }) }
                item { SearchResultCard(SearchCardInfo("playlist", "Musik buat sore hari", SearchCardKind.PLAYLIST, "Ari & Nara"), onClick = { opened++ }) }
                item { SearchResultCard(SearchCardInfo("podcast", "Cerita sebelum tidur", SearchCardKind.PODCAST), onClick = { opened++ }) }
                item { SearchPersonCard("user", "Ari dengan nama profil yang panjang", null) { opened++ } }
            }
        }
    }
}
