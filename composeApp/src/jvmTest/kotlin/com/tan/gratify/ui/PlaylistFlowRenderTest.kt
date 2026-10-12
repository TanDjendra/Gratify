package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.utils.toSongEntity
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.AddSongsToPlaylistState
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaylistFlowRenderTest {
    private fun track(id: Int) = Track(null,null,"3:00",180,true,false,null,null,"Song $id","song-$id",null,null,null,null)
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene)=s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun text(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private fun desc(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private suspend fun settle(s: ImageComposeScene,start: Long=0) { repeat(20) { s.render(start+it*16_000_000L).close(); delay(16) } }
    private fun click(n: SemanticsNode)=n.config[SemanticsActions.OnClick].action!!.invoke()
    private fun screenshot(s: ImageComposeScene,name: String) {
        val root=generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path=root.resolve("artifacts/playlist-cache-crop-20261011/$name.png"); Files.createDirectories(path.parent)
        s.render(7_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path,data.bytes) } }
    }
    @Test fun nameFirstCreationHasOneActionAndNoSongSelectionEvenWithKeyboardSpace(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width,height,scale) in listOf(Triple(390,800,1f),Triple(320,420,1.5f),Triple(1100,800,1f))) {
            val state=mutableStateOf(AddSongsToPlaylistState()); var creates=0
            val scene=ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { CreatePlaylistContent(state.value,{state.value=state.value.copy(title=it)},{creates++},{}) }
            }
            try {
                settle(scene)
                fun create()=nodes(scene).first { text(it)=="Create playlist" && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(create().config.contains(SemanticsProperties.Disabled))
                assertFalse(nodes(scene).any { text(it)=="Pick songs" || text(it)?.startsWith("Arrange")==true })
                val field=nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
                field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("1")); settle(scene,1_000_000_000L)
                assertTrue(create().boundsInRoot.bottom<=height); assertTrue(create().boundsInRoot.height>=48)
                click(create()); assertEquals(1,creates)
                screenshot(scene,"create-$width-$height")
            } finally { scene.close() }
        }
    }
    @Test fun pickerPersistsThroughIndividualPlusAndOffersRealSourceFiltersAndBottomSearch(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width,height,scale) in listOf(Triple(390,800,1f),Triple(320,560,1.5f),Triple(1100,800,1f))) {
            val state=mutableStateOf(AddSongsToPlaylistState(title="1",targetPlaylistId=1,
                existingTrackIds=setOf("song-1"),recommendations=listOf(track(1),track(2)),
                recentSongs=listOf(track(3).toSongEntity()),likedSongs=listOf(track(4).toSongEntity())))
            val adds=mutableListOf<String>(); var closes=0
            val scene=ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { PlaylistSongPickerContent(state.value,{state.value=state.value.copy(searchQuery=it)},
                    {adds+=it.videoId;state.value=state.value.copy(existingTrackIds=if(it.videoId in state.value.existingTrackIds)
                        state.value.existingTrackIds-it.videoId else state.value.existingTrackIds+it.videoId)},
                    {closes++},{},{},{},{}) }
            }
            try {
                settle(scene)
                assertFalse(nodes(scene).any { text(it)=="Create playlist" || text(it)=="Pick songs" })
                val old=nodes(scene).first { desc(it)=="Already in this playlist" }
                assertTrue(old.config.contains(SemanticsProperties.Disabled))
                assertEquals(androidx.compose.ui.state.ToggleableState.On,old.config.getOrNull(SemanticsProperties.ToggleableState))
                val plus=nodes(scene).first { desc(it)=="Add Song 2" }
                assertTrue(plus.boundsInRoot.width>=48 && plus.boundsInRoot.height>=48)
                click(plus); settle(scene,1_000_000_000L)
                assertEquals(listOf("song-2"),adds)
                val checked=nodes(scene).filter { desc(it)=="Already in this playlist" }
                assertEquals(2,checked.size)
                assertTrue(checked.all { it.config.contains(SemanticsProperties.Disabled) })
                assertEquals(listOf("song-2"),adds)
                assertTrue(nodes(scene).any { text(it)=="Done" && !it.config.contains(SemanticsProperties.Disabled) })
                val field=nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
                assertTrue(field.boundsInRoot.top>height/2 && field.boundsInRoot.bottom<=height)
                screenshot(scene,"picker-$width-$height")
                click(nodes(scene).first { text(it)=="Recently played" && it.config.contains(SemanticsActions.OnClick) }); settle(scene,2_000_000_000L)
                assertTrue(nodes(scene).any { desc(it)=="Add Song 3" })
                // The liked chip can be off-screen on narrow devices; expose it via horizontal scrolling.
                nodes(scene).first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(10000f,0f); settle(scene,3_000_000_000L)
                click(nodes(scene).first { text(it)=="Liked songs" && it.config.contains(SemanticsActions.OnClick) }); settle(scene,4_000_000_000L)
                assertTrue(nodes(scene).any { desc(it)=="Add Song 4" })
            } finally { scene.close() }
        }
    }
    @Test fun pendingFailedAndAccountChangedStatesPreventFalseAddedFeedback(): Unit = runBlocking(Dispatchers.Swing) {
        val state=mutableStateOf(AddSongsToPlaylistState(title="1",targetPlaylistId=1,recommendations=listOf(track(2)),pendingTrackIds=setOf("song-2")))
        val scene=ImageComposeScene(390,800,Density(1f),coroutineContext=coroutineContext) {
            AppTheme { PlaylistSongPickerContent(state.value,{},{},{},{},{},{},{}) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && desc(it)=="Back" }.all { it.config.contains(SemanticsProperties.Disabled) })
            assertFalse(nodes(scene).any { desc(it)=="Remove Song 2" })
            state.value=state.value.copy(pendingTrackIds=emptySet(),failedTrackIds=setOf("song-2")); settle(scene,1_000_000_000L)
            assertTrue(nodes(scene).any { text(it)=="Could not add this song. Tap + to retry." })
            assertFalse(nodes(scene).first { desc(it)=="Add Song 2" }.config.contains(SemanticsProperties.Disabled))
            state.value=state.value.copy(ownerChanged=true); settle(scene,2_000_000_000L)
            assertTrue(nodes(scene).any { text(it)=="Your account changed" })
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) && !it.config.contains(SemanticsProperties.Disabled) })
        } finally { scene.close() }
    }
}
