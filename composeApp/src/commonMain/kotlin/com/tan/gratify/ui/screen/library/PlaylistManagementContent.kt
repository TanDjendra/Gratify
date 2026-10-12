package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.validPlaylistName
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun OwnedPlaylistVisibility(isPublic: Boolean, known: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)) {
        Icon(if (isPublic) Icons.Rounded.Public else Icons.Rounded.Lock, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (!known) Res.string.playlist_manage_check_visibility else if (isPublic) Res.string.playlist_manage_public else Res.string.playlist_manage_private),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsSheet(title: String, trackCount: Int, isPublic: Boolean, visibilityKnown: Boolean,
    ytPlaylistId: String?, onDismiss: () -> Unit, onAdd: () -> Unit, onEdit: () -> Unit,
    onVisibility: () -> Unit, onQueue: () -> Unit, onSync: () -> Unit, onRefresh: () -> Unit,
    onDelete: () -> Unit, onOpenPublic: (() -> Unit)? = null, onShareYouTube: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GratifyColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PlaylistOptionsContent(title, trackCount, isPublic, visibilityKnown, ytPlaylistId, onAdd, onEdit,
            onVisibility, onQueue, onSync, onRefresh, onDelete, onOpenPublic, onShareYouTube)
    }
}

@Composable
fun PlaylistOptionsContent(title: String, trackCount: Int, isPublic: Boolean, visibilityKnown: Boolean,
    ytPlaylistId: String?, onAdd: () -> Unit, onEdit: () -> Unit, onVisibility: () -> Unit,
    onQueue: () -> Unit, onSync: () -> Unit, onRefresh: () -> Unit, onDelete: () -> Unit,
    onOpenPublic: (() -> Unit)? = null, onShareYouTube: (() -> Unit)? = null) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold))
        Text(stringResource(Res.string.playlist_manage_song_count, trackCount), Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color = GratifyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        PlaylistManagementAction(Icons.Rounded.AddCircleOutline, stringResource(Res.string.playlist_flow_add_title), onAdd)
        PlaylistManagementAction(Icons.Rounded.Edit, stringResource(Res.string.playlist_manage_edit), onEdit)
        PlaylistManagementAction(if (isPublic) Icons.Rounded.Public else Icons.Rounded.Lock,
            stringResource(if (!visibilityKnown) Res.string.playlist_manage_check_visibility else if (isPublic) Res.string.playlist_manage_public else Res.string.playlist_manage_private), onVisibility)
        if (isPublic && onOpenPublic != null) PlaylistManagementAction(Icons.Rounded.OpenInNew,
            stringResource(Res.string.playlist_manage_view_public), onOpenPublic)
        if (trackCount > 0) PlaylistManagementAction(Icons.Rounded.QueueMusic, stringResource(Res.string.add_to_queue), onQueue)
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = GratifyColors.Divider)
        PlaylistManagementAction(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            stringResource(Res.string.playlist_manage_youtube), { advanced = !advanced })
        if (advanced) {
            Text(stringResource(Res.string.playlist_manage_youtube_hint), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = GratifyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            PlaylistManagementAction(Icons.Rounded.Sync, stringResource(if (ytPlaylistId == null) Res.string.sync else Res.string.playlist_manage_disconnect), onSync)
            if (ytPlaylistId != null) PlaylistManagementAction(Icons.Rounded.Refresh, stringResource(Res.string.update_playlist), onRefresh)
            if (ytPlaylistId != null && onShareYouTube != null) PlaylistManagementAction(Icons.Rounded.Share,
                stringResource(Res.string.share), onShareYouTube)
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = GratifyColors.Divider)
        PlaylistManagementAction(Icons.Rounded.DeleteOutline, stringResource(Res.string.delete_playlist), onDelete, destructive = true)
    }
}

@Composable
private fun PlaylistManagementAction(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val color = if (destructive) GratifyColors.Error else GratifyColors.TextPrimary
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Text(label, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditDialog(title: String, saving: Boolean, failed: Boolean, onSave: (String) -> Unit,
    onCover: () -> Unit, onDismiss: () -> Unit, coverPreview: String? = null, coverChanged: Boolean = false) {
    var name by rememberSaveable(title) { mutableStateOf(title) }
    ModalBottomSheet(onDismissRequest={if(!saving)onDismiss()},containerColor=GratifyColors.Surface,
        sheetState=com.tan.gratify.ui.component.rememberBusyModalSheetState(saving),dragHandle=null) {
        Column(Modifier.fillMaxWidth().heightIn(max=720.dp).imePadding()) {
            WorkspaceSheetHeader(stringResource(Res.string.playlist_manage_edit),stringResource(Res.string.cancel),
                stringResource(if(saving)Res.string.playlist_builder_saving else Res.string.save),saving,
                validPlaylistName(name)&&(name.trim()!=title||coverChanged),onDismiss,{onSave(name.trim())})
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top) {
                    Box(Modifier.size(88.dp).clickable(enabled=!saving,onClick=onCover),contentAlignment=Alignment.Center) {
                        if(coverPreview!=null)coil3.compose.AsyncImage(coverPreview,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote,null,Modifier.size(48.dp),tint=GratifyColors.TextSecondary)
                    }
                    TextField(name,{name=it.take(100)},enabled=!saving,singleLine=true,label={Text(stringResource(Res.string.playlist_builder_name))},modifier=Modifier.weight(1f))
                }
            OutlinedButton(onClick = onCover, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.edit_thumbnail))
            }
            Text(stringResource(Res.string.playlist_manage_cover_hint), style = MaterialTheme.typography.bodySmall,
                color = GratifyColors.TextSecondary)
            if (failed) Text(stringResource(Res.string.playlist_manage_edit_failed), color = GratifyColors.Error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if(saving)LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
fun PlaylistVisibilityDialog(isPublic: Boolean, known: Boolean, busy: Boolean, failed: Boolean,
    onConfirm: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.playlist_manage_visibility)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (!known) Res.string.playlist_manage_visibility_unknown
                else if (isPublic) Res.string.playlist_manage_public_hint else Res.string.playlist_manage_private_hint))
            if (failed) Text(stringResource(Res.string.playlist_manage_visibility_failed), color = GratifyColors.Error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } },
        confirmButton = { TextButton(onClick = if (known) onConfirm else onRetry, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (!known) Res.string.retry else if (isPublic) Res.string.hide_from_public else Res.string.share_to_public))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.cancel)) } })
}

@Composable
fun PlaylistDeleteDialog(title: String, isPublic: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.delete_playlist)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (isPublic) Res.string.playlist_manage_delete_public_hint else Res.string.playlist_manage_delete_hint))
        } },
        confirmButton = { TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.delete), color = GratifyColors.Error)
        } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.cancel)) } })
}
