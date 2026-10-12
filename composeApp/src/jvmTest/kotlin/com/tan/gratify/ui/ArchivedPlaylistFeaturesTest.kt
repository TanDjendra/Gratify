package com.tan.gratify.ui

import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.domain.utils.PlaylistFeaturePolicy
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ArchivedPlaylistFeaturesTest {
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{tree(it.rootSemanticsNode)}
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString{it.text}.orEmpty()
    private suspend fun settle(s:ImageComposeScene){repeat(25){s.render(it*16_000_000L).close();delay(12)}}
    private fun click(s:ImageComposeScene,label:String){var n=nodes(s).first{text(it).startsWith(label)};while(!n.config.contains(SemanticsActions.OnClick))n=n.parent!!;assertTrue(n.config[SemanticsActions.OnClick].action!!.invoke())}

    @Test fun releaseCreationOffersOnlyOrdinaryPlaylist():Unit=runBlocking(Dispatchers.Swing) {
        var selected=""
        val s=ImageComposeScene(320,560,Density(1f,1.5f),coroutineContext=coroutineContext){AppTheme{PlaylistCreateOptions{selected=it}}}
        try{settle(s);assertFalse(PlaylistFeaturePolicy.experimentalEnabled)
            assertFalse(nodes(s).any{text(it) in listOf("Playlist kolaboratif","Blend","Folder")})
            click(s,"Playlist");assertEquals("personal",selected)
        }finally{s.close()}
    }
    @Test fun existingCanonicalPlaylistKeepsOrdinaryControlsWithoutInvitations():Unit=runBlocking(Dispatchers.Swing) {
        var added=0;var played=0;var edited=0
        val p=PlaylistWorkspace("same","owner","collaborative","Existing playlist",member=true,
            members=listOf(WorkspaceMember("owner","Tan",owner=true),WorkspaceMember("member","Nadia")),
            items=listOf(WorkspaceSong("item","video","Track","Artist")))
        val s=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            WorkspacePlaylistHeader(p,"owner",false,{}, {edited++},{played++},{added++},desktop=false)}}
        try{settle(s)
            assertFalse(nodes(s).any{it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Undang kolaborator")==true})
            assertTrue(nodes(s).filter{it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Anggota playlist")==true}.all{it.config.contains(SemanticsProperties.Disabled)||!it.config.contains(SemanticsActions.OnClick)})
            click(s,nodes(s).first{text(it) in listOf("Ubah","Edit")}.let(::text))
            click(s,nodes(s).first{text(it) in listOf("Tambahkan ke playlist ini","Add to this playlist")}.let(::text))
            assertEquals(1,edited);assertEquals(1,added)
        }finally{s.close()}
    }
    @Test fun retainedInvitationRouteCannotOfferJoin():Unit=runBlocking(Dispatchers.Swing) {
        var back=0
        val s=ImageComposeScene(320,560,Density(1f,1.5f),coroutineContext=coroutineContext){AppTheme{ArchivedPlaylistFeatureScreen{back++}}}
        try{settle(s);assertTrue(nodes(s).any{text(it)==PlaylistFeaturePolicy.archivedMessage})
            assertFalse(nodes(s).any{text(it) in listOf("Gabung","Undang","Buat Blend")})
            click(s,"Kembali");assertEquals(1,back)
        }finally{s.close()}
    }
}
