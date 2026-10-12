package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.model.social.*
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.gratify.expect.ClipPlaybackState
import com.tan.gratify.ui.screen.friends.*
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class FriendsSocialRenderTest {
    @Test fun lyricsShareDraftSurvivesDurationLoadingAndOpensTheCorrectSong(): Unit = runBlocking(Dispatchers.Swing) {
        val track = StoryTrack("shared-song", "Lagu pilihan lirik", "Nadira", null, 0)
        val seed = MusicStoryDraft(track, 23000, 15, "aku bersamamu\nkali ini", "public")
        var published: MusicStoryDraft? = null
        val scene = ImageComposeScene(360, 900, Density(1f, 1.3f), coroutineContext = coroutineContext) {
            AppTheme { MusicStoryEditor(StoryTrack("other-song", "Lagu lain", "Artis lain", null, 180000), emptyList(), false, false, null,
                {}, {}, {}, { published = it }, completeTrack = { it.copy(durationMs = 180000) }, initialDraft = seed) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { text(it) == track.title })
            assertFalse(nodes(scene).any { text(it) == "Lagu lain" })
            nodes(scene).first { text(it) == "Bagikan" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertNotNull(published); assertEquals(seed.caption, published!!.caption)
            assertEquals(seed.startMs, published!!.startMs); assertEquals("public", published!!.audience)
            assertEquals("shared-song", published!!.track.videoId); assertTrue(published!!.isValid)
            capture(scene, "lyrics-share-editor")
        } finally { scene.close() }
    }
    private val now = Clock.System.now()
    private val expires = now.plus(kotlin.time.Duration.parse("24h")).toString()
    private fun story(id: String, owner: String, name: String, caption: String = "") = MusicStory(id, owner, name,
        videoId = "song-$id", title = "Langkah menuju cahaya", artist = "Nadira", trackDurationMs = 180000,
        startMs = 15000, clipSeconds = 15, caption = caption, createdAt = now.toString(), expiresAt = expires)
    private val stories = listOf(story("a","self","Alia"),story("b","nara","Nara"),story("c","bima","Bima"),
        story("expired","expired","Tidak tampil").copy(expiresAt = "2020-01-01T00:00:00Z"))
    private val chats = listOf(DirectConversation("chat-a","nara","Nara",accepted = true,requestFrom = "self",lastBody = "Dengarkan bagian reff-nya",unreadCount = 2),
        DirectConversation("request-a","citra","Citra",accepted = false,requestFrom = "citra",lastBody = "Halo, boleh kenalan?",unreadCount = 1))
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private fun desc(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private suspend fun settle(s: ImageComposeScene, start: Long = 0) { repeat(24) { s.render(start + it*16_000_000L).close(); delay(16) } }
    private fun capture(s: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/friends-social/$name.png"); Files.createDirectories(path.parent)
        s.render(2_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path,data.bytes) } }
    }
    @Test fun notesReplaceStoriesAndConversationActionsRemainSeparate(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width,scale) in listOf(390 to 1f,360 to 1.5f,900 to 1f)) {
            var opened = ""; var profile = ""; var created = 0; var viewed = ""
            val tab = mutableStateOf(FriendsInboxTab.ACTIVITY)
            val friends = (1..12).map { FriendActivityItem(UserProfile("friend-$it","Teman $it"),false,"","","","Offline") }
            val scene = ImageComposeScene(width,900,Density(1f,scale),coroutineContext = coroutineContext) {
                AppTheme { Box(Modifier.fillMaxSize().background(GratifyColors.Background)) {
                    FriendsInboxContent(FriendsActivityState(friendsList = friends),FriendsSocialState(userId = "self",stories = stories,conversations = chats),
                        "Alia",null,tab.value,{tab.value=it},{viewed=it.first().ownerId},{created++},{},{opened=it},{profile=it},{},{},{},{},
                        PaddingValues(top=76.dp,bottom=156.dp))
                    FriendsInboxHeader("Alia",{},{},{})
                } }
            }
            try {
                settle(scene)
                assertFalse(nodes(scene).any { desc(it)?.contains("Tidak tampil")==true })
                assertFalse(nodes(scene).any { desc(it)?.startsWith("Story")==true }); assertEquals("",viewed)
                tab.value=FriendsInboxTab.MAIN; settle(scene,2_400_000_000L)
                nodes(scene).first { desc(it)=="Pesan Nara" }.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals("nara",opened)
                nodes(scene).first { desc(it)=="Lihat profil Nara" }.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals("nara",profile)
                tab.value=FriendsInboxTab.ACTIVITY; settle(scene,2_600_000_000L)
                assertTrue(nodes(scene).any { desc(it)=="Edit catatan kamu" }); assertEquals(0,created)
                capture(scene,"inbox-$width")
                tab.value=FriendsInboxTab.REQUESTS; settle(scene,3_000_000_000L)
                assertTrue(nodes(scene).any { desc(it)=="Pesan Citra" }); assertFalse(nodes(scene).any { desc(it)=="Pesan Nara" })
                capture(scene,"requests-$width")
                tab.value=FriendsInboxTab.PUBLIC; settle(scene,4_000_000_000L); capture(scene,"public-$width")
                tab.value=FriendsInboxTab.MAIN; settle(scene,5_000_000_000L)
                nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) && it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(0f,10000f)
                settle(scene,6_000_000_000L)
                val last=nodes(scene).first { desc(it)=="Pesan Teman 12" }
                assertTrue(last.boundsInRoot.bottom<=744f,"Last contact covered by player/navigation")
            } finally { scene.close() }
        }
    }
    @Test fun storyEditorClampsExcerptWhenDurationChangesAndKeepsPublishingReachable(): Unit = runBlocking(Dispatchers.Swing) {
        var published: MusicStoryDraft? = null
        for ((width,scale) in listOf(390 to 1f,360 to 1.5f)) {
            val scene=ImageComposeScene(width,900,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { MusicStoryEditor(StoryTrack("sample","Langkah menuju cahaya","Nadira",null,60000),emptyList(),false,false,null,{}, {}, {}, {published=it}) }
            }
            try {
                settle(scene); capture(scene,"story-editor-$width")
                val slider=nodes(scene).first { desc(it)=="Titik mulai potongan musik" }
                slider.config[SemanticsActions.SetProgress].action!!.invoke(45000f)
                settle(scene,2_500_000_000L)
                nodes(scene).first { text(it)=="30 detik" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
                settle(scene,3_000_000_000L)
                nodes(scene).first { text(it)=="Bagikan" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(30000L,published!!.startMs); assertEquals(30,published!!.clipSeconds); assertTrue(published!!.isValid)
            } finally { scene.close() }
        }
    }
    @Test fun requestsRequireAcceptanceAndFailedSendPreservesDraft(): Unit = runBlocking(Dispatchers.Swing) {
        val state=mutableStateOf(FriendsSocialState(userId="self",activeConversation=chats[1]))
        var accept: Boolean? = null; var sent=""
        val scene=ImageComposeScene(390,900,Density(1f,1.3f),coroutineContext=coroutineContext) {
            AppTheme { FriendsChatContent(state.value,{}, {}, {body,_ -> sent=body}, {accept=it},{},{},{}) }
        }
        try {
            settle(scene); capture(scene,"chat-request")
            assertFalse(nodes(scene).any { desc(it)=="Kirim pesan" })
            nodes(scene).first { text(it)=="Terima" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(true,accept)
            state.value=state.value.copy(activeConversation=chats[0],messages=listOf(DirectMessage("m1","chat-a","nara","client","Musik untuk perjalanan pulang",now.toString())))
            settle(scene,3_000_000_000L)
            nodes(scene).first { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString("Halo Nara"))
            settle(scene,4_000_000_000L)
            nodes(scene).first { desc(it)=="Kirim pesan" }.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals("Halo Nara",sent)
            state.value=state.value.copy(chatError="Koneksi terputus")
            settle(scene,5_000_000_000L); capture(scene,"chat")
            assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.EditableText)?.text=="Halo Nara" })
        } finally { scene.close() }
    }
    @Test fun storyViewerHasPlaybackRecoveryAndReplyWithoutInventedActivity(): Unit = runBlocking(Dispatchers.Swing) {
        var retries=0;var replies=0
        val scene=ImageComposeScene(390,844,Density(1f,1.3f),coroutineContext=coroutineContext) {
            AppTheme { MusicStoryViewer(stories[1],0,2,ClipPlaybackState(error=true,buffering=false),true,false,false,{}, {retries++},{},{},{},{replies++},{}) }
        }
        try {
            settle(scene);capture(scene,"story-viewer")
            nodes(scene).first { text(it)=="Coba lagi" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,retries)
            nodes(scene).first { text(it)=="Kirim pesan" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,replies)
            assertFalse(nodes(scene).any { text(it)?.contains("Sedang aktif")==true })
        } finally { scene.close() }
    }

    @Test fun previewShowsElapsedSecondsAndLyricsFromTheSelectedPart(): Unit = runBlocking(Dispatchers.Swing) {
        val playback = mutableStateOf(ClipPlaybackState(positionMs = 2500, buffering = false))
        val lyrics = Lyrics(lines = listOf(Line("6000", "0", words = "Bagian awal"),
            Line("12000", "6000", words = "Bagian dipilih"), Line("18000", "12000", words = "Bagian berikutnya")), syncType = "LINE_SYNCED")
        val sample = stories[0].copy(startMs = 5000)
        val scene = ImageComposeScene(390, 900, Density(1f, 1.3f), coroutineContext = coroutineContext) {
            AppTheme { MusicStoryViewer(sample, 0, 1, playback.value, true, true, false,
                {}, {}, {}, {}, {}, {}, {}, canDelete = false, lyrics = lyrics) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { text(it) == "0:02 / 0:15" })
            assertTrue(nodes(scene).any { text(it) == "Bagian dipilih" })
            capture(scene, "story-preview-lyrics")
            playback.value = playback.value.copy(positionMs = 8000)
            settle(scene, 3_000_000_000L)
            assertTrue(nodes(scene).any { text(it) == "0:08 / 0:15" })
            playback.value = playback.value.copy(positionMs = 0, completed = true)
            settle(scene, 4_000_000_000L)
            assertTrue(nodes(scene).any { text(it) == "0:15 / 0:15" })
        } finally { scene.close() }
    }

    @Test fun currentSongWithUnknownDurationCanBeCompletedAndPublishedWithLyricsPreview(): Unit = runBlocking(Dispatchers.Swing) {
        var published: MusicStoryDraft? = null
        val scene = ImageComposeScene(360, 900, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme { MusicStoryEditor(StoryTrack("current", "Langkah menuju cahaya", "Nadira", null, 0),
                emptyList(), false, false, null, {}, {}, {}, { published = it },
                completeTrack = { it.copy(durationMs = 12000) },
                loadLyrics = { flowOf(Lyrics(lines = listOf(Line("8000", "0", words = "<00:00.00>Cahaya <00:01.00>pagi")), syncType = "RICH_SYNCED")) }) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { text(it) == "Cahaya pagi" })
            assertTrue(nodes(scene).any { text(it) == "Pratinjau musik & lirik" })
            nodes(scene).first { text(it) == "Bagikan" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(12000L, published?.track?.durationMs)
            assertEquals(8, published?.clipSeconds)
            assertTrue(published?.isValid == true)
            capture(scene, "story-editor-lyrics-360")
        } finally { scene.close() }
    }
}
