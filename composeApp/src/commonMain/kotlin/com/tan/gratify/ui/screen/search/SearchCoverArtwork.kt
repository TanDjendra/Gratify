package com.tan.gratify.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.theme.GratifyColors
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.layout.size

/** Text classification only controls the category background color, never its photo. */
enum class SearchCoverTheme(val tile: Int) {
    POP(0), ROCK(1), HIP_HOP(2), JAZZ(3), CLASSICAL(4), ELECTRONIC(5), RNB(6), INDIE(7),
    CHILL(8), SLEEP(9), FOCUS(10), WORKOUT(11), LOVE(12), TRAVEL(13), PODCAST(14), WORLD(15), PERSONAL(-1),
}

private val coverWords = Regex("[\\p{L}\\p{N}]+")

fun searchCoverTheme(title: String): SearchCoverTheme {
    val words = coverWords.findAll(title.lowercase()).map { it.value }.toSet()
    fun has(vararg terms: String) = terms.any { it in words }
    return when {
        has("jazz", "blues", "saxophone") -> SearchCoverTheme.JAZZ
        has("rock", "metal", "punk", "grunge") -> SearchCoverTheme.ROCK
        has("hip", "hiphop", "rap", "trap") -> SearchCoverTheme.HIP_HOP
        has("classical", "klasik", "orchestra", "piano", "instrumental") -> SearchCoverTheme.CLASSICAL
        has("electronic", "electronics", "electronica", "elektronik", "edm", "dance", "dj", "techno", "house") -> SearchCoverTheme.ELECTRONIC
        has("rnb", "soul", "rhythm") || (has("r") && has("b")) -> SearchCoverTheme.RNB
        has("indie", "alternative", "alternatif", "nostalgia", "retro", "90s", "80s") -> SearchCoverTheme.INDIE
        has("podcast", "podcasts", "cerita", "talk", "komedi", "comedy") -> SearchCoverTheme.PODCAST
        has("sleep", "tidur", "lullaby", "malam", "night") -> SearchCoverTheme.SLEEP
        has("focus", "fokus", "study", "belajar", "kerja", "work", "concentration") -> SearchCoverTheme.FOCUS
        has("workout", "gym", "olahraga", "lari", "running", "energize", "energi", "fitness") -> SearchCoverTheme.WORKOUT
        has("love", "cinta", "romance", "romantis", "heartbreak", "galau", "sad", "sedih") -> SearchCoverTheme.LOVE
        has("travel", "perjalanan", "pulang", "road", "trip", "commute", "driving", "jalan") -> SearchCoverTheme.TRAVEL
        has("chill", "relax", "santai", "tenang", "calm", "coffee", "kopi", "acoustic", "akustik", "sore", "morning", "pagi") -> SearchCoverTheme.CHILL
        has("indonesia", "indonesian", "dangdut", "world", "latin", "reggae", "folk", "global", "tropical") -> SearchCoverTheme.WORLD
        has("pop", "kpop", "k", "hits", "charts", "trending", "party", "pesta", "music", "musik", "songs", "lagu") -> SearchCoverTheme.POP
        else -> SearchCoverTheme.PERSONAL
    }
}

@Composable
internal fun SearchCoverArtwork(title: String, modifier: Modifier = Modifier, imageUrl: String? = null) {
    val color = GratifyColors.BrowseArtwork[(title.hashCode() and Int.MAX_VALUE) % GratifyColors.BrowseArtwork.size]
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(color, GratifyColors.Navigation))), contentAlignment = Alignment.Center) {
        if (!loaded) {
            val logoSize = (minOf(maxWidth, maxHeight) * .55f).coerceIn(16.dp, 48.dp)
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(logoSize), tint = GratifyColors.TextSecondary)
        }
        if (!imageUrl.isNullOrBlank()) AsyncImage(imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onLoading = { loaded = false }, onSuccess = { loaded = true }, onError = { loaded = false })
    }
}
