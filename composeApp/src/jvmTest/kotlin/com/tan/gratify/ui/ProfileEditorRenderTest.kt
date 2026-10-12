package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ProfileEditorRenderTest {
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun click(scene: ImageComposeScene, label: String) = nodes(scene).first {
        (label in texts(it) || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true) && it.config.contains(SemanticsActions.OnClick)
    }.config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(scene: ImageComposeScene) { repeat(20) { scene.render(it * 16_000_000L).close(); delay(16) } }
    private fun snapshot(scene: ImageComposeScene, name: String, folder: String = "profile-2026-10-08") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val file = root.resolve("artifacts/$folder/$name.png"); Files.createDirectories(file.parent)
        scene.render(3_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
    }
    @Test fun editorFitsSmallLargeTextAndWideScreens(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            var saves = 0
            val scene = ImageComposeScene(width, 1100, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { ProfileEditorContent(CreateProfileUiState(ownerId = "fixture", displayName = "Nara", initialName = "Earlier name"),
                    false, {}, {}, {}, { saves++ }) }
            }
            try {
                settle(scene)
                assertTrue(nodes(scene).filter { texts(it).isNotEmpty() }.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width })
                val save = nodes(scene).first { "Save changes" in texts(it) && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(save.boundsInRoot.height >= 48); click(scene, "Save changes"); assertEquals(1, saves)
                snapshot(scene, "editor-$width")
            } finally { scene.close() }
        }
    }
    @Test fun closingDirtyEditorKeepsDraftUntilDiscardIsChosen(): Unit = runBlocking(Dispatchers.Swing) {
        var closed = 0
        val scene = ImageComposeScene(390, 900, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { ProfileEditorContent(CreateProfileUiState(ownerId = "fixture", displayName = "New name", initialName = "Nara"),
                false, {}, {}, {}, {}, { closed++ }) }
        }
        try {
            settle(scene); click(scene, "Close"); settle(scene); assertEquals(0, closed)
            assertTrue(nodes(scene).any { "Discard changes?" in texts(it) })
            snapshot(scene, "editor-discard")
            click(scene, "Keep editing"); settle(scene); assertEquals(0, closed)
            click(scene, "Close"); settle(scene); click(scene, "Discard"); assertEquals(1, closed)
        } finally { scene.close() }
    }
    @Test fun saveFailureAndPhotoRemovalAreVisibleWhileBusyControlsAreDisabled(): Unit = runBlocking(Dispatchers.Swing) {
        var state by mutableStateOf(CreateProfileUiState(ownerId = "fixture", displayName = "Nara", initialName = "Earlier",
            avatarPath = "https://example.invalid/storage/v1/object/public/avatars/fixture.jpg", problem = ProfileSaveProblem.SAVE_FAILED))
        var removed = 0
        val scene = ImageComposeScene(390, 1100, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { ProfileEditorContent(state, false, {}, {}, { removed++ }, {}) }
        }
        try {
            settle(scene); assertTrue(nodes(scene).any { texts(it).any { text -> text.startsWith("Your profile could not be saved.") } })
            click(scene, "Remove photo"); assertEquals(1, removed); snapshot(scene, "editor-failure")
            state = state.copy(isSaving = true); settle(scene)
            listOf("Choose photo", "Remove photo", "Saving…").forEach { label ->
                assertTrue(nodes(scene).first { label in texts(it) && it.config.contains(SemanticsActions.OnClick) }
                    .config.contains(SemanticsProperties.Disabled), label)
            }
            assertTrue(nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.all { it.config.contains(SemanticsProperties.Disabled) })
        } finally { scene.close() }
    }
    @Test fun updatingFeedbackChangesFromUploadToSaveThenSuccessOrRetry(): Unit = runBlocking(Dispatchers.Swing) {
        var saves = 0
        var closes = 0
        var state by mutableStateOf(CreateProfileUiState(ownerId = "fixture", displayName = "Nara",
            initialName = "Before", isSaving = true, saveStage = ProfileSaveStage.UPLOADING_PHOTO))
        val scene = ImageComposeScene(320, 1100, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme { ProfileEditorContent(state, false, {}, {}, {}, { saves++ }, { closes++ }) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { "Uploading photo…" in texts(it) })
            assertTrue(nodes(scene).any { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) })
            click(scene, "Close"); assertEquals(0, closes)
            snapshot(scene, "feedback-uploading", "photo-privacy-revision-20261012")
            state = state.copy(saveStage = ProfileSaveStage.SAVING_PROFILE); settle(scene)
            assertTrue(nodes(scene).any { "Updating profile…" in texts(it) })
            assertFalse(nodes(scene).any { "Uploading photo…" in texts(it) })
            snapshot(scene, "feedback-updating", "photo-privacy-revision-20261012")
            state = state.copy(isSaving = false, saveStage = null, problem = ProfileSaveProblem.PHOTO_UPLOAD_FAILED)
            settle(scene)
            assertTrue(nodes(scene).any { texts(it).any { text -> text.startsWith("Your photo could not be uploaded.") } })
            assertFalse(nodes(scene).any { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) })
            snapshot(scene, "feedback-failed", "photo-privacy-revision-20261012")
            click(scene, "Try again"); assertEquals(1, saves)
            state = state.copy(problem = null, saveSuccess = true); settle(scene)
            assertTrue(nodes(scene).any { "Profile saved successfully." in texts(it) })
            assertFalse(nodes(scene).any { "Updating profile…" in texts(it) })
            snapshot(scene, "feedback-success", "photo-privacy-revision-20261012")
        } finally { scene.close() }
    }
    @Test fun privacyRowsDescribeActivityAndHaveFullRowToggleActions(): Unit = runBlocking(Dispatchers.Swing) {
        val changes = mutableListOf<Pair<String, Boolean>>()
        var ready by mutableStateOf(true); var retries = 0
        val scene = ImageComposeScene(320, 1200, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme { ProfilePrivacyContent(true, false, false, ready, false, null,
                { key, visible -> changes += key to visible }, { retries++ }) }
        }
        try {
            settle(scene)
            click(scene, "Followers and following"); click(scene, "Displayed playlists")
            assertEquals(listOf("privacy_show_followers" to false, "privacy_show_playlist" to true), changes)
            assertTrue(nodes(scene).any { "Followed artists and listening activity" in texts(it) })
            val toggles = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
            assertEquals(3, toggles.size); assertTrue(toggles.all { it.boundsInRoot.height >= 72 })
            snapshot(scene, "privacy-320")
            ready = false; settle(scene); click(scene, "Try again"); assertEquals(1, retries)
            assertTrue(nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }.all { it.config.contains(SemanticsProperties.Disabled) })
        } finally { scene.close() }
    }
    @Test fun profileActionsHaveReadableNamesAndLargeTargets(): Unit = runBlocking(Dispatchers.Swing) {
        val actions = mutableListOf<String>()
        val scene = ImageComposeScene(320, 600, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme { ProfileActionsContent(true, true, false, { actions += "edit" }, { actions += "privacy" }, { actions += "more" }) }
        }
        try {
            settle(scene); click(scene, "Edit profile"); click(scene, "Profile visibility"); click(scene, "More profile options")
            assertEquals(listOf("edit", "privacy", "more"), actions)
            assertTrue(nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.all { it.boundsInRoot.height >= 48 })
        } finally { scene.close() }
    }
}
