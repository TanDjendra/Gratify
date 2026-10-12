package com.tan.gratify.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.social.*
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.gratify.expect.ClipPlaybackState
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal fun clipTime(ms: Long) = "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2,'0')}"

internal fun completeStoryDraft(draft: MusicStoryDraft, track: StoryTrack): MusicStoryDraft {
    require(track.durationMs >= 8000)
    val seconds = listOf(30, 15, 8).first { it <= draft.clipSeconds && it * 1000L <= track.durationMs }
    val next = draft.copy(track = track, clipSeconds = seconds)
    return next.copy(startMs = next.startMs.coerceIn(0, next.maxStartMs))
}

@Composable
fun MusicStoryEditor(initialTrack: StoryTrack?, tracks: List<StoryTrack>, searching: Boolean,
    busy: Boolean, error: String?, onSearch: (String) -> Unit,
    onClose: () -> Unit, onPreview: (MusicStoryDraft) -> Unit, onPublish: (MusicStoryDraft) -> Unit,
    completeTrack: suspend (StoryTrack) -> StoryTrack = { it },
    loadLyrics: (StoryTrack) -> Flow<Lyrics> = { emptyFlow() }, initialDraft: MusicStoryDraft? = null,
    onDraftChange: (MusicStoryDraft?) -> Unit = {}) {
    val seed = initialDraft ?: initialTrack?.let { MusicStoryDraft(it, clipSeconds = if(it.durationMs < 15000) 8 else 15) }
    val draftState = rememberSaveable(stateSaver = Saver<MusicStoryDraft?, String>(
        save = { it?.let { value -> Json.encodeToString(value) } },
        restore = { runCatching { Json.decodeFromString<MusicStoryDraft>(it) }.getOrNull() })) { mutableStateOf(seed) }
    var draft by draftState
    fun updateDraft(next: MusicStoryDraft?) { draft = next; onDraftChange(next) }
    LaunchedEffect(Unit) { onDraftChange(draft) }
    var choosing by rememberSaveable { mutableStateOf(seed == null) }
    var query by rememberSaveable { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var trackError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (seed != null && seed.track.durationMs <= 0) {
            resolving = true
            try {
                val resolved = completeTrack(seed.track)
                require(resolved.durationMs >= 8000)
                updateDraft(completeStoryDraft(seed, resolved))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { trackError = true; choosing = true }
            finally { resolving = false }
        }
    }
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.Rounded.Close, "Tutup editor story") }
            Text("Story musik", Modifier.weight(1f), style = typo().titleLarge, color = GratifyColors.TextPrimary)
            TextButton(onClick = { draft?.let(onPublish) }, enabled = draft?.isValid == true && !busy && !choosing && !resolving) { Text(if(busy) "Menyimpan…" else "Bagikan") }
        }
        if (choosing) {
            if (draft != null) TextButton(onClick = { choosing = false }) { Text("Kembali ke lagu pilihan") }
            OutlinedTextField(query, { query = it; onSearch(it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true, placeholder = { Text("Cari lagu di Gratify") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            if (searching || resolving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (trackError) Text("Durasi lagu belum dapat dimuat. Coba lagu lain.", Modifier.padding(16.dp), color = GratifyColors.Error)
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.weight(1f)) {
                items(tracks, key = StoryTrack::videoId) { track ->
                    Surface(onClick = { if (!resolving) scope.launch {
                        resolving = true; trackError = false
                        try { val resolved = completeTrack(track); updateDraft(MusicStoryDraft(resolved, clipSeconds = if(resolved.durationMs < 15000) 8 else 15)); choosing = false }
                        catch(e: CancellationException) { throw e }
                        catch(_: Exception) { trackError = true }
                        finally { resolving = false }
                    } }, enabled = !resolving, color = GratifyColors.Background) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StoryCover(track.thumbnailUrl, Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, style = typo().titleSmall, color = GratifyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = typo().bodySmall, color = GratifyColors.TextSecondary)
                            }
                            Text(clipTime(track.durationMs), style = typo().labelSmall, color = GratifyColors.TextSecondary)
                        }
                    }
                }
                if (!searching && query.length >= 2 && tracks.isEmpty()) item { InboxNotice("Lagu belum ditemukan", "Coba judul atau nama artis yang lain.") }
                if(query.length < 2) item { InboxNotice("Pilih musikmu", "Cari lagu, lalu pilih bagian yang ingin dibagikan.") }
            }
        } else draft?.let { current ->
            var lyricsRetry by remember(current.track.videoId) { mutableIntStateOf(0) }
            val lyrics = rememberStoryLyrics(current.track, loadLyrics, lyricsRetry)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                StoryCover(current.track.thumbnailUrl, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(current.track.title, style = typo().titleLarge, color = GratifyColors.TextPrimary)
                        Text(current.track.artist, style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                    }
                    TextButton(onClick = { choosing = true }, enabled = !busy && !resolving) { Text("Ganti") }
                }
                if (resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Durasi potongan", style = typo().labelLarge, color = GratifyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8,15,30).forEach { seconds ->
                        FilterChip(selected = current.clipSeconds == seconds, enabled = current.track.durationMs >= seconds * 1000L && !busy,
                            onClick = { val next = current.copy(clipSeconds = seconds); updateDraft(next.copy(startMs = next.startMs.coerceAtMost(next.maxStartMs))) },
                            label = { Text("$seconds detik") })
                    }
                }
                Text("${clipTime(current.startMs)} — ${clipTime(current.startMs + current.clipSeconds * 1000L)}", style = typo().titleMedium, color = GratifyColors.TextPrimary)
                Slider(value = current.startMs.toFloat(), onValueChange = { updateDraft(current.copy(startMs = (it.toLong() / 100) * 100)) },
                    valueRange = 0f..current.maxStartMs.toFloat().coerceAtLeast(1f), enabled = !busy && current.maxStartMs > 0,
                    modifier = Modifier.semantics { contentDescription = "Titik mulai potongan musik" })
                OutlinedButton(onClick = { onPreview(current) }, enabled = current.isValid && !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Pratinjau musik & lirik")
                }
                StoryLyricsPreview(lyrics.lyrics, lyrics.loading, current.startMs, current.clipSeconds, onRetry = { lyricsRetry++ })
                if (current.track.durationMs < current.clipSeconds * 1000L) Text("Durasi lagu belum tersedia atau terlalu pendek untuk potongan ini. Pilih lagu lain.", style = typo().bodySmall, color = GratifyColors.Error)
                OutlinedTextField(current.caption, { if(it.length <= 200) updateDraft(current.copy(caption = it)) }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Tambahkan catatan…") }, maxLines = 4, enabled = !busy, supportingText = { Text("${current.caption.length}/200") })
                Text("Siapa yang bisa melihat?", style = typo().labelLarge, color = GratifyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(current.audience == "friends", { updateDraft(current.copy(audience = "friends")) }, enabled = !busy, label = { Text("Teman") }, leadingIcon = { Icon(Icons.Rounded.PeopleOutline, null, Modifier.size(18.dp)) })
                    FilterChip(current.audience == "public", { updateDraft(current.copy(audience = "public")) }, enabled = !busy, label = { Text("Publik") }, leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(18.dp)) })
                }
                Text("Story tampil selama 24 jam.", style = typo().bodySmall, color = GratifyColors.TextSecondary)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (error != null) Text(error, Modifier.padding(16.dp), color = GratifyColors.Error, style = typo().bodySmall)
    }
}

@Composable
internal fun StoryCover(url: String?, modifier: Modifier) {
    Box(modifier.clip(GratifyShapes.large).background(GratifyColors.SurfaceRaised), contentAlignment = Alignment.Center) {
        if(url != null) AsyncImage(url, "Sampul lagu", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(.3f), tint = GratifyColors.TextSecondary)
    }
}

@Composable
fun MusicStoryViewer(story: MusicStory, index: Int, count: Int, playback: ClipPlaybackState,
    playing: Boolean, own: Boolean, busy: Boolean, onToggle: () -> Unit, onRetry: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onClose: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit, canDelete: Boolean = true,
    lyrics: Lyrics? = null, lyricsLoading: Boolean = false, onRetryLyrics: () -> Unit = {}) {
    val elapsed = storyElapsedMs(playback.positionMs, story.clipSeconds, playback.completed)
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i -> LinearProgressIndicator(progress = { if(i < index) 1f else if(i == index) (elapsed.toFloat()/(story.clipSeconds * 1000)).coerceIn(0f,1f) else 0f },
                modifier = Modifier.weight(1f).height(3.dp), color = GratifyColors.Accent, trackColor = GratifyColors.Outline) }
        }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(story.ownerAvatar, story.ownerName, Modifier.padding(start = 8.dp).size(38.dp), borderWidth = 0.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(story.ownerName ?: "Pengguna Gratify", style = typo().titleSmall, color = GratifyColors.TextPrimary)
                Text(if(story.audience == "public") "Publik · ${story.clipSeconds} detik" else "Teman · ${story.clipSeconds} detik", style = typo().labelSmall, color = GratifyColors.TextSecondary)
            }
            if(own && canDelete) IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "Hapus story") }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Tutup story") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            StoryCover(story.thumbnailUrl, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(24.dp))
            Text(story.title, style = typo().headlineSmall.copy(fontWeight = FontWeight.Bold), color = GratifyColors.TextPrimary)
            Text(story.artist, Modifier.padding(top = 8.dp), style = typo().bodyLarge, color = GratifyColors.TextSecondary)
            if(story.caption.isNotBlank()) Text(story.caption, Modifier.padding(top = 24.dp), style = typo().bodyLarge, color = GratifyColors.TextPrimary)
            Text("${clipTime(story.startMs)} — ${clipTime(story.startMs + story.clipSeconds * 1000L)}", Modifier.padding(top = 16.dp), style = typo().labelSmall, color = GratifyColors.TextSecondary)
            Text("${clipTime(elapsed)} / ${clipTime(story.clipSeconds * 1000L)}", Modifier.padding(top = 8.dp).testTag("story-preview-clock"),
                style = typo().titleMedium.copy(fontFamily = FontFamily.Monospace), color = GratifyColors.TextPrimary)
            Spacer(Modifier.height(20.dp))
            StoryLyricsPreview(lyrics, lyricsLoading, story.startMs, story.clipSeconds, elapsed, onRetryLyrics)
            if(playback.error) { Text("Potongan belum dapat diputar.", Modifier.padding(top = 16.dp), color = GratifyColors.Error); TextButton(onClick = onRetry) { Text("Coba lagi") } }
            else if(playback.buffering) CircularProgressIndicator(Modifier.padding(top = 20.dp).size(24.dp), strokeWidth = 2.dp)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = index > 0) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Story sebelumnya") }
            FilledIconButton(onClick = onToggle, enabled = !playback.error) { Icon(if(playing && !playback.completed) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(playing && !playback.completed) "Jeda potongan" else "Putar potongan") }
            IconButton(onClick = onNext, enabled = index < count - 1) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Story berikutnya") }
        }
        if(!own) OutlinedButton(onClick = onReply, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { Text("Kirim pesan") }
    }
}
