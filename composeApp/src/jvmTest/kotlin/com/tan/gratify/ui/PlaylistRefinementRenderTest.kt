package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaylistRefinementRenderTest {
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{tree(it.rootSemanticsNode)}
    private suspend fun settle(s:ImageComposeScene){repeat(24){s.render(it*16_000_000L).close();delay(16)}}
    private fun shot(s:ImageComposeScene,name:String) {
        val root=generateSequence(Path.of("").toAbsolutePath()){it.parent}.first{Files.exists(it.resolve("settings.gradle.kts"))}
        val path=root.resolve("artifacts/playlist-refinements-20261012/$name.png")
        s.render(1_000_000_000L).use{it.encodeToData()!!.use{data->Files.write(path,data.bytes)}}
    }
    @Test fun collectionCardUsesAutomaticCoverUntilTheOwnerChoosesAnUpload():Unit=runBlocking(Dispatchers.Swing) {
        var upload by mutableStateOf<String?>(null)
        val scene=ImageComposeScene(390,180,Density(1f),coroutineContext=coroutineContext){AppTheme{
            LibraryListItem("Empat lagu","Playlist",Res.drawable.round_library_music_24,
                listOf(GratifyColors.SurfaceRaised,GratifyColors.SurfaceHighest),{},imageUrl=upload,
                automaticImages=(1..4).map{"https://example.invalid/cover-$it.jpg"})}}
        try {
            settle(scene)
            fun automatic()=nodes(scene).any{it.config.getOrNull(SemanticsProperties.ContentDescription)?.any{label->label.contains("4 lagu teratas")}==true}
            assertTrue(automatic());shot(scene,"automatic-cover-card")
            upload="https://example.invalid/owner-upload.jpg";settle(scene)
            assertFalse(automatic(),"Explicit uploaded cover takes priority over automatic artwork")
            upload=null;settle(scene);assertTrue(automatic(),"Removing upload returns to song artwork")
        }finally{scene.close()}
    }
    @Test fun blendSetupRequiresConsentAndAnExplicitInvitation():Unit=runBlocking(Dispatchers.Swing) {
        var consent by mutableStateOf(false);var invites=0
        val scene=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            BlendSetupContent(consent,{consent=it},false,{invites++})}}
        try {
            settle(scene)
            fun invite()=nodes(scene).first{it.config.getOrNull(SemanticsProperties.Text)?.any{t->t.text=="Undang"}==true&&it.config.contains(SemanticsActions.OnClick)}
            assertTrue(invite().config.contains(SemanticsProperties.Disabled));assertEquals(0,invites)
            assertTrue(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Text)?.any{t->t.text.contains("Blend baru muncul di Pustaka")}==true})
            nodes(scene).first{it.config.getOrNull(SemanticsProperties.Role)==Role.Checkbox&&it.config.contains(SemanticsActions.OnClick)}
                .config[SemanticsActions.OnClick].action!!.invoke()
            settle(scene);assertFalse(invite().config.contains(SemanticsProperties.Disabled));assertEquals(0,invites)
            invite().config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,invites)
            shot(scene,"blend-invitation-setup")
        }finally{scene.close()}
    }
}
