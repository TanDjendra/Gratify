package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.utils.toSongEntity
import com.tan.gratify.ui.screen.library.PlaylistBuilderContent
import com.tan.gratify.ui.screen.library.OwnedPlaylistAddSongsAction
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.AddSongsToPlaylistState
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaylistBuilderRenderTest {
    private fun track(id: Int) = Track(null,null,"3:00",180,true,false,null,null,"Lagu pilihan $id","song-$id",null,null,null,null)
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene)=s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun text(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private fun desc(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private suspend fun settle(s: ImageComposeScene,start: Long=0) { repeat(22) { s.render(start+it*16_000_000L).close(); delay(16) } }
    private fun click(n: SemanticsNode)=n.config[SemanticsActions.OnClick].action!!.invoke()
    private fun screenshot(s: ImageComposeScene,name: String, directory: String = "playlist-composer-2026-10-08") {
        val root=generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path=root.resolve("artifacts/$directory/$name.png"); Files.createDirectories(path.parent)
        s.render(7_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path,data.bytes) } }
    }
    @Test fun selectionNameAndCreateActionsWorkWithLargeTextSmallKeyboardSpaceAndDesktop(): Unit = runBlocking(Dispatchers.Swing) {
        for((width,height,scale) in listOf(Triple(390,900,1f),Triple(320,560,1.5f),Triple(1100,900,1f))) {
            val state=mutableStateOf(AddSongsToPlaylistState(title="Perjalanan pulang",recentSongs=(1..20).map { track(it).toSongEntity() }))
            var saved=0; var closed=0
            val scene=ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { PlaylistBuilderContent(state.value,{state.value=state.value.copy(title=it)},
                    {state.value=state.value.copy(searchQuery=it)}, {state.value=state.value.copy(selectedTracks=state.value.selectedTracks+it)},
                    {id -> state.value=state.value.copy(selectedTracks=state.value.selectedTracks.filter { it.videoId!=id })},{saved++},{closed++},{}) }
            }
            try {
                settle(scene)
                val create=nodes(scene).first { text(it)=="Create playlist" && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(create.boundsInRoot.bottom<=height); assertTrue(create.boundsInRoot.top>=0)
                click(create); assertEquals(1,saved)
                val name=nodes(scene).first { it.config.getOrNull(SemanticsProperties.EditableText)?.text==state.value.title }
                name.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("x".repeat(101))); assertEquals("Perjalanan pulang",state.value.title)
                if(height==900) {
                    click(nodes(scene).first { desc(it)=="Add Lagu pilihan 1" }); settle(scene,1_000_000_000L)
                    assertEquals(1,state.value.selectedTracks.size)
                    click(nodes(scene).first { desc(it)=="Remove Lagu pilihan 1" }); settle(scene,2_000_000_000L)
                    assertTrue(state.value.selectedTracks.isEmpty())
                }
                if(width==1100) assertTrue(name.boundsInRoot.left>=170 && name.boundsInRoot.right<=930)
                screenshot(scene,"builder-$width-$height")
                nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) && it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(0f,10000f)
                settle(scene,3_000_000_000L)
                val last=nodes(scene).first { desc(it)=="Add Lagu pilihan 20" }
                assertTrue(last.boundsInRoot.bottom<=create.boundsInRoot.top,"Last song is covered by the save area")
            } finally { scene.close() }
        }
    }
    @Test fun existingPlaylistPickerShowsMembershipAndAddsOnlyNewSelectionsWithLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width,height,scale) in listOf(Triple(390,900,1f),Triple(320,560,1.5f),Triple(1100,900,1f))) {
            val state=mutableStateOf(AddSongsToPlaylistState(title="1",targetPlaylistId=1,existingTrackIds=(1..6).map { "song-$it" }.toSet(),
                recentSongs=listOf(track(1),track(7)).map { it.toSongEntity() }))
            var saves=0
            val scene=ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { PlaylistBuilderContent(state.value,{}, {},{state.value=state.value.copy(selectedTracks=state.value.selectedTracks+it)},
                    {},{saves++},{},{}) }
            }
            try {
                settle(scene)
                val add=nodes(scene).first { text(it)=="Add songs" && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(add.config.contains(SemanticsProperties.Disabled)); assertTrue(add.boundsInRoot.height>=48)
                assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.EditableText)?.text=="1" })
                if(height<900) {
                    nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) }.config[SemanticsActions.ScrollBy].action!!.invoke(0f,1000f)
                    settle(scene,500_000_000L)
                }
                assertTrue(nodes(scene).first { desc(it)=="Already in playlist: Lagu pilihan 1" }.config.contains(SemanticsProperties.Disabled))
                click(nodes(scene).first { desc(it)=="Add Lagu pilihan 7" }); settle(scene,1_000_000_000L)
                assertEquals(listOf("song-7"),state.value.selectedTracks.map { it.videoId })
                val save=nodes(scene).first { text(it)=="Add songs" && it.config.contains(SemanticsActions.OnClick) }
                assertFalse(save.config.contains(SemanticsProperties.Disabled)); click(save); assertEquals(1,saves)
                assertTrue(save.boundsInRoot.bottom<=height)
                screenshot(scene,"picker-$width-$height","playlist-additions-2026-10-09")
            } finally { scene.close() }
        }
    }
    @Test fun addButtonIsVisibleAndAccessibleOnlyOnAvailableOwnedPlaylists(): Unit=runBlocking(Dispatchers.Swing) {
        val readOnly=mutableStateOf(false); val unavailable=mutableStateOf(false); var clicks=0
        val scene=ImageComposeScene(320,200,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { OwnedPlaylistAddSongsAction(readOnly.value,unavailable.value) { clicks++ } }
        }
        try {
            settle(scene); val button=nodes(scene).first { it.config.contains(SemanticsActions.OnClick) }
            assertTrue(button.boundsInRoot.height>=48); click(button); assertEquals(1,clicks)
            readOnly.value=true; settle(scene,1_000_000_000L); assertFalse(nodes(scene).any { text(it)=="Add songs" })
            readOnly.value=false; unavailable.value=true; settle(scene,2_000_000_000L); assertFalse(nodes(scene).any { text(it)=="Add songs" })
        } finally { scene.close() }
    }
    @Test fun arrangementControlsWorkOnNarrowLargeTextAndDesktopAndKeepSearchSeparate(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, height, scale) in listOf(Triple(390,900,1f), Triple(320,560,1.5f), Triple(1100,900,1f))) {
            val state = mutableStateOf(AddSongsToPlaylistState(title="", selectedTracks=(1..3).map(::track), searchQuery="retained"))
            val scene = ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { PlaylistBuilderContent(state.value, {state.value=state.value.copy(title=it)}, {}, {},
                    { id -> state.value=state.value.copy(selectedTracks=state.value.selectedTracks.filter { it.videoId!=id }) }, {}, {}, {},
                    { id, offset ->
                        val tracks=state.value.selectedTracks.toMutableList(); val index=tracks.indexOfFirst { it.videoId==id }
                        tracks.add(index+offset,tracks.removeAt(index)); state.value=state.value.copy(selectedTracks=tracks)
                    }) }
            }
            try {
                settle(scene)
                fun scroll(amount: Float) { nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(0f,amount) }
                if (height<900) { scroll(210f); settle(scene,500_000_000L) }
                click(nodes(scene).first { text(it)=="Arrange · 3" && it.config.contains(SemanticsActions.OnClick) })
                settle(scene,1_000_000_000L)
                if (height<900) { scroll(240f); settle(scene,1_500_000_000L) }
                assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.EditableText)?.text=="retained" })
                val up=nodes(scene).first { desc(it)=="Move Lagu pilihan 1 up" }
                assertTrue(up.config.contains(SemanticsProperties.Disabled))
                val down=nodes(scene).first { desc(it)=="Move Lagu pilihan 1 down" }
                assertTrue(down.boundsInRoot.width>=48 && down.boundsInRoot.height>=48)
                click(down); settle(scene,2_000_000_000L)
                assertEquals(listOf("song-2","song-1","song-3"),state.value.selectedTracks.map { it.videoId })
                screenshot(scene,"arrange-$width-$height")
                state.value=state.value.copy(isSaving=true); settle(scene,8_000_000_000L)
                assertTrue(nodes(scene).filter { desc(it)?.startsWith("Move ")==true }.all { it.config.contains(SemanticsProperties.Disabled) })
                state.value=state.value.copy(isSaving=false,selectedTracks=emptyList()); settle(scene,9_000_000_000L)
                scroll(-10000f); settle(scene,10_000_000_000L)
                if (height<900) { scroll(10000f); settle(scene,11_000_000_000L) }
                assertTrue(nodes(scene).any { text(it)=="Your playlist starts here" })
            } finally { scene.close() }
        }
    }

    @Test fun noMatchesAndFailureNeverPretendToBeRecommendationsAndOwnerChangeHidesDraft(): Unit = runBlocking(Dispatchers.Swing) {
        val state=mutableStateOf(AddSongsToPlaylistState(title="Pilihan sore",searchQuery="nothing",recentSongs=listOf(track(1).toSongEntity())))
        var retries=0
        val scene=ImageComposeScene(390,900,Density(1f),coroutineContext=coroutineContext) {
            AppTheme { PlaylistBuilderContent(state.value,{}, {retries++},{},{},{},{},{}) }
        }
        try {
            settle(scene); assertTrue(nodes(scene).any { text(it)=="No matching songs" })
            assertFalse(nodes(scene).any { desc(it)=="Add Lagu pilihan 1" })
            state.value=state.value.copy(searchError=true,saveError="disk full"); settle(scene,1_000_000_000L)
            assertTrue(nodes(scene).any { text(it)=="Could not search for songs" })
            click(nodes(scene).first { text(it)=="Retry" && it.config.contains(SemanticsActions.OnClick) }); assertEquals(1,retries)
            assertTrue(nodes(scene).any { text(it)?.contains("Your selections are still here")==true }); screenshot(scene,"builder-error")
            state.value=state.value.copy(isSearching=true,searchError=false,saveError=null); settle(scene,2_000_000_000L)
            assertTrue(nodes(scene).any { desc(it)=="Loading songs" }); assertFalse(nodes(scene).any { text(it)=="No matching songs" })
            state.value=state.value.copy(ownerChanged=true); settle(scene,3_000_000_000L)
            assertTrue(nodes(scene).any { text(it)=="Your account changed" })
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) }); screenshot(scene,"builder-account-changed")
        } finally { scene.close() }
    }
    @Test fun savingDisablesSelectionAndBlankNameCannotCreate(): Unit = runBlocking(Dispatchers.Swing) {
        val state=mutableStateOf(AddSongsToPlaylistState(title="",recentSongs=listOf(track(1).toSongEntity())))
        val scene=ImageComposeScene(390,900,Density(1f),coroutineContext=coroutineContext) {
            AppTheme { PlaylistBuilderContent(state.value,{}, {},{},{},{},{},{}) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).first { text(it)=="Create playlist" && it.config.contains(SemanticsActions.OnClick) }.config.contains(SemanticsProperties.Disabled))
            state.value=state.value.copy(title="Sore",isSaving=true); settle(scene,1_000_000_000L)
            assertTrue(nodes(scene).first { desc(it)=="Add Lagu pilihan 1" }.config.contains(SemanticsProperties.Disabled))
            assertTrue(nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.all { it.config.contains(SemanticsProperties.Disabled) })
            screenshot(scene,"builder-saving")
        } finally { scene.close() }
    }
}
