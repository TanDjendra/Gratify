package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WorkspaceReferenceRenderTest {
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{tree(it.rootSemanticsNode)}
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString{it.text}.orEmpty()
    private suspend fun settle(s:ImageComposeScene){repeat(28){s.render(it*16_000_000L).close();delay(16)}}
    private fun shot(s:ImageComposeScene,name:String){
        val root=generateSequence(Path.of("").toAbsolutePath()){it.parent}.first{Files.exists(it.resolve("settings.gradle.kts"))}
        val path=root.resolve("artifacts/playlist-reference-20261012/$name.png");Files.createDirectories(path.parent)
        s.render(3_000_000_000L).use{it.encodeToData()!!.use{d->Files.write(path,d.bytes)}}
    }
    @Test fun blendStartsWithInvitationAndRequiresConsent():Unit=runBlocking(Dispatchers.Swing){
        for((w,h,scale)in listOf(Triple(390,850,1f),Triple(320,560,1.5f))){
            val consent=mutableStateOf(false);var invited=0
            val s=ImageComposeScene(w,h,Density(1f,scale),coroutineContext=coroutineContext){AppTheme{
                BlendSetupContent(consent.value,{consent.value=it},false,{invited++})}}
            try{
                settle(s);assertFalse(nodes(s).any{it.config.contains(SemanticsProperties.EditableText)})
                fun invite()=nodes(s).first{text(it)=="Undang"&&it.config.contains(SemanticsActions.OnClick)}
                assertTrue(invite().config.contains(SemanticsProperties.Disabled))
                nodes(s).first{it.config.contains(SemanticsProperties.ToggleableState)}.config[SemanticsActions.OnClick].action!!.invoke();settle(s)
                assertFalse(invite().config.contains(SemanticsProperties.Disabled))
                if(invite().boundsInRoot.bottom>h){nodes(s).first{it.config.contains(SemanticsProperties.VerticalScrollAxisRange)}.config[SemanticsActions.ScrollBy].action!!.invoke(0f,10000f);settle(s)}
                assertTrue(invite().boundsInRoot.bottom<=h,"$w invite bottom ${invite().boundsInRoot}");assertTrue(invite().boundsInRoot.height>=48,"$w invite touch bounds ${invite().boundsInRoot}")
                invite().config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,invited)
                shot(s,"blend-setup-$w")
            }finally{s.close()}
        }
    }
    @Test fun namingSelectsDefaultAndKeepsCreationAccessible():Unit=runBlocking(Dispatchers.Swing){
        for((w,h,scale)in listOf(Triple(390,850,1f),Triple(320,560,1.5f))){
            val title=mutableStateOf("Playlist kami No.3");var created=0
            val s=ImageComposeScene(w,h,Density(1f,scale),coroutineContext=coroutineContext){AppTheme{
                PlaylistNameEntry("Beri nama playlist kalian",title.value,{title.value=it},true,false,{created++},{})}}
            try{
                settle(s);val field=nodes(s).first{it.config.contains(SemanticsProperties.EditableText)}
                assertEquals(TextRange(0,title.value.length),field.config[SemanticsProperties.TextSelectionRange])
                val create=nodes(s).first{text(it)=="Buat"&&it.config.contains(SemanticsActions.OnClick)}
                assertTrue(create.boundsInRoot.bottom<=h);create.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,created)
                field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(""));settle(s)
                assertTrue(nodes(s).first{text(it)=="Buat"&&it.config.contains(SemanticsActions.OnClick)}.config.contains(SemanticsProperties.Disabled))
                field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Playlist kami No.3"));settle(s);shot(s,"collaborative-name-$w")
            }finally{s.close()}
        }
    }
    @Test fun rapidNameChangesKeepCompleteTextAndCursor():Unit=runBlocking(Dispatchers.Swing){
        val title=mutableStateOf("Playlist kami No.3")
        val s=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            PlaylistNameEntry("Beri nama playlist kalian",title.value,{title.value=it},true,false,{},{})}}
        try{
            settle(s)
            val expected="GRATIFY_UI_20261012_COLLAB"
            for(i in 1..expected.length){
                nodes(s).first{it.config.contains(SemanticsProperties.EditableText)}.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(expected.take(i)))
                s.render(i*16_000_000L).close()
            }
            settle(s)
            val field=nodes(s).first{it.config.contains(SemanticsProperties.EditableText)}
            assertEquals(expected,field.config[SemanticsProperties.EditableText].text)
            assertEquals(expected,title.value)
            assertEquals(TextRange(expected.length),field.config[SemanticsProperties.TextSelectionRange])
        }finally{s.close()}
    }
    @Test fun blendHeaderDoesNotOfferManualSongEditing():Unit=runBlocking(Dispatchers.Swing){
        for(kind in listOf("blend","collaborative")){
            val p=PlaylistWorkspace("p","a",kind,"Playlist kami No.3",member=true,members=listOf(WorkspaceMember("a","Tan",owner=true),WorkspaceMember("b","Nadia")))
            var people=0
            val s=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
                WorkspacePlaylistHeader(p,"a",false,{people++},{},{},{},experimentalEnabled=true,desktop=false)}}
            try{
                settle(s);assertEquals(kind=="collaborative",nodes(s).any{text(it) in listOf("Tambahkan ke playlist ini","Add to this playlist")})
                val group=nodes(s).first{it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Anggota playlist")==true&&it.config.contains(SemanticsActions.OnClick)}
                group.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,people);shot(s,"$kind-header-390")
            }finally{s.close()}
        }
    }
    @Test fun coverChoicesStayReachableOnSmallScreens():Unit=runBlocking(Dispatchers.Swing){
        for((w,h,scale)in listOf(Triple(390,850,1f),Triple(320,560,1.5f))){
            var picked=0
            val s=ImageComposeScene(w,h,Density(1f,scale),coroutineContext=coroutineContext){AppTheme{
                WorkspaceCoverChooser(PlaylistWorkspace("p","a","collaborative","Playlist kami"),"a",null,false,false,{picked++},{},{})}}
            try{
                settle(s)
                fun pick()=nodes(s).first{text(it)=="Ubah gambar sampul"&&it.config.contains(SemanticsActions.OnClick)}
                if(pick().boundsInRoot.bottom>h){
                    nodes(s).first{it.config.contains(SemanticsProperties.VerticalScrollAxisRange)}.config[SemanticsActions.ScrollBy].action!!.invoke(0f,10000f);settle(s)
                }
                assertTrue(pick().boundsInRoot.top>=0&&pick().boundsInRoot.bottom<=h,"$w cover picker ${pick().boundsInRoot}")
                assertTrue(pick().boundsInRoot.height>=48)
                pick().config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,picked)
                shot(s,"cover-chooser-$w")
            }finally{s.close()}
        }
    }
    @Test fun inviteCardShowsRealWorkspaceWithoutFabricatedTasteScore():Unit=runBlocking(Dispatchers.Swing){
        val p=PlaylistWorkspace("p","a","collaborative","Playlist kami No.3")
        val s=ImageComposeScene(390,360,Density(1f),coroutineContext=coroutineContext){AppTheme{WorkspaceInviteCard(p,"a")}}
        try{settle(s);assertTrue(nodes(s).any{text(it)==p.title});assertTrue(nodes(s).any{text(it)=="Gratify"});assertFalse(nodes(s).any{'%' in text(it)});shot(s,"invitation-card-390")}finally{s.close()}
    }
}
