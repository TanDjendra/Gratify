package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.library.CanonicalSongOrderSheet
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class CanonicalSongOrderTest {
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{tree(it.rootSemanticsNode)}
    private fun find(s:ImageComposeScene,label:String)=nodes(s).first{n->
        n.config.getOrNull(SemanticsProperties.Text)?.any{it.text==label}==true ||
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label)==true}
    private fun button(s:ImageComposeScene,label:String):SemanticsNode {
        var node=find(s,label);while(!node.config.contains(SemanticsActions.OnClick))node=node.parent!!;return node
    }
    private fun click(s:ImageComposeScene,label:String){assertTrue(button(s,label).config[SemanticsActions.OnClick].action!!.invoke())}
    private suspend fun settle(s:ImageComposeScene){repeat(32){s.render(it*16_000_000L).close();delay(16)}}
    private val playlist=PlaylistWorkspace("p","owner","collaborative","Same",member=true,revision=4,
        items=listOf(WorkspaceSong("first","one","First","Artist"),WorkspaceSong("second","two","Second","Artist")))

    @Test fun editingOrderSavesCanonicalItemIdsWithOriginalRevision():Unit=runBlocking(Dispatchers.Swing) {
        var saved:Pair<List<String>,Long>?=null
        val scene=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            CanonicalSongOrderSheet(playlist,false,null,{}, {ids,revision->saved=ids to revision})}}
        try{settle(scene);click(scene,"Turunkan First");settle(scene);click(scene,"Simpan")
            assertEquals(listOf("second","first") to 4L,saved)}finally{scene.close()}
    }

    @Test fun concurrentMemberChangeRequiresReloadBeforeSave():Unit=runBlocking(Dispatchers.Swing) {
        var current by mutableStateOf(playlist);var saved:Pair<List<String>,Long>?=null
        val scene=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            CanonicalSongOrderSheet(current,false,null,{}, {ids,revision->saved=ids to revision})}}
        try{settle(scene);current=playlist.copy(revision=5,items=playlist.items.reversed());settle(scene)
            assertTrue(button(scene,"Simpan").config.contains(SemanticsProperties.Disabled))
            click(scene,"Muat urutan terbaru");settle(scene);click(scene,"Simpan")
            assertEquals(listOf("second","first") to 5L,saved)}finally{scene.close()}
    }
}
