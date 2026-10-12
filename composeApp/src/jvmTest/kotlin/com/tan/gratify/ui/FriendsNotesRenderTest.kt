package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import com.tan.gratify.ui.screen.friends.*
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class FriendsNotesRenderTest {
    private var frame=0L
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene)=s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun labels(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()+n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private suspend fun settle(s: ImageComposeScene) { repeat(20) { frame+=16_000_000; s.render(frame).close(); delay(12) } }
    private fun action(s: ImageComposeScene,label: String)=nodes(s).first { label in labels(it) && it.config.contains(SemanticsActions.OnClick) }
    private fun click(s: ImageComposeScene,label: String)=action(s,label).config[SemanticsActions.OnClick].action!!.invoke()
    private fun capture(s: ImageComposeScene,name: String) {
        val root=generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val p=root.resolve("artifacts/friends-notes-2026-10-09/$name.png"); Files.createDirectories(p.parent)
        frame+=16_000_000; s.render(frame).use { it.encodeToData()!!.use { data -> Files.write(p,data.bytes) } }
    }
    @Test fun notesDoNotExpireAndLegacyStoryTabsRestoreToActivity() {
        for(t in listOf(null,"invalid","0",(System.currentTimeMillis()-72*60*60*1000L).toString())) assertTrue(isNoteActive("Catatan",t))
        assertFalse(isNoteActive(" ","0")); assertFalse(isNoteActive(null,"0"))
        assertEquals(FriendsInboxTab.ACTIVITY,notesInboxTab("PUBLIC")); assertEquals(FriendsInboxTab.ACTIVITY,notesInboxTab("invalid"))
        assertEquals(FriendsInboxTab.REQUESTS,notesInboxTab("REQUESTS"))
    }
    @Test fun plainTextCardsHaveIndependentEditAndProfileTargetsOnLargeTextAndDesktop(): Unit = runBlocking(Dispatchers.Swing) {
        for((width,scale) in listOf(390 to 1f,320 to 1.5f,1100 to 1f)) {
            var edits=0;var profiles=0
            val scene=ImageComposeScene(width,900,Density(1f,scale),coroutineContext=coroutineContext) { AppTheme {
                Column { FriendsNoteCard("Alia",null,null,true,{edits++});FriendsNoteCard("Nara",null,"Catatan teman tetap bisa dibaca langsung tanpa membuka pemutar musik.",false,{profiles++}) }
            } }
            try {
                settle(scene)
                val edit=action(scene,"Edit catatan kamu"); val profile=action(scene,"Catatan Nara")
                assertTrue(edit.boundsInRoot.height>=48 && profile.boundsInRoot.height>=48)
                assertTrue(listOf(edit,profile).all { it.boundsInRoot.left>=0 && it.boundsInRoot.right<=width })
                click(scene,"Edit catatan kamu"); assertEquals(1,edits);assertEquals(0,profiles)
                click(scene,"Catatan Nara");assertEquals(1,profiles)
                assertFalse(nodes(scene).any { labels(it).any { t -> t.contains("Story") || t.contains("24 hours") } })
                capture(scene,"notes-$width")
            } finally { scene.close() }
        }
    }
    @Test fun editorSupportsSaveDeleteCancelFailedRetryAndPreventsSavingWhileBusy(): Unit = runBlocking(Dispatchers.Swing) {
        for((width,scale) in listOf(390 to 1f,320 to 1.5f)) {
            var value by mutableStateOf(""); var busy by mutableStateOf(false); var failed by mutableStateOf(false)
            var saved=0; var deleted=0; var closed=0
            val scene=ImageComposeScene(width,900,Density(1f,scale),coroutineContext=coroutineContext) { AppTheme {
                FriendsNoteEditor(value,"Old note",busy,failed,{value=it;failed=false},{saved++},{deleted++},{closed++})
            } }
            try {
                settle(scene);assertTrue(action(scene,"Save").config.contains(SemanticsProperties.Disabled))
                nodes(scene).first { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Catatan baru"))
                settle(scene); click(scene,"Save"); assertEquals(1,saved)
                failed=true;settle(scene);assertEquals("Catatan baru",value);assertTrue(nodes(scene).any { "Your note was not saved. Try again." in labels(it) })
                click(scene,"Save");assertEquals(2,saved)
                busy=true;settle(scene)
                for(label in listOf("Saving…","Delete","Cancel")) assertTrue(action(scene,label).config.contains(SemanticsProperties.Disabled))
                busy=false;settle(scene);capture(scene,"note-editor-$width")
                for(label in listOf("Save","Delete","Cancel")) {
                    val n=action(scene,label);assertTrue(n.boundsInRoot.width>=48 && n.boundsInRoot.height>=48)
                    assertTrue(n.boundsInRoot.left>=0 && n.boundsInRoot.right<=width)
                }
                click(scene,"Delete");assertEquals(1,deleted);click(scene,"Cancel");assertEquals(1,closed)
            } finally { scene.close() }
        }
    }
}
