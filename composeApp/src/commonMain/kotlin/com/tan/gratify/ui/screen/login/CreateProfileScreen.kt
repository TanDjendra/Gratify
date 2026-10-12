package com.tan.gratify.ui.screen.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.gratify.expect.ui.photoPickerResult
import com.tan.gratify.ui.component.ProfileEditorContent
import com.tan.gratify.ui.navigation.destination.login.YouTubeConnectDestination
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.viewModel.CreateProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateProfileScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    hideBottomNavigation: () -> Unit = {},
    showBottomNavigation: () -> Unit = {},
    viewModel: CreateProfileViewModel = koinViewModel(),
    skipYouTubeConnection: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val photoPicker = photoPickerResult(com.tan.gratify.expect.ui.PhotoCropShape.CIRCLE) { uri -> uri?.let(viewModel::updateAvatarPath) }
    LaunchedEffect(Unit) { hideBottomNavigation() }
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) navController.navigate(if (skipYouTubeConnection) MainDestination else YouTubeConnectDestination) { popUpTo(0) { inclusive = true } }
    }
    ProfileEditorContent(state, creating = true, onNameChange = viewModel::updateDisplayName,
        onChoosePhoto = { photoPicker.launch() }, onRemovePhoto = { viewModel.updateAvatarPath("") },
        onSave = viewModel::saveProfile, photoSelectionActive = photoPicker.isActive)
}
