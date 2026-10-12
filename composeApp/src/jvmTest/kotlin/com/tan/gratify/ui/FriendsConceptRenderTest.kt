package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.model.social.MusicStory
import com.tan.gratify.ui.screen.friends.*
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class FriendsConceptRenderTest {
    private val now = System.currentTimeMillis()
    private fun friend(id: String, name: String, visible: Boolean = true, age: Long = 0) = friendActivityAt(
        UserProfile(id, name, showRecentArtists = visible, nowPlayingTitle = "Lagu $name", nowPlayingArtist = "Nadira",
            nowPlayingVideoId = id, lastActiveAt = (now-age).toString()), now)
    private fun story(id: String, audience: String = "public") = MusicStory(id, "nara", "Nara", videoId = id,
        title = "Potongan $id", artist = "Nadira", trackDurationMs = 120000, startMs = 0, clipSeconds = 15,
        caption = "Untuk perjalanan pulang", audience = audience, createdAt = "2026-10-08T00:00:00Z", expiresAt = "2099-10-09T00:00:00Z")
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private fun desc(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private fun click(n: SemanticsNode) = n.config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(s: ImageComposeScene, start: Long = 0) { repeat(24) { s.render(start+it*16_000_000L).close(); delay(16) } }
    private fun capture(s: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/friends-audit-2026-10-08/$name.png"); Files.createDirectories(path.parent)
        s.render(9_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }

    @Test fun musicFirstActionsRemainIndependentWithLargeTextAndDesktopWidths(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            var played = ""; var profile = ""; var chat = ""; var shares = 0; var notes = 0; var refreshes = 0
            val tab = mutableStateOf(FriendsInboxTab.ACTIVITY)
            val people = listOf(friend("nara","Nara"), friend("bima","Bima"), friend("private","Citra",false), friend("old","Dimas", age=600001))
            val scene = ImageComposeScene(width, 1000, Density(1f,scale), coroutineContext=coroutineContext) {
                AppTheme { Box(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                    FriendsInboxContent(FriendsActivityState(currentUserId="self",friendsList=people),FriendsSocialState(userId="self"),
                        "Alia",null,tab.value,{tab.value=it},{},{shares++},{notes++},{chat=it},{profile=it},{played=it},{},{refreshes++},{},
                        PaddingValues(top=76.dp,bottom=156.dp))
                    FriendsInboxHeader("Alia",{},{},{})
                } }
            }
            try {
                settle(scene)
                click(nodes(scene).first { desc(it)=="Lihat profil Nara" }); assertEquals("nara",profile); assertEquals("",played)
                click(nodes(scene).first { desc(it)=="Pesan Nara" }); assertEquals("nara",chat); assertEquals("",played)
                click(nodes(scene).first { desc(it)=="Putar Lagu Nara" }); assertEquals("nara",played)
                assertFalse(nodes(scene).any { desc(it)?.contains("Story")==true || desc(it)=="Tambah story musik" }); assertEquals(0,shares)
                click(nodes(scene).first { desc(it)=="Edit catatan kamu" && it.config.contains(SemanticsActions.OnClick) }); assertEquals(1,notes)
                click(nodes(scene).first { desc(it)=="Refresh friends" }); assertEquals(1,refreshes)
                assertFalse(nodes(scene).any { desc(it)=="Putar Lagu Citra" || desc(it)=="Putar Lagu Dimas" })
                if(width==1100) {
                    val play = nodes(scene).first { desc(it)=="Putar Lagu Nara" }
                    assertTrue(play.boundsInRoot.right<=930f); assertTrue(play.boundsInRoot.left>=170f)
                }
                capture(scene,"activity-$width")
                nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) && it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(0f,10000f)
                settle(scene,2_000_000_000L)
                val last = nodes(scene).first { desc(it)=="Pesan Dimas" }
                assertTrue(last.boundsInRoot.bottom<=844f,"Last friend is covered by player/navigation")
            } finally { scene.close() }
        }
    }

    @Test fun restoredExploreNavigationShowsNotesWithoutAnyStoryTargets(): Unit = runBlocking(Dispatchers.Swing) {
        var viewed = 0
        val scene = ImageComposeScene(390,1000,Density(1f),coroutineContext=coroutineContext) {
            AppTheme { FriendsInboxContent(FriendsActivityState(currentUserId="self",myNote="Catatan tetap"),
                FriendsSocialState(userId="self",publicFeed=true,stories=listOf(story("first"),story("secret","friends"))),
                "Alia",null,FriendsInboxTab.PUBLIC,{},{viewed++},{},{},{},{},{},{},{},{}) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { text(it)?.contains("Catatan tetap")==true })
            assertFalse(nodes(scene).any { text(it)?.startsWith("Potongan")==true || desc(it)?.startsWith("Story")==true })
            assertFalse(nodes(scene).any { text(it)=="Explore" || text(it)=="24 hours" })
            assertEquals(0, viewed)
        } finally { scene.close() }
    }

    @Test fun signInLoadingAndFailureAreDifferentStatesAndNoteEditorRetainsInput(): Unit = runBlocking(Dispatchers.Swing) {
        val activity = mutableStateOf(FriendsActivityState())
        val social = mutableStateOf(FriendsSocialState())
        var logins = 0
        val scene = ImageComposeScene(320,900,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { FriendsInboxContent(activity.value,social.value,"Alia",null,FriendsInboxTab.ACTIVITY,{},{},{},{},{},{},{},{},{},{},onLogin={logins++}) }
        }
        try {
            settle(scene); click(nodes(scene).first { text(it)=="Sign in" && it.config.contains(SemanticsActions.OnClick) }); assertEquals(1,logins)
            capture(scene,"signed-out-320")
            activity.value=FriendsActivityState(currentUserId="self",isLoading=true); social.value=FriendsSocialState(userId="self",loading=true)
            settle(scene,2_000_000_000L); assertTrue(nodes(scene).any { desc(it)=="Loading friends" })
            assertFalse(nodes(scene).any { text(it)=="Start with one friend" })
            activity.value=activity.value.copy(isLoading=false,error="offline"); social.value=social.value.copy(loading=false)
            settle(scene,3_000_000_000L); assertTrue(nodes(scene).any { text(it)=="Could not load friend activity" })
            assertFalse(nodes(scene).any { text(it)=="Start with one friend" }); capture(scene,"error-320")
        } finally { scene.close() }
        val value = mutableStateOf("Catatan belum tersimpan"); val saving = mutableStateOf(false)
        val noteScene = ImageComposeScene(320,600,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { Column(Modifier.background(GratifyColors.Background).padding(16.dp)) { FriendsNoteInput(value.value,{value.value=it},saving.value,true) } }
        }
        try {
            settle(noteScene); assertTrue(nodes(noteScene).any { text(it)=="Your note was not saved. Try again." })
            val input=nodes(noteScene).first { it.config.contains(SemanticsActions.SetText) }
            input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("x".repeat(101))); assertEquals("Catatan belum tersimpan",value.value)
            saving.value=true; settle(noteScene,2_000_000_000L)
            assertTrue(nodes(noteScene).any { it.config.contains(SemanticsProperties.Disabled) && it.config.getOrNull(SemanticsProperties.EditableText)?.text==value.value })
            capture(noteScene,"note-error-320")
        } finally { noteScene.close() }
    }
}
