package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.PlaylistWorkspaceState
import com.tan.gratify.viewModel.PlaylistWorkspace
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaylistWorkspaceRenderTest {
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(scene:ImageComposeScene)=scene.semanticsOwners.flatMap {tree(it.rootSemanticsNode)}
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString{it.text}.orEmpty()
    private suspend fun settle(scene:ImageComposeScene) {repeat(20){scene.render(it*16_000_000L).close();delay(16)}}
    @Test fun fourCreateChoicesStayAccessibleWithLargeText():Unit=runBlocking(Dispatchers.Swing) {
        for((width,height,scale) in listOf(Triple(390,800,1f),Triple(320,560,1.5f))) {
            var chosen=""
            val scene=ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext){AppTheme {PlaylistCreateOptions(experimentalEnabled=true){chosen=it}}}
            try {
                settle(scene)
                for((label,kind) in listOf("Playlist" to "personal","Playlist kolaboratif" to "collaborative","Blend" to "blend","Folder" to "folder")) {
                    var node=nodes(scene).find{text(it).startsWith(label)&&it.config.contains(SemanticsActions.OnClick)}
                    if(node==null||node.boundsInRoot.bottom>height) {
                        nodes(scene).first{it.config.contains(SemanticsProperties.VerticalScrollAxisRange)}.config[SemanticsActions.ScrollBy].action!!.invoke(0f,10000f)
                        settle(scene);node=nodes(scene).find{text(it).startsWith(label)&&it.config.contains(SemanticsActions.OnClick)}
                    }
                    assertNotNull(node,label);assertTrue(node.boundsInRoot.width>=48&&node.boundsInRoot.height>=48)
                    node.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(kind,chosen)
                }
                val root=generateSequence(Path.of("").toAbsolutePath()){it.parent}.first{Files.exists(it.resolve("settings.gradle.kts"))}
                val path=root.resolve("artifacts/playlist-workspaces-20261012/create-menu-$width.png")
                scene.render(2_000_000_000L).use{it.encodeToData()!!.use{data->Files.write(path,data.bytes)}}
            } finally {scene.close()}
        }
    }
    @Test fun progressDoesNotClaimSuccessAndFailureOffersRetry():Unit=runBlocking(Dispatchers.Swing) {
        val state=mutableStateOf(PlaylistWorkspaceState(busy=true));var retries=0
        val scene=ImageComposeScene(390,800,Density(1f),coroutineContext=coroutineContext){AppTheme{WorkspaceFeedback(state.value){retries++}}}
        try {
            settle(scene);assertTrue(nodes(scene).any{text(it)=="Menyimpan perubahan…"});assertFalse(nodes(scene).any{"berhasil" in text(it)})
            state.value=state.value.copy(busy=false,error="Undangan tidak tersedia.");settle(scene)
            nodes(scene).first{text(it)=="Muat ulang"&&it.config.contains(SemanticsActions.OnClick)}.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1,retries);assertTrue(nodes(scene).any{text(it)=="Undangan tidak tersedia."})
        } finally{scene.close()}
    }
    @Test fun blendConsentStartsUncheckedAndDescribesSharedInformation():Unit=runBlocking(Dispatchers.Swing) {
        val accepted=mutableStateOf(false)
        val scene=ImageComposeScene(320,560,Density(1f,1.5f),coroutineContext=coroutineContext){AppTheme{Column{BlendConsent(accepted.value){accepted.value=it}}}}
        try {
            settle(scene);assertTrue(nodes(scene).any{"riwayat mentah dan isi chat tetap privat" in text(it)})
            val check=nodes(scene).first{it.config.contains(SemanticsProperties.ToggleableState)}
            assertEquals(androidx.compose.ui.state.ToggleableState.Off,check.config[SemanticsProperties.ToggleableState])
            check.config[SemanticsActions.OnClick].action!!.invoke();assertTrue(accepted.value)
        } finally{scene.close()}
    }
    @Test fun coverEditorOffersPickerAndResetAndRetainsFailedDraft():Unit=runBlocking(Dispatchers.Swing) {
        val detail=PlaylistWorkspace("p","a","collaborative","Together",coverPath="old")
        val state=mutableStateOf(PlaylistWorkspaceState(owner="a"));var picks=0;var resets=0;var saves=0
        val scene=ImageComposeScene(390,800,Density(1f),coroutineContext=coroutineContext){AppTheme{
            WorkspaceEditDialog(detail,"Together",{},state.value,"local-cropped.jpg",false,false,{picks++},{resets++},{},{}){_,_->saves++}
        }}
        try {
            settle(scene)
            fun button(label:String)=nodes(scene).first{text(it)==label&&it.config.contains(SemanticsActions.OnClick)}
            button("Pilih foto sampul").config[SemanticsActions.OnClick].action!!.invoke();settle(scene)
            button("Ubah gambar sampul").config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,picks);settle(scene)
            button("Gunakan sampul otomatis").config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,resets)
            state.value=state.value.copy(error="Sampul belum tersimpan di server.");settle(scene)
            assertTrue(nodes(scene).any{text(it)=="Sampul belum tersimpan di server."})
            button("Simpan").config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,saves)
            state.value=state.value.copy(busy=true,error=null,busyMessage="Mengunggah sampul…");settle(scene)
            assertTrue(nodes(scene).any{text(it)=="Mengunggah sampul…"})
            assertTrue(button("Pilih foto sampul").config.contains(SemanticsProperties.Disabled))
            assertTrue(button("Menyimpan…").config.contains(SemanticsProperties.Disabled))
        }finally{scene.close()}
    }
    @Test fun coverEditorIsScrollableWithLargeTextAndKeyboardSpace():Unit=runBlocking(Dispatchers.Swing) {
        val detail=PlaylistWorkspace("p","a","blend","Blend kami")
        val scene=ImageComposeScene(320,560,Density(1f,1.5f),coroutineContext=coroutineContext){AppTheme{
            WorkspaceEditDialog(detail,"Blend kami",{},PlaylistWorkspaceState(owner="a"),null,false,false,{},{},{},{}){_,_->}
        }}
        try {
            settle(scene)
            assertTrue(nodes(scene).any{it.config.contains(SemanticsProperties.VerticalScrollAxisRange)})
            val save=nodes(scene).first{text(it)=="Simpan"&&it.config.contains(SemanticsActions.OnClick)}
            assertTrue(save.boundsInRoot.bottom<=560);assertFalse(save.config.contains(SemanticsProperties.Disabled))
            assertFalse(nodes(scene).any{text(it)=="Publik di Search"})
            val root=generateSequence(Path.of("").toAbsolutePath()){it.parent}.first{Files.exists(it.resolve("settings.gradle.kts"))}
            scene.render(2_000_000_000L).use{it.encodeToData()!!.use{data->Files.write(root.resolve("artifacts/playlist-workspaces-20261012/shared-cover-editor-320.png"),data.bytes)}}
        }finally{scene.close()}
    }
}
