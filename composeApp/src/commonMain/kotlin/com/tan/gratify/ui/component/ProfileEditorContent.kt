package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileActionsContent(editEnabled: Boolean, privacyEnabled: Boolean, preview: Boolean,
    onEdit: () -> Unit, onPrivacy: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (preview) Text("Pratinjau profil", modifier = Modifier.weight(1f), style = typo().titleSmall)
        else {
            OutlinedButton(onClick = onEdit, enabled = editEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(Res.string.profile_edit_title)) }
            IconButton(onClick = onPrivacy, enabled = privacyEnabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Visibility, stringResource(Res.string.profile_privacy_action))
            }
        }
        IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.MoreVert, stringResource(Res.string.profile_more_action))
        }
    }
}

@Composable
internal fun profileSaveMessage(problem: ProfileSaveProblem): String = stringResource(when (problem) {
    ProfileSaveProblem.NAME_REQUIRED -> Res.string.profile_name_required
    ProfileSaveProblem.NAME_TOO_LONG -> Res.string.profile_name_too_long
    ProfileSaveProblem.PHOTO_FAILED -> Res.string.profile_photo_failed
    ProfileSaveProblem.PHOTO_UPLOAD_FAILED -> Res.string.profile_photo_upload_failed
    ProfileSaveProblem.SIGN_IN_REQUIRED -> Res.string.profile_sign_in_required
    ProfileSaveProblem.ACCOUNT_CHANGED -> Res.string.profile_account_changed
    ProfileSaveProblem.SAVE_FAILED -> Res.string.profile_save_failed
})

@Composable
fun ProfileSaveFeedback(state: CreateProfileUiState, modifier: Modifier = Modifier) {
    val problem = state.problem?.takeUnless { it == ProfileSaveProblem.NAME_REQUIRED || it == ProfileSaveProblem.NAME_TOO_LONG }
    if (!state.isSaving && !state.saveSuccess && problem == null) return
    val message = when {
        state.isSaving -> stringResource(if (state.saveStage == ProfileSaveStage.UPLOADING_PHOTO)
            Res.string.profile_uploading_photo else Res.string.profile_updating)
        state.saveSuccess -> stringResource(Res.string.profile_update_success)
        else -> profileSaveMessage(problem!!)
    }
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isSaving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(message, style = typo().bodyMedium, modifier = Modifier.weight(1f),
            color = when { state.isSaving -> GratifyColors.TextSecondary
                state.saveSuccess -> GratifyColors.Accent
                else -> MaterialTheme.colorScheme.error })
    }
}

/** Shared, scrollable editor; the close action confirms only actual unsaved changes. */
@Composable
fun ProfileEditorContent(
    state: CreateProfileUiState, creating: Boolean,
    onNameChange: (String) -> Unit, onChoosePhoto: () -> Unit, onRemovePhoto: () -> Unit,
    onSave: () -> Unit, onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    photoSelectionActive: Boolean = false,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = {
        if (!state.isSaving) {
            if (state.hasChanges && !state.saveSuccess) confirmDiscard = true else onClose()
        }
    }
    LayerBackHandler(!creating && !photoSelectionActive) { close() }
    val nameProblem = state.problem?.takeIf { it == ProfileSaveProblem.NAME_REQUIRED || it == ProfileSaveProblem.NAME_TOO_LONG }
    Column(modifier.fillMaxSize().background(GratifyColors.Background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!creating) IconButton(onClick = close, enabled = !state.isSaving, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(Res.string.profile_close))
            }
            Text(stringResource(if (creating) Res.string.profile_create_title else Res.string.profile_edit_title),
                style = typo().titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).padding(12.dp), color = GratifyColors.TextPrimary)
        }
        ProfileSaveFeedback(state, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(Res.string.profile_identity_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val photoLabel = stringResource(Res.string.profile_choose_photo)
                    Box(Modifier.size(104.dp).clickable(enabled = !state.isSaving, role = Role.Button,
                        onClickLabel = photoLabel, onClick = onChoosePhoto)) {
                        UserAvatar(state.avatarPath.takeIf { it.isNotBlank() }, state.displayName, Modifier.fillMaxSize(), allowLocalPreview = true)
                    }
                    TextButton(onClick = onChoosePhoto, enabled = !state.isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(photoLabel)
                    }
                    if (state.avatarPath.isNotEmpty()) TextButton(onClick = onRemovePhoto, enabled = !state.isSaving,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.profile_remove_photo)) }
                    Text(stringResource(Res.string.profile_photo_optional), style = typo().bodySmall, color = GratifyColors.TextSecondary)
                }
                OutlinedTextField(value = state.displayName, onValueChange = onNameChange, enabled = !state.isSaving,
                    singleLine = true, label = { Text(stringResource(Res.string.profile_name_label)) },
                    isError = nameProblem != null,
                    supportingText = {
                        Text(nameProblem?.let { profileSaveMessage(it) }
                            ?: stringResource(Res.string.profile_name_hint, state.displayName.trim().length, PROFILE_NAME_LIMIT))
                    }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!state.isSaving) onSave() }))
                Button(onClick = onSave, enabled = !state.isSaving && state.ownerId != null &&
                    profileNameProblem(state.displayName) == null && (creating || state.hasChanges || state.problem != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GratifyColors.TextSecondary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(stringResource(if (state.isSaving) Res.string.profile_saving else if (state.problem != null) Res.string.profile_retry else if (creating)
                        Res.string.profile_save_continue else Res.string.profile_save_changes))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(Res.string.profile_discard_title)) },
        text = { Text(stringResource(Res.string.profile_discard_hint)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(Res.string.profile_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.profile_keep_editing)) } })
}

@Composable
fun ProfilePrivacyContent(
    followers: Boolean, playlists: Boolean, artists: Boolean, ready: Boolean, saving: Boolean,
    error: String?, onChange: (String, Boolean) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
        .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.profile_privacy_title), style = typo().titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(stringResource(Res.string.profile_privacy_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
        if (!ready) {
            Text(stringResource(Res.string.profile_privacy_unavailable), color = GratifyColors.TextSecondary)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.profile_retry)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (saving) Text(stringResource(Res.string.profile_saving), color = GratifyColors.TextSecondary)
        listOf(
            Triple("privacy_show_followers", Res.string.profile_followers_label, followers),
            Triple("privacy_show_playlist", Res.string.profile_playlists_label, playlists),
            Triple("privacy_show_recent_artists", Res.string.profile_artists_label, artists),
        ).forEach { (key, label, checked) ->
            val hint = when (key) {
                "privacy_show_followers" -> Res.string.profile_followers_hint
                "privacy_show_playlist" -> Res.string.profile_playlists_hint
                else -> Res.string.profile_artists_hint
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(value = checked, enabled = ready && !saving,
                role = Role.Switch, onValueChange = { onChange(key, it) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(label), style = typo().titleSmall)
                    Text(stringResource(hint), style = typo().bodySmall, color = GratifyColors.TextSecondary)
                }
                Switch(checked = checked, onCheckedChange = null, enabled = ready && !saving)
            }
        }
    }
}
