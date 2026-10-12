package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*

/** Debug-only visual fixture using production components. No account or server writes. */
class ProfilePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { AppTheme {
            var page by remember { mutableStateOf("profile") }
            var draft by remember { mutableStateOf(CreateProfileUiState(ownerId = "visual-fixture", displayName = "Nara", initialName = "Nara")) }
            var followers by remember { mutableStateOf(false) }
            var playlists by remember { mutableStateOf(true) }
            var artists by remember { mutableStateOf(false) }
            when (page) {
                "edit" -> ProfileEditorContent(draft, false,
                    onNameChange = { draft = draft.copy(displayName = it, problem = null) },
                    onChoosePhoto = {}, onRemovePhoto = { draft = draft.copy(avatarPath = "") },
                    onSave = { draft = draft.copy(problem = ProfileSaveProblem.SAVE_FAILED) }, onClose = { page = "profile" })
                "privacy" -> Column(Modifier.fillMaxSize().systemBarsPadding().background(GratifyColors.Background)) {
                    TextButton(onClick = { page = "profile" }) { Text("Back to profile") }
                    ProfilePrivacyContent(followers, playlists, artists, true, false, null, { key, value ->
                        when (key) {
                            "privacy_show_followers" -> followers = value
                            "privacy_show_playlist" -> playlists = value
                            else -> artists = value
                        }
                    }, {})
                }
                else -> Box(Modifier.fillMaxSize().background(GratifyColors.Background), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                        ProfileIdentityHeader("Nara", null, "24 pengikut", "18 mengikuti", true,
                            { finish() }, {}, {})
                        ProfileActionsContent(true, true, false,
                            { draft = CreateProfileUiState(ownerId = "visual-fixture", displayName = "Nara", initialName = "Nara"); page = "edit" },
                            { page = "privacy" }, {})
                        ProfileContentPlaceholder("Playlist", "Pilih playlist buatanmu untuk dipajang di profil.", Modifier.padding(horizontal = 20.dp))
                        ProfileContentPlaceholder("Artis yang diikuti", "Artis pilihanmu akan muncul di sini.", Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        } }
    }
}
