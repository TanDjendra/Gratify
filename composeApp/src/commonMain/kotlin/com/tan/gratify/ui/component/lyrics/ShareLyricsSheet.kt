package com.tan.gratify.ui.component.lyrics

// Picker and preview flow adapted from SimpMusic 62e0073 (GPL-3.0).
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.tan.gratify.expect.shareLyricsImage
import com.tan.gratify.expect.saveLyricsImage
import com.tan.gratify.expect.LyricsShareTarget
import com.tan.gratify.expect.lyricsShareTargetAvailable
import com.tan.gratify.expect.ui.fileSaverResult
import com.tan.gratify.ui.theme.typo
import gratify.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLyricsSheet(
    lines: List<String>, songTitle: String, artistName: String,
    artwork: ImageBitmap?, seedColor: Color, initialIndex: Int?, onDismiss: () -> Unit,
    onShare: (suspend (ImageBitmap) -> Boolean)? = null,
    onShareTo: (suspend (ImageBitmap, LyricsShareTarget) -> Boolean)? = null,
) {
    // A sheet is keyed to its track by its caller; picks never spill into the next song.
    val selection = remember(lines) { ShareLyricsSelection(initialIndex?.takeIf { it in lines.indices && lines[it].isNotBlank() }) }
    var preview by remember(lines) { mutableStateOf(false) }
    var background by remember(seedColor) { mutableStateOf(seedColor) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var destinationError by remember { mutableStateOf<org.jetbrains.compose.resources.StringResource?>(null) }
    val storySource = LocalLyricsStorySource.current
    val context = LocalPlatformContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    fun shareTo(target: LyricsShareTarget) {
        error = false; destinationError = null; saved = false
        if (onShareTo == null && !lyricsShareTargetAvailable(context, target)) {
            destinationError = if (target == LyricsShareTarget.INSTAGRAM) Res.string.share_lyrics_instagram_missing else Res.string.share_lyrics_whatsapp_missing
            return
        }
        busy = true
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap()
                error = !(onShareTo?.invoke(bitmap, target)
                    ?: if (target == LyricsShareTarget.SYSTEM && onShare != null) onShare.invoke(bitmap)
                    else shareLyricsImage(bitmap, context, target))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    val fileName = remember(songTitle) { "Gratify_lyrics_${java.util.UUID.randomUUID()}.png" }
    val saver = fileSaverResult(fileName, "image/png") { uri ->
        if (uri != null) scope.launch {
            busy = true; error = false; saved = false; destinationError = null
            try { saved = saveLyricsImage(layer.toImageBitmap(), context, uri); error = !saved }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    val content = Color.White
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = com.tan.gratify.ui.component.rememberBusyModalSheetState(busy),
        containerColor = Color(0xFF1B1B1F), contentColor = content) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ if (preview) preview = false else onDismiss() }, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.apple_player_close))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (preview) Res.string.share_lyrics else Res.string.share_lyrics_select), style = typo().titleLarge)
                    Text(stringResource(Res.string.share_lyrics_count, selection.count), style = typo().bodySmall)
                }
            }
            if (preview) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    ShareLyricsCard(selection.range.orEmpty().map { lines[it] }, songTitle, artistName, artwork, background,
                        Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) })
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(seedColor, Color(0xFF28384D), Color(0xFF5B3243), Color(0xFFEEE9DE)).distinct().forEach { color ->
                            Box(Modifier.size(44.dp).clip(CircleShape).background(color)
                                .semantics { selected = color == background }.clickable(enabled = !busy) { background = color })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    destinationError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    if (error) Text(stringResource(Res.string.share_lyrics_failed), color = MaterialTheme.colorScheme.error)
                    if (saved) Text(stringResource(Res.string.share_lyrics_saved))
                    LyricsShareDestinations(enabled = !busy && !selection.isEmpty, onShare = ::shareTo, onStory = {})
                    OutlinedButton({ saver.launch() }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(stringResource(Res.string.share_lyrics_save))
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex?.coerceIn(0, (lines.size - 1).coerceAtLeast(0)) ?: 0),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(lines) { index, line ->
                        if (line.isNotBlank()) Text(line, style = typo().headlineSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (selection.isSelected(index)) seedColor else Color.White.copy(alpha = .05f))
                                .semantics { selected = selection.isSelected(index) }
                                .clickable { limitReached = false; selection.toggle(index) { limitReached = true } }.padding(16.dp))
                    }
                }
                if (limitReached) Text(stringResource(Res.string.share_lyrics_limit), Modifier.padding(horizontal = 24.dp))
                Button({ preview = true }, enabled = !selection.isEmpty && selection.range.orEmpty().all { lines[it].isNotBlank() },
                    modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text(stringResource(Res.string.share_lyrics_preview)) }
            }
        }
    }
}

@Composable
internal fun LyricsShareDestinations(enabled: Boolean, onShare: (LyricsShareTarget) -> Unit, onStory: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.share_lyrics_destination), style = typo().titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onShare(LyricsShareTarget.INSTAGRAM) }, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(22.dp))
                    Text("Instagram", style = typo().labelLarge)
                }
            }
            OutlinedButton({ onShare(LyricsShareTarget.WHATSAPP) }, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(22.dp))
                    Text("WhatsApp", style = typo().labelLarge)
                }
            }
        }
        TextButton({ onShare(LyricsShareTarget.SYSTEM) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.MoreHoriz, null); Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.share_lyrics_other_apps))
        }
    }
}

private fun IntRange?.orEmpty(): List<Int> = this?.toList().orEmpty()
