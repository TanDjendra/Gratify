package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.AddSongsToPlaylistState
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaylistManagementRenderTest {
    private var frame=0L
    private fun tree(n: SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene)=s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun button(s: ImageComposeScene,label: String)=nodes(s).first { label in texts(it) && it.config.contains(SemanticsActions.OnClick) }
    private fun click(s: ImageComposeScene,label: String)=button(s,label).config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(s: ImageComposeScene) { repeat(20) { frame+=16_000_000; s.render(frame).close(); delay(12) } }

    @Test fun optionsSeparateEditingVisibilityAndYouTubeAndOmitEmptyQueue():Unit=runBlocking(Dispatchers.Swing) {
        for((width,scale) in listOf(390 to 1f,320 to 1.5f)) {
            var edit=0;var visibility=0;var deletion=0
            val s=ImageComposeScene(width,1200,Density(1f,scale),coroutineContext=coroutineContext) { AppTheme {
                PlaylistOptionsContent("Perjalanan pulang",0,false,true,null,{}, {edit++},{visibility++},{},{},{},{deletion++})
            } }
            try {
                settle(s)
                assertFalse(nodes(s).any { "Add to queue" in texts(it) || "Sync first" in texts(it) || "Update playlist from YouTube Music" in texts(it) })
                click(s,"Edit playlist");click(s,"Private playlist");assertEquals(1,edit);assertEquals(1,visibility);assertEquals(0,deletion)
                click(s,"YouTube connection");settle(s)
                assertTrue(nodes(s).any { texts(it).any { t -> t.contains("separate from public visibility") } })
                assertFalse(nodes(s).any { "Update playlist from YouTube Music" in texts(it) })
                val targets=nodes(s).filter { it.config.contains(SemanticsActions.OnClick) }
                targets.forEach { assertTrue(it.boundsInRoot.height>=48 && it.boundsInRoot.left>=0 && it.boundsInRoot.right<=width+1) }
            } finally { s.close() }
        }
    }
    @Test fun editorPreservesDraftOnFailureRejectsBlankAndLocksPending():Unit=runBlocking(Dispatchers.Swing) {
        var saving by mutableStateOf(false);var failed by mutableStateOf(false);var saved="";var covers=0
        val s=ImageComposeScene(320,950,Density(1f,1.5f),coroutineContext=coroutineContext) { AppTheme {
            PlaylistEditDialog("1",saving,failed,{saved=it},{covers++},{})
        } }
        try {
            settle(s)
            assertTrue(button(s,"Save").config.contains(SemanticsProperties.Disabled))
            fun type(t:String)=nodes(s).single { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(t))
            type("   ");settle(s);assertTrue(button(s,"Save").config.contains(SemanticsProperties.Disabled))
            type("  Sore tenang  ");settle(s);click(s,"Save");assertEquals("Sore tenang",saved)
            failed=true;settle(s)
            assertTrue(nodes(s).any { it.config.getOrNull(SemanticsProperties.EditableText)?.text=="  Sore tenang  " })
            assertTrue(nodes(s).any { it.config.contains(SemanticsProperties.LiveRegion) })
            click(s,"Edit thumbnail");assertEquals(1,covers)
            saving=true;settle(s)
            // Bottom sheets expose a scrim action; check the actual editor buttons.
            assertTrue(nodes(s).filter { it.config.contains(SemanticsActions.OnClick)&&it.config.getOrNull(SemanticsProperties.Role)==Role.Button }.all { it.config.contains(SemanticsProperties.Disabled) })
            assertTrue(nodes(s).single { it.config.contains(SemanticsProperties.EditableText) }.config.contains(SemanticsProperties.Disabled))
        } finally { s.close() }
    }
    @Test fun coverOnlyDraftEnablesSaveAndCancelDoesNotCommitIt(): Unit = runBlocking(Dispatchers.Swing) {
        var changed by mutableStateOf(false); var saved=0; var cancelled=0
        val scene=ImageComposeScene(390,900,Density(1f),coroutineContext=coroutineContext) { AppTheme {
            PlaylistEditDialog("Original",false,false,{saved++},{changed=true},{cancelled++},coverChanged=changed)
        } }
        try {
            settle(scene); assertTrue(button(scene,"Save").config.contains(SemanticsProperties.Disabled))
            click(scene,"Edit thumbnail"); settle(scene)
            assertFalse(button(scene,"Save").config.contains(SemanticsProperties.Disabled))
            click(scene,"Cancel"); assertEquals(1,cancelled); assertEquals(0,saved)
            click(scene,"Save"); assertEquals(1,saved)
        } finally { scene.close() }
    }
    @Test fun unknownVisibilityRetriesWithoutPublishingAndDeleteRequiresConfirmation():Unit=runBlocking(Dispatchers.Swing) {
        var known by mutableStateOf(false);var busy by mutableStateOf(false);var retries=0;var changes=0
        val s=ImageComposeScene(390,900,Density(1f),coroutineContext=coroutineContext) { AppTheme {
            PlaylistVisibilityDialog(false,known,busy,false,{changes++},{retries++},{})
        } }
        try {
            settle(s);click(s,"Retry");assertEquals(1,retries);assertEquals(0,changes)
            known=true;settle(s);click(s,"Share to Public");assertEquals(1,changes)
            busy=true;settle(s);assertTrue(nodes(s).filter { it.config.contains(SemanticsActions.OnClick) }.all { it.config.contains(SemanticsProperties.Disabled) })
        } finally { s.close() }
        var deleted=0;var cancelled=0
        val d=ImageComposeScene(320,950,Density(1f,1.5f),coroutineContext=coroutineContext) { AppTheme {
            PlaylistDeleteDialog("1",true,{deleted++},{cancelled++})
        } }
        try {
            settle(d);assertEquals(0,deleted);click(d,"Cancel");assertEquals(1,cancelled);assertEquals(0,deleted)
            assertTrue(nodes(d).any { texts(it).any { t -> t.contains("lose access") } })
            click(d,"Delete");assertEquals(1,deleted)
        } finally { d.close() }
    }
    @Test fun immediatePickerShowsConfirmedTotalAndDone():Unit=runBlocking(Dispatchers.Swing) {
        var closed=0
        val s=ImageComposeScene(390,800,Density(1f),coroutineContext=coroutineContext) { AppTheme {
            PlaylistSongPickerContent(AddSongsToPlaylistState(title="1",targetPlaylistId=1,existingTrackIds=(1..8).map { "$it" }.toSet(),addedCount=2),{},{},{closed++},{},{},{},{})
        } }
        try {
            settle(s);assertTrue(nodes(s).any { "8 songs · 2 added" in texts(it) });click(s,"Done");assertEquals(1,closed)
        } finally { s.close() }
    }
    @Test fun syncedPlaylistKeepsSharingAndRefreshWithinItsYouTubeSection():Unit=runBlocking(Dispatchers.Swing) {
        var shared=0;var refreshed=0;var publicOpened=0
        val s=ImageComposeScene(390,1100,Density(1f),coroutineContext=coroutineContext) { AppTheme {
            PlaylistOptionsContent("1",6,true,true,"VL-real",{},{},{},{},{},{refreshed++},{},
                onOpenPublic={publicOpened++},onShareYouTube={shared++})
        } }
        try {
            settle(s);click(s,"View public playlist");assertEquals(1,publicOpened)
            assertFalse(nodes(s).any { "Share" in texts(it) })
            click(s,"YouTube connection");settle(s)
            click(s,"Share");click(s,"Update playlist from YouTube Music");assertEquals(1,shared);assertEquals(1,refreshed)
            assertTrue(nodes(s).any { "Disconnect YouTube" in texts(it) })
        } finally {s.close()}
    }

}

