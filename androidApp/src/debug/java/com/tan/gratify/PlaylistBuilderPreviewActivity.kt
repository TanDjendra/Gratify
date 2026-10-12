@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.tan.data.sync.AccountAccess
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.*
import com.tan.domain.repository.*
import com.tan.domain.utils.Resource
import com.tan.domain.utils.LocalResource
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.utils.toSongEntity
import com.tan.gratify.ui.component.CollectionDetailHeader
import com.tan.gratify.ui.component.CollectionDetailAction
import com.tan.gratify.ui.navigation.destination.library.AddSongsToPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.openPlaylistSongPicker
import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.ui.screen.search.SearchCoverArtwork
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.AddSongsToPlaylistViewModel
import kotlinx.coroutines.flow.*
import java.lang.reflect.Proxy

/** Production flow/components with synthetic storage; no account, network, or database writes. */
class PlaylistBuilderPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val titles=listOf("Ruang tenang","Langkah baru","Perjalanan pulang","Pelan-pelan","Hari yang baru")
        val tracks=titles.mapIndexed { index,title -> Track(null,listOf(Artist(null,if(index%2==0) "Senja" else "Ruang Selatan")),"3:00",180,true,false,null,
            listOf(Thumbnail(160,"android.resource://$packageName/${if(index%2==0) R.drawable.home_sample_green else R.drawable.home_sample_coral}",160)),
            title,"preview-$index",null,null,null,null) }
        val addMode=intent.getBooleanExtra("add_mode",false)
        val store=MutableStateFlow(LocalPlaylistEntity(id=1,title="1",tracks=if(addMode) listOf("preview-0")+(1..5).map { "existing-$it" } else emptyList()))
        val songs=fake<SongRepository> { name,_ -> when(name) {
            "getRecentSong" -> tracks.map { it.toSongEntity() }
            "getLikedSongs" -> flowOf(tracks.takeLast(2).map { it.toSongEntity() })
            "getRelatedData" -> flowOf(Resource.Success(tracks to null))
            else -> error(name)
        } }
        val search=fake<SearchRepository> { name,args -> if(name=="getSearchDataSong") flowOf(Resource.Success(tracks.filter {
            it.title.contains(args[0] as String,true) || it.artists.orEmpty().any { artist -> artist.name.contains(args[0] as String,true) }
        }.map { SongsResult(it.album,it.artists,null,it.duration,it.durationSeconds,null,false,"song",it.thumbnails,it.title,it.videoId,null,"") })) else error(name) }
        val playlists=fake<LocalPlaylistRepository> { name,args -> when(name) {
            "getLocalPlaylist" -> store.map { LocalResource.Success(it) }
            "createLocalPlaylistWithSongsForOwner" -> {
                if(intent.getBooleanExtra("save_error",false)) error("synthetic storage failure")
                store.value=(args[0] as LocalPlaylistEntity).copy(id=1,tracks=emptyList()); 1L
            }
            "addTracksToLocalPlaylistForOwner" -> {
                if(intent.getBooleanExtra("save_error",false)) error("synthetic storage failure")
                val ids=(args[1] as List<*>).map { (it as SongEntity).videoId }
                val new=ids.filter { it !in store.value.tracks.orEmpty() }
                store.value=store.value.copy(tracks=store.value.tracks.orEmpty()+new); new.size
            }
            "removeTrackFromLocalPlaylistForOwner" -> {
                if(intent.getBooleanExtra("save_error",false) || intent.getBooleanExtra("remove_error",false)) error("synthetic storage failure")
                val videoId=(args[1] as SongEntity).videoId
                val existed=videoId in store.value.tracks.orEmpty()
                store.value=store.value.copy(tracks=store.value.tracks.orEmpty().filterNot { it==videoId }); existed
            }
            else -> error(name)
        } }
        val access=MutableStateFlow<AccountAccess>(AccountAccess.Ready("preview-owner",false,false))
        fun newModel(target: Long?=null) = AddSongsToPlaylistViewModel(search,songs,playlists,
            SavedStateHandle(if(target==null) emptyMap() else mapOf("targetPlaylistId" to target)), access)
        setContent { AppTheme {
            val navigation=rememberNavController()
            NavHost(navigation,startDestination=if(intent.getBooleanExtra("management",false)) LocalPlaylistDestination(1L) else AddSongsToPlaylistDestination(if(addMode) "1" else "",if(addMode) 1L else null)) {
                composable<AddSongsToPlaylistDestination> { entry ->
                    val route=entry.toRoute<AddSongsToPlaylistDestination>()
                    val model=remember(entry) { newModel(route.targetPlaylistId) }
                    AddSongsToPlaylistScreen(route.playlistTitle,navigation,model,route.targetPlaylistId)
                }
                composable<LocalPlaylistDestination> { entry ->
                    val playlist by store.collectAsState()
                    val model=remember(entry) { newModel(1L) }
                    val state by model.uiState.collectAsState()
                    LaunchedEffect(playlist.tracks) {
                        if (state.pendingTrackIds.isEmpty() && playlist.tracks.orEmpty().size != model.uiState.value.existingTrackIds.size) model.loadTargetPlaylist()
                    }
                    LaunchedEffect(state.membershipChangeCount) { if(state.membershipChangeCount>0) model.loadTargetPlaylist() }
                    var showOptions by remember { mutableStateOf(false) }
                    var showEdit by remember { mutableStateOf(false) }
                    var showVisibility by remember { mutableStateOf(false) }
                    var showDelete by remember { mutableStateOf(false) }
                    var published by remember { mutableStateOf(false) }
                    var editFailed by remember { mutableStateOf(false) }
                    var failedOnce by remember { mutableStateOf(intent.getBooleanExtra("edit_error",false)) }
                    var covers by remember { mutableIntStateOf(0) }
                    var deletes by remember { mutableIntStateOf(0) }
                    if(showOptions) PlaylistOptionsSheet(playlist.title,playlist.tracks.orEmpty().size,published,true,null,
                        {showOptions=false},{showOptions=false;navigation.openPlaylistSongPicker(1L,playlist.title)},
                        {showOptions=false;showEdit=true},{showOptions=false;showVisibility=true},{},{},{},
                        {showOptions=false;showDelete=true})
                    if(showEdit) PlaylistEditDialog(playlist.title,false,editFailed,{ title ->
                        if(failedOnce) { editFailed=true;failedOnce=false }
                        else { store.value=store.value.copy(title=title);showEdit=false;editFailed=false }
                    },{covers++},{showEdit=false})
                    if(showVisibility) PlaylistVisibilityDialog(published,true,false,false,
                        {published=!published;showVisibility=false},{},{showVisibility=false})
                    if(showDelete) PlaylistDeleteDialog(playlist.title,published,{deletes++;showDelete=false},{showDelete=false})
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            CollectionDetailHeader(playlist.title,"Playlist","Tan.","${playlist.tracks.orEmpty().size} songs",
                                GratifyColors.Accent,{navigation.navigateUp()},{},{},false,false,
                                artwork={SearchCoverArtwork(playlist.title,it)},compactPlaylist=true,onEdit={showEdit=true},
                                actions={CollectionDetailAction(Icons.Rounded.MoreHoriz,"Options",onClick={showOptions=true})})
                            OwnedPlaylistVisibility(published,true) {showVisibility=true}
                            OwnedPlaylistAddSongsAction(false,false) { navigation.openPlaylistSongPicker(1L,playlist.title) }
                        }
                        item { PlaylistRecommendationsContent(state,model::toggleTrack,model::loadRecommendations) }
                        item { Text("Preview items: ${playlist.tracks.orEmpty().size} · Cover: $covers · Deleted: $deletes",Modifier.padding(16.dp)) }
                        item { Spacer(Modifier.height(64.dp)) }
                    }
                }
            }
        } }
    }
    private inline fun <reified T> fake(crossinline answer: (String,Array<out Any?>)->Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,method,args ->
            if(method.name=="toString") "PlaylistPreviewRepository" else answer(method.name,args.orEmpty())
        } as T
}
