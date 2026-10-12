package com.tan.gratify.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.mood.Mood

data class SearchCategoryEntry(val id: String, val title: String, val imageUrl: String? = null, val subtitle: String? = null,
    val automaticImages:List<String> = emptyList())

internal fun searchBrowseCategories(mood: Mood?): List<SearchCategoryEntry> =
    (mood?.genres.orEmpty().map { SearchCategoryEntry(it.params, it.title) } +
        mood?.moodsMoments.orEmpty().map { SearchCategoryEntry(it.params, it.title) })
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }.distinctBy { it.id }

/** Stable title-based colors keep a category recognizable when server ordering changes. */
fun searchCategoryColor(title: String): Color = when (searchCoverTheme(title)) {
    SearchCoverTheme.POP -> if (Regex("\\bk[ -]?pop\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)) Color(0xFF2548BA) else Color(0xFFC90A7B)
    SearchCoverTheme.ROCK -> Color(0xFFB92332)
    SearchCoverTheme.HIP_HOP -> Color(0xFF9B4A22)
    SearchCoverTheme.JAZZ -> Color(0xFF26738C)
    SearchCoverTheme.CLASSICAL -> Color(0xFF65508B)
    SearchCoverTheme.ELECTRONIC -> Color(0xFF6930B5)
    SearchCoverTheme.RNB -> Color(0xFF8C174E)
    SearchCoverTheme.INDIE -> Color(0xFF9F1644)
    SearchCoverTheme.CHILL -> Color(0xFF08715F)
    SearchCoverTheme.SLEEP -> Color(0xFF30497D)
    SearchCoverTheme.FOCUS -> Color(0xFF58642D)
    SearchCoverTheme.WORKOUT -> Color(0xFFAB422A)
    SearchCoverTheme.LOVE -> Color(0xFFAC2552)
    SearchCoverTheme.TRAVEL -> Color(0xFF286C87)
    SearchCoverTheme.PODCAST -> Color(0xFF006654)
    SearchCoverTheme.WORLD -> Color(0xFFB02438)
    SearchCoverTheme.PERSONAL -> listOf(Color(0xFF804D9C), Color(0xFF1B5778), Color(0xFF8A3D4B),
        Color(0xFF58662B), Color(0xFF006B5B), Color(0xFF4B429A))[(title.hashCode().toLong() and 0x7fffffffL).rem(6).toInt()]
}

/** Artwork is clipped within its own corner, so large text never collides with the tilted cover. */
@Composable
fun SearchCategoryTile(entry: SearchCategoryEntry, modifier: Modifier = Modifier, compact: Boolean = false,
    onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(searchCategoryColor(entry.title))
        .clickable(enabled = entry.id.isNotBlank(), role = Role.Button, onClick = onClick)) {
        val artworkWidth = if (compact) 64.dp else 72.dp
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = if (compact) 80.dp else 104.dp)) {
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp),
                    color = Color.White, maxLines = if (entry.subtitle == null) 4 else 3, overflow = TextOverflow.Ellipsis)
                if (!entry.subtitle.isNullOrBlank()) Text(entry.subtitle, style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
                    color = Color.White.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.width(artworkWidth).fillMaxHeight().clipToBounds()) {
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 10.dp).size(artworkWidth + 6.dp)
                    .rotate(20f).clip(RoundedCornerShape(4.dp))) {
                    if(entry.automaticImages.isNotEmpty()&&(entry.imageUrl.isNullOrBlank()||entry.imageUrl.contains("i.ytimg.com")))
                        com.tan.gratify.ui.component.PlaylistAutomaticArtwork(entry.automaticImages,Modifier.fillMaxSize(),entry.title)
                    else SearchCoverArtwork(entry.title, Modifier.fillMaxSize(), entry.imageUrl)
                }
            }
        }
    }
}

@Composable
fun SearchCategoryRow(entries: List<SearchCategoryEntry>, columns: Int, compact: Boolean = false,
    onSelect: (SearchCategoryEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { entry -> SearchCategoryTile(entry, Modifier.weight(1f).fillMaxHeight(), compact) { onSelect(entry) } }
        repeat((columns - entries.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}
