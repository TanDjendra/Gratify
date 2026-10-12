package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.tan.domain.repository.SongRepository
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class UnifiedPlaylistRenderTest {
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{tree(it.rootSemanticsNode)}
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString{it.text}.orEmpty()
    private fun find(s:ImageComposeScene,vararg labels:String)=nodes(s).first{n->n.config.getOrNull(SemanticsProperties.Text)?.any{it.text in labels}==true || n.config.getOrNull(SemanticsProperties.ContentDescription)?.any{it in labels}==true}
    private fun click(s:ImageComposeScene,vararg labels:String){var n=find(s,*labels);while(!n.config.contains(SemanticsActions.OnClick))n=n.parent!!;assertTrue(n.config[SemanticsActions.OnClick].action!!.invoke())}
    private suspend fun settle(s:ImageComposeScene){repeat(32){s.render(it*16_000_000L).close();delay(16)}}
    private fun shot(s:ImageComposeScene,name:String){
        val root=generateSequence(Path.of("").toAbsolutePath()){it.parent}.first{Files.exists(it.resolve("settings.gradle.kts"))}
        val path=root.resolve("artifacts/playlist-unified-20261012/$name.png");Files.createDirectories(path.parent)
        s.render(3_000_000_000L).use{it.encodeToData()!!.use{d->Files.write(path,d.bytes)}}
    }
    private val songs=listOf(WorkspaceSong("s1","preview-one","Perjalanan pulang","Langit Kota"),WorkspaceSong("s2","preview-two","Ruang tenang","Senja"))
    private fun playlist(member:Boolean=true)=PlaylistWorkspace("p","a","collaborative","Playlist kami No.3",member=member,
        items=songs,members=listOf(WorkspaceMember("a","Tan",owner=true),WorkspaceMember("b","Nadia")))

    @Test fun collaborativeUsesPersonalHeaderTypographyAndWorkingControls():Unit=runBlocking(Dispatchers.Swing){
        var people=0;var edit=0;var play=0;var shuffle=0;var more=0;var back=0;var add=0
        val shared=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            WorkspacePlaylistHeader(playlist(),"a",false,{people++},{edit++},{play++},{add++},
                onBack={back++},onOptions={more++},onShuffle={shuffle++},experimentalEnabled=true,desktop=false)}}
        val personal=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            CollectionDetailHeader(title=playlist().title,kind=stringResource(Res.string.playlist),subtitle="Tan",
                metadata=stringResource(Res.string.album_length,"2",""),accent=playlistTitleGradient(playlist().title).first(),
                onBack={},onPlay={},onShuffle={},isPlaying=false,canPlay=true,compactPlaylist=true,desktop=false,
                onEdit={},artwork={Box(it)})}}
        try{
            settle(shared);settle(personal)
            fun layout(s:ImageComposeScene):TextLayoutResult{val l=mutableListOf<TextLayoutResult>();find(s,playlist().title).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(l);return l.first()}
            assertEquals(layout(personal).layoutInput.style.fontSize,layout(shared).layoutInput.style.fontSize)
            assertEquals(find(personal,playlist().title).boundsInRoot.left,find(shared,playlist().title).boundsInRoot.left)
            click(shared,"Anggota playlist");click(shared,"Ubah","Edit");click(shared,"Putar","Play");click(shared,"Acak","Shuffle")
            click(shared,"Pilihan lainnya");click(shared,"Kembali","Go back");click(shared,"Tambahkan ke playlist ini","Add to this playlist")
            assertEquals(listOf(1,1,1,1,1,1,1),listOf(people,edit,play,shuffle,more,back,add))
            shot(shared,"collaborative-header-390");shot(personal,"personal-header-390")
        }finally{shared.close();personal.close()}
    }

    @Test fun narrowCollaborativeRespectsReadOnlyAndPendingState():Unit=runBlocking(Dispatchers.Swing){
        for((member,busy) in listOf(false to false,true to true)){
            val s=ImageComposeScene(320,850,Density(1f,1.5f),coroutineContext=coroutineContext){AppTheme{
                WorkspacePlaylistHeader(playlist(member).copy(title="Playlist untuk perjalanan pulang bersama teman"),"b",busy,{},{},{},{},experimentalEnabled=true,desktop=false)}}
            try{
                settle(s)
                assertFalse(nodes(s).any{text(it) in listOf("Ubah","Edit","Tambahkan ke playlist ini","Add to this playlist")})
                if(busy)assertTrue(nodes(s).filter{it.config.contains(SemanticsActions.OnClick)}.all{it.config.contains(SemanticsProperties.Disabled)})
                val p=find(s,"Putar","Play");assertTrue(p.boundsInRoot.right<=320)
                shot(s,if(busy)"collaborative-pending-320" else "collaborative-readonly-320")
            }finally{s.close()}
        }
    }

    @Test fun collaborativeRowsUsePersonalMenusWithoutInlineMutationButtons(){
        val repository=Proxy.newProxyInstance(SongRepository::class.java.classLoader,arrayOf(SongRepository::class.java)){_,method,_->
            if(method.name=="getSongAsFlow")flowOf(null) else error("Unexpected repository call: ${method.name}") } as SongRepository
        startKoin{modules(module{single<SongRepository>{repository}})}
        try{runBlocking(Dispatchers.Swing){
            var play=0;var more=0
            val s=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
                LazyColumn{item{WorkspacePlaylistHeader(playlist(),"a",false,{},{},{},{},experimentalEnabled=true,desktop=false)}
                    items(songs.size){i->WorkspaceSongRow(songs[i],false,{play++},{more++},{},desktop=false)}}}}
            try{
                settle(s);shot(s,"collaborative-with-songs-390");click(s,"Perjalanan pulang");click(s,"Opsi lainnya","More options")
                assertEquals(1,play);assertEquals(1,more)
                assertFalse(nodes(s).any{it.config.getOrNull(SemanticsProperties.ContentDescription)?.any{it in listOf("Pindahkan ke atas","Pindahkan ke bawah","Hapus lagu")}==true})
                shot(s,"collaborative-with-songs-390")
            }finally{s.close()}
        }}finally{stopKoin()}
    }
}
