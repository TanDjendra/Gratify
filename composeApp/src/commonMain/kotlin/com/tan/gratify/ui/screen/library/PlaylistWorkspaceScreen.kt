package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.common.Config
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.utils.toTrack
import com.tan.gratify.expect.shareUrl
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.component.rememberBusyModalSheetState
import com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.openMainTab
import com.tan.gratify.ui.navigation.destination.library.LibraryDestination
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import com.tan.gratify.viewModel.UIEvent
import kotlinx.serialization.json.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import java.util.UUID

internal fun LocalPlaylistEntity.folderReference(): String? = canonicalPlaylistId?.let { "workspace:$it" }
    ?:sourceSharedPlaylistId?.let { "shared:$it" }
    ?: syncId?.let { "local:$it" }
internal fun WorkspaceSong.playbackTrack() = Track(null,listOf(Artist(id=null,name=artist)),null,duration,true,false,null,
    listOf(Thumbnail(url=thumbnail ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",width=320,height=180)),title,videoId,"MUSIC_VIDEO_TYPE_ATV",null,null,"song",null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistCreateMenu(onDismiss: () -> Unit, showPersonal:Boolean=true, experimentalEnabled:Boolean=com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled, onChoose: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GratifyColors.Surface) {
        PlaylistCreateOptions(showPersonal,experimentalEnabled,onChoose)
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
@Composable internal fun PlaylistCreateOptions(showPersonal:Boolean=true,experimentalEnabled:Boolean=com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled,onChoose: (String)->Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=560.dp)) {
        listOf(Triple("personal","Playlist","Susun lagu pilihanmu"),
            Triple("collaborative","Playlist kolaboratif","Buat dan kelola lagu bersama teman"),
            Triple("blend","Blend","Satukan selera musik teman-temanmu"),
            Triple("folder","Folder","Atur playlist-mu")).filter{(showPersonal||it.first!="personal")&&(experimentalEnabled||it.first=="personal")}.forEach { (kind,title,hint) ->
            item(kind) { ListItem(headlineContent = { Text(title,fontWeight=FontWeight.Bold) }, supportingContent = { Text(hint) },
                leadingContent = { WorkspaceRoundIcon(when(kind) { "collaborative" -> Icons.Rounded.Group; "blend" -> Icons.Rounded.JoinInner; "folder" -> Icons.Rounded.Folder; else -> Icons.Rounded.MusicNote }, null) },
                modifier = Modifier.clickable { onChoose(kind) }.heightIn(min=92.dp),
                colors = ListItemDefaults.colors(containerColor = GratifyColors.Surface)) }
        }
    }
}

@Composable
internal fun WorkspaceFeedback(state: PlaylistWorkspaceState, onRetry: () -> Unit) {
    if (state.loading || state.busy) Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
        Text(if(state.busy) state.busyMessage else "Memperbarui…")
    }
    state.error?.let { Column(Modifier.padding(16.dp).semantics { liveRegion=LiveRegionMode.Polite }) { Text(it,color=GratifyColors.Error); TextButton(onClick=onRetry,enabled=!state.busy) { Text("Muat ulang") } } }
    state.success?.let { Text(it,Modifier.padding(horizontal=16.dp,vertical=8.dp),color=GratifyColors.Accent) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaylistWorkspaceScreen(route: PlaylistWorkspaceDestination, nav: NavController,
    model: PlaylistWorkspaceViewModel = koinViewModel(), player: SharedViewModel = koinInject(),localCacheId:Long?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    val experimentalEnabled=com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled
    if(!com.tan.domain.utils.PlaylistFeaturePolicy.allowsWorkspaceMode(route.mode) ||
        (!experimentalEnabled&&state.detail?.takeIf{it.id==route.id}?.kind=="blend")) {
        ArchivedPlaylistFeatureScreen { nav.navigateUp() };return
    }
    val creationScope=rememberCoroutineScope()
    val controller by player.controllerState.collectAsStateWithLifecycle()
    val playingQueue by player.getQueueDataState().collectAsStateWithLifecycle()
    val nowPlaying by player.nowPlayingState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model, route.id, route.token) {
        model.visible(route.id.takeIf { route.mode=="detail" },route.token.takeIf { route.mode=="invite" })
        onStopOrDispose { model.hidden() }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable(route.id) { mutableStateOf(route.inviteOnOpen&&experimentalEnabled) }
    var startInvitation by rememberSaveable(route.id) { mutableStateOf(route.inviteOnOpen&&experimentalEnabled) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showMove by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showSongOrder by rememberSaveable(route.id) { mutableStateOf(false) }
    var restrict by rememberSaveable { mutableStateOf(false) }
    var showFolderCreate by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable(route.id) { mutableStateOf(false) }
    var songOptionsId by rememberSaveable(route.id,state.owner) { mutableStateOf<String?>(null) }
    var title by rememberSaveable(route.id,route.mode) { mutableStateOf(route.title) }
    val creationId = rememberSaveable(route.mode) { UUID.randomUUID().toString() }
    var consent by rememberSaveable(route.token,route.mode) { mutableStateOf(false) }
    val creation = route.mode in listOf("personal","collaborative","blend","folder")
    val folder = state.library.folders.find { it.id==route.id }
    val detail = state.detail?.takeIf{it.id==route.id}
    val cacheId=localCacheId?:state.localPlaylists.firstOrNull{it.canonicalPlaylistId==detail?.id}?.id
    val downloads:LocalPlaylistViewModel=koinViewModel(key="canonical-download-${route.id}")
    val downloadState by downloads.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(cacheId,detail?.revision){cacheId?.let{downloads.updatePlaylistState(it,true)}}
    val owner = detail?.ownerId==state.owner
    var coverDraft by rememberSaveable(route.id,state.owner){mutableStateOf<String?>(null)}
    var resetCover by rememberSaveable(route.id,state.owner){mutableStateOf(false)}
    val coverPicker=com.tan.gratify.expect.ui.photoPickerResult { uri ->
        if(showEdit&&owner&&!state.busy&&uri!=null){coverDraft=uri;resetCover=false}
    }
    val editor = detail?.member==true && detail.kind=="collaborative"
    fun play(song: WorkspaceSong,shuffle:Boolean=false) { detail?.let { p ->
        val tracks=p.items.map { it.playbackTrack() }
        val queue=if(shuffle)tracks.shuffled() else tracks
        val first=if(shuffle)queue.firstOrNull()?:return else song.playbackTrack()
        player.setQueueData(QueueData.Data(listTracks=queue.toCollection(ArrayList()),firstPlayedTrack=first,playlistId="WORKSPACE_${p.id}",playlistName=p.title,playlistType=PlaylistType.PLAYLIST,continuation=null))
        player.loadMediaItemFromTrack(first,Config.PLAYLIST_CLICK,index=queue.indexOfFirst{it.videoId==first.videoId}.coerceAtLeast(0))
    } }
    com.tan.gratify.ui.component.LayerBackHandler(state.busy) { }
    fun open(id: String,invite:Boolean=false) { nav.navigate(PlaylistWorkspaceDestination("detail",id,inviteOnOpen=invite)) { popUpTo<PlaylistWorkspaceDestination> { inclusive=true } } }
        Scaffold(containerColor=GratifyColors.Background, modifier=Modifier.imePadding(), topBar={
        if(detail?.kind!="collaborative") {
        CenterAlignedTopAppBar(title={ Text(when { creation -> if(route.mode=="blend") "Buat Blend" else ""
            route.mode=="invite" -> "Undangan Gratify"; route.mode=="folder_detail" -> folder?.title ?: "Folder"; else -> detail?.title ?: "Playlist" }) },
            navigationIcon={ IconButton(onClick={ if(!state.busy) nav.navigateUp() },enabled=!state.busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Kembali") } },
            actions={if(detail!=null||folder!=null)IconButton(onClick={showOptions=true},enabled=!state.busy){Icon(Icons.Rounded.MoreVert,"Pilihan lainnya")}},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=GratifyColors.Background))
        }
    },contentWindowInsets=if(detail?.kind=="collaborative")WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if(detail?.kind!="collaborative")WorkspaceFeedback(state,{ model.refresh() })
            if(!state.accountReady) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("Masuk dengan akun Gratify untuk membuat playlist bersama, mengatur folder, atau menerima undangan.")
                    Button(onClick={ model.refresh() }) { Text("Coba sambungkan kembali") }
                }
            } else when {
                creation&&route.mode=="blend" -> BlendSetupContent(consent,{consent=it},state.busy,
                    onInvite={model.create("blend",title.ifBlank{"Blend kami"},creationId,consent,parentId=route.parentId.takeIf{it.isNotBlank()}){open(it,true)}})
                creation -> PlaylistNameEntry(if(route.mode=="folder")"Beri nama folder" else if(route.mode=="personal")"Beri nama playlist-mu" else "Beri nama playlist kalian",
                    title,{title=it},!state.busy,state.busy,onCancel={nav.navigateUp()},error=state.error,
                    notice=if(route.mode=="folder")"Folder mengatur koleksi tanpa mengubah playlist." else "Mulai privat. Kamu bisa mengundang teman setelah playlist dibuat.",
                    onCreate={model.create(route.mode,title,creationId,false,parentId=route.parentId.takeIf{it.isNotBlank()},automaticName=title==route.title){id->
                        if(route.mode=="folder")nav.navigate(PlaylistWorkspaceDestination("folder_detail",id)){popUpTo<PlaylistWorkspaceDestination>{inclusive=true}} else open(id,route.mode=="collaborative")}})
                route.mode=="invite" -> state.invitation?.let { invite -> Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text(invite.title,style=typo().headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Undangan dari ${invite.ownerName ?: "pengguna Gratify"}")
                    if(invite.kind=="blend") BlendConsent(consent,{consent=it})
                    else Text("Dengan bergabung, kamu bisa menambah, menghapus, dan mengurutkan lagu. Privasi dan anggota dikelola pemilik.")
                    Button(onClick={model.join(route.token,consent||invite.alreadyMember,{open(it)})},enabled=!state.busy&&(invite.kind!="blend"||consent||invite.alreadyMember)) {
                        Text(if(invite.alreadyMember) "Buka playlist" else "Gabung")
                    }
                } }
                route.mode=="folder_detail" -> if(folder!=null) {
                    val refs = state.library.placements.filter { it.folderId==folder.id }.map { it.ref }.toSet()
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp)) {
                        item { Column {
                            FlowRow { TextButton(onClick={nav.openMainTab(LibraryDestination,returnToRoot=true)}){Text("Koleksi")}
                                folderBreadcrumbs(folder.id,state.library.folders).forEach { crumb-> TextButton(onClick={if(crumb.id!=folder.id)nav.navigate(PlaylistWorkspaceDestination("folder_detail",crumb.id))}){Text(crumb.title)} }
                            }
                            TextButton(onClick={showAdd=true},enabled=!state.busy) { Text("Tambahkan playlist") }
                            TextButton(onClick={showFolderCreate=true},enabled=!state.busy){Icon(Icons.Rounded.Add,null);Text("Buat di folder ini")}
                        } }
                        items(state.library.folders.filter{it.parentId==folder.id},key={"folder:${it.id}"}) { child->
                            ListItem(headlineContent={Text(child.title)},supportingContent={Text("Subfolder")},leadingContent={Icon(Icons.Rounded.Folder,null)},
                                modifier=Modifier.clickable{nav.navigate(PlaylistWorkspaceDestination("folder_detail",child.id))},colors=ListItemDefaults.colors(containerColor=GratifyColors.Background))
                        }
                        items(state.localPlaylists.filter { it.folderReference() in refs },key={ "local:${it.id}" }) { p ->
                            FolderPlaylistRow(p.title,{nav.navigate(LocalPlaylistDestination(p.id))},{p.folderReference()?.let {model.folder("move",null,it)}}) { modifier ->
                                if(com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail(p.thumbnail,p.youtubePlaylistId))
                                    com.tan.gratify.ui.component.PlaylistCollageThumbnail(p.tracks.orEmpty(),modifier,p.title)
                                else AsyncImage(p.thumbnail,null,modifier,contentScale=ContentScale.Crop)
                            }
                        }
                        items(state.library.playlists.filter { "workspace:${it.id}" in refs && state.localPlaylists.none{cache->cache.canonicalPlaylistId==it.id} },key={it.id}) { p ->
                            FolderPlaylistRow(p.title,{nav.navigate(PlaylistWorkspaceDestination("detail",p.id))},{model.folder("move",null,"workspace:${p.id}")}) { modifier->WorkspaceCover(p,state.owner,modifier) }
                        }
                        if(refs.isEmpty()&&state.library.folders.none{it.parentId==folder.id}) item { Text("Folder kosong. Tambahkan playlist atau buat subfolder dan playlist bersama di sini.",Modifier.padding(16.dp)) }
                    }
                }
                detail!=null&&detail.kind=="blend"&&detail.members.size<2 -> BlendSetupContent(true,{},state.busy,
                    onInvite={startInvitation=true;showPeople=true},member=detail.members.firstOrNull{it.userId==state.owner},waiting=true)
                detail!=null -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=16.dp)) {
                    item {
                        WorkspacePlaylistHeader(detail,state.owner,state.busy,onPeople={startInvitation=false;showPeople=true},
                            onEdit={title=detail.title;coverDraft=null;resetCover=false;showEdit=true;model.clearFeedback()},
                            onPlay={if(playingQueue?.data?.playlistId=="WORKSPACE_${detail.id}"||(cacheId!=null&&playingQueue?.data?.playlistId==com.tan.common.LOCAL_PLAYLIST_ID+cacheId))player.onUIEvent(UIEvent.PlayPause) else detail.items.firstOrNull()?.let{play(it)}},onAdd={showAdd=true},
                            onBack={nav.navigateUp()},onOptions={showOptions=true},onShuffle={detail.items.firstOrNull()?.let{play(it,true)}},
                            onInvite={startInvitation=true;showPeople=true},
                            downloadAction={if(detail.member&&cacheId!=null)com.tan.gratify.ui.component.CollectionDownloadAction(downloadState.downloadState,
                                enabled=detail.items.isNotEmpty()&&!state.busy,onClick={downloads.downloadFullPlaylist()})},
                            isPlaying=controller.isPlaying&&(playingQueue?.data?.playlistId=="WORKSPACE_${detail.id}"||(cacheId!=null&&playingQueue?.data?.playlistId==com.tan.common.LOCAL_PLAYLIST_ID+cacheId)))
                        if(detail.kind=="collaborative")WorkspaceFeedback(state,{model.refresh()})
                        if(detail.kind=="blend") Text(if(detail.members.size<2) "Undang teman untuk membentuk Blend."
                            else if(detail.items.isEmpty()) "Selera musik belum cukup. Sukai atau dengarkan lagu, tunggu sinkronisasi, lalu muat ulang."
                            else "Campuran dari favorit dan ringkasan dengar. Diperbarui saat dibuka setelah satu hari.",Modifier.padding(horizontal=16.dp),color=GratifyColors.TextSecondary)
                        if(editor&&detail.items.isEmpty())Text("Playlist ini masih kosong. Tambahkan lagu pilihanmu.",Modifier.padding(16.dp),color=GratifyColors.TextSecondary)
                        if(editor&&detail.items.isNotEmpty())TextButton(onClick={showSongOrder=true},enabled=!state.busy,modifier=Modifier.padding(horizontal=8.dp)){Text("Edit lagu")}
                    }
                    items(detail.items,key={it.id}) { song ->
                        if(detail.kind=="collaborative") WorkspaceSongRow(song,controller.isPlaying&&nowPlaying?.songEntity?.videoId==song.videoId,
                            onPlay={play(song)},onMore={if(!state.busy)songOptionsId=song.id},onAddToQueue={player.addListToQueue(arrayListOf(song.playbackTrack()))})
                        else Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            AsyncImage(song.thumbnail ?: "https://i.ytimg.com/vi/${song.videoId}/mqdefault.jpg",null,Modifier.size(42.dp).padding(end=8.dp),contentScale=ContentScale.Crop)
                            Column(Modifier.weight(1f).clickable { play(song) }.padding(vertical=14.dp)) {
                                Text(song.title,style=typo().bodyLarge); Text(song.artist,style=typo().bodySmall,color=GratifyColors.TextSecondary)
                            if(detail.kind=="blend")detail.members.firstOrNull{it.userId==song.addedBy}?.let{m->Text("Dari selera ${m.name?:"anggota"}",style=typo().labelSmall,color=GratifyColors.TextSecondary)}
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.items?.firstOrNull{it.id==songOptionsId}?.let { song ->
        ModalBottomSheet(onDismissRequest={if(!state.busy)songOptionsId=null},sheetState=rememberBusyModalSheetState(state.busy,skipPartiallyExpanded=false),containerColor=GratifyColors.Surface) {
            Text(song.title,Modifier.padding(16.dp),style=typo().titleMedium)
            @Composable fun option(label:String,enabled:Boolean=true,action:()->Unit) {
                TextButton(onClick={songOptionsId=null;action()},enabled=enabled&&!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)}
            }
            option("Tambahkan ke antrean"){player.addListToQueue(arrayListOf(song.playbackTrack()))}
            if(editor) {
                val index=detail.items.indexOf(song)
                fun move(to:Int){val order=detail.items.map{it.id}.toMutableList();order.add(to,order.removeAt(index));model.reorder(detail.id,order,detail.revision)}
                option("Pindahkan ke atas",index>0){move(index-1)}
                option("Pindahkan ke bawah",index<detail.items.lastIndex){move(index+1)}
                option("Hapus lagu"){model.remove(detail.id,song)}
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
    if(showOptions&&(folder!=null||detail!=null))ModalBottomSheet(onDismissRequest={if(!state.busy)showOptions=false},sheetState=rememberBusyModalSheetState(state.busy,skipPartiallyExpanded=false),containerColor=GratifyColors.Surface) {
        @Composable fun option(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,action:()->Unit) {
            ListItem(headlineContent={Text(label)},leadingContent={Icon(icon,null)},modifier=Modifier.clickable(enabled=!state.busy){showOptions=false;action()},colors=ListItemDefaults.colors(containerColor=GratifyColors.Surface))
        }
        if(folder!=null) {
            option("Ubah nama",Icons.Rounded.Edit){title=folder.title;showEdit=true}
            option("Pindahkan folder",Icons.Rounded.Folder){showMove=true}
            option("Hapus folder",Icons.Rounded.Delete){confirmDelete=true}
        } else if(detail!=null) {
            if(editor)option("Edit lagu",Icons.Rounded.DragHandle){showSongOrder=true}
            if(detail.items.isNotEmpty())option("Tambahkan ke antrean",Icons.Rounded.QueueMusic){player.addListToQueue(ArrayList(detail.items.map{it.playbackTrack()}))}
            if(detail.isPublic&&detail.sharedId!=null)option("Buka halaman publik",Icons.Rounded.Public){nav.navigate(com.tan.gratify.ui.navigation.destination.list.SharedPlaylistDestination(requireNotNull(detail.sharedId)))}
            if(experimentalEnabled&&detail.member)option("Anggota dan undangan",Icons.Rounded.Group){startInvitation=false;showPeople=true}
            if(owner)option("Nama & detail",Icons.Rounded.Edit){title=detail.title;coverDraft=null;resetCover=false;showEdit=true;model.clearFeedback()}
            if(experimentalEnabled&&detail.member)option("Pindahkan ke folder",Icons.Rounded.Folder){showMove=true}
            if(experimentalEnabled&&owner&&detail.members.size>1)option("Batasi hanya saya",Icons.Rounded.Lock){restrict=true}
            if(owner||detail.member)option(if(owner)"Hapus playlist" else "Keluar dari playlist",Icons.Rounded.Delete){confirmDelete=true}
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
    if(confirmDelete) AlertDialog(onDismissRequest={if(!state.busy)confirmDelete=false},title={Text(if(folder!=null) "Hapus folder?" else if(owner) "Hapus playlist?" else "Keluar dari playlist?")},
        text={Text(if(folder!=null) "Playlist dan subfolder di dalamnya dipindahkan ke folder induk atau Koleksi. Isinya tetap tersimpan." else if(owner) "Playlist ini akan dihapus untuk semua anggota." else if(detail?.kind=="blend") "Hak akses dan kontribusi Blend kamu akan dihentikan." else "Kamu akan kehilangan akses untuk mengelola playlist ini.")},
        confirmButton={TextButton(onClick={if(folder!=null)model.folder("folder_delete",folder.id,done={nav.openMainTab(LibraryDestination,returnToRoot=true)}) else detail?.let{model.action(if(owner)"delete" else "leave",it.id,done={nav.openMainTab(LibraryDestination,returnToRoot=true)})}},enabled=!state.busy){Text("Lanjutkan")}},
        dismissButton={TextButton(onClick={confirmDelete=false},enabled=!state.busy){Text("Batal")}})
    if(showEdit&&detail!=null) WorkspaceEditDialog(detail,title,{title=it},state,coverDraft,resetCover,coverPicker.isActive,
        onPick={coverPicker.launch()},onReset={coverDraft=null;resetCover=true},onClose={showEdit=false},onRetry={model.refresh()}) { description,public ->
        model.saveDetails(detail.id,title,description,public,coverDraft,resetCover) {showEdit=false;coverDraft=null;resetCover=false}
    }
    if(showEdit&&folder!=null) AlertDialog(onDismissRequest={if(!state.busy)showEdit=false},title={Text("Ubah nama folder")},
        text={OutlinedTextField(title,{if(it.length<=100)title=it},singleLine=true,enabled=!state.busy)},
        confirmButton={TextButton(onClick={model.folder("folder_rename",folder.id,title=title){showEdit=false}},enabled=!state.busy&&title.isNotBlank()){Text("Simpan")}},
        dismissButton={TextButton(onClick={showEdit=false},enabled=!state.busy){Text("Batal")}})
    if(restrict&&detail!=null) AlertDialog(onDismissRequest={if(!state.busy)restrict=false},title={Text("Batasi hanya saya?")},
        text={Text("Playlist menjadi privat, anggota lain dikeluarkan, dan semua undangan lama dicabut. Kamu dapat mengundang lagi nanti.")},
        confirmButton={TextButton(onClick={model.action("restrict",detail.id){restrict=false}},enabled=!state.busy){Text("Batasi")}},
        dismissButton={TextButton(onClick={restrict=false},enabled=!state.busy){Text("Batal")}})
    if(experimentalEnabled&&showPeople&&detail!=null) WorkspacePeopleSheet(detail,state,model,{showPeople=false},startInvitation,
        onInvitationStarted={startInvitation=false})
    if(showSongOrder&&detail!=null&&editor)CanonicalSongOrderSheet(detail,state.busy,state.error,{showSongOrder=false}) { ids,revision ->
        model.action("reorder",detail.id,buildJsonObject{put("operation_id",UUID.randomUUID().toString());put("ids",JsonArray(ids.map(::JsonPrimitive)));put("revision",revision)}){showSongOrder=false}
    }
    if(showFolderCreate&&folder!=null) PlaylistCreateMenu({showFolderCreate=false},showPersonal=false) { kind->
        showFolderCreate=false
        creationScope.launch { nav.navigate(PlaylistWorkspaceDestination(kind,title=model.nextName(kind),parentId=folder.id)) }
    }
    if(experimentalEnabled&&showMove&&(detail!=null||folder!=null)) ModalBottomSheet(onDismissRequest={if(!state.busy)showMove=false},sheetState=rememberBusyModalSheetState(state.busy,skipPartiallyExpanded=false),containerColor=GratifyColors.Surface) {
        Text("Pindahkan ke folder",Modifier.padding(20.dp),style=typo().titleLarge)
        fun move(target:String?) { if(folder!=null)model.moveFolder(folder.id,target){showMove=false} else if(detail!=null)model.folder("move",target,"workspace:${detail.id}"){showMove=false} }
        WorkspaceFeedback(state,{model.refresh()})
        TextButton(onClick={move(null)},enabled=!state.busy){Text("Koleksi utama")}
        LazyColumn(Modifier.heightIn(max=440.dp)) { items(if(folder!=null)folderMoveTargets(folder.id,state.library.folders) else state.library.folders,key={it.id}) { f ->
            ListItem(headlineContent={Text(folderPath(f.id,state.library.folders))},modifier=Modifier.clickable(enabled=!state.busy){move(f.id)})
        } }
    }
    if(showAdd) ModalBottomSheet(onDismissRequest={if(!state.busy)showAdd=false},sheetState=rememberBusyModalSheetState(state.busy,skipPartiallyExpanded=false),containerColor=GratifyColors.Surface) {
        if(folder!=null) LazyColumn(contentPadding=PaddingValues(16.dp),modifier=Modifier.heightIn(max=560.dp)) {
            item { Text("Tambahkan playlist",style=typo().titleLarge) }
            items(state.localPlaylists,key={"local:${it.id}"}) { p -> TextButton(onClick={p.folderReference()?.let{model.folder("move",folder.id,it){showAdd=false}}},enabled=!state.busy&&p.folderReference()!=null){Text(p.title)} }
            items(state.library.playlists.filter{p->state.localPlaylists.none{it.canonicalPlaylistId==p.id}},key={it.id}) { p -> TextButton(onClick={model.folder("move",folder.id,"workspace:${p.id}"){showAdd=false}},enabled=!state.busy){Text(p.title)} }
        } else if(detail!=null) {
            var query by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(query,{query=it;model.search(it)},Modifier.fillMaxWidth().padding(16.dp),label={Text("Cari lagu")},singleLine=true)
            WorkspaceFeedback(state,{model.search(query)})
            LazyColumn(Modifier.heightIn(max=480.dp),contentPadding=PaddingValues(16.dp)) {
                if(state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(state.searchResults,key={it.videoId}) { song ->
                    val added=detail.items.any{it.videoId==song.videoId}
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(song.title.orEmpty(),Modifier.weight(1f).padding(vertical=12.dp))
                        IconButton(onClick={model.add(detail.id,song.toTrack())},enabled=!added&&!state.busy){Icon(if(added)Icons.Rounded.Check else Icons.Rounded.Add,"Tambah lagu")}
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable internal fun BlendConsent(checked: Boolean, onChange: (Boolean)->Unit) {
    Text("Blend menggabungkan favorit dan ringkasan dengar anggota. Nama, foto, dan lagu hasil campuran terlihat oleh anggota; riwayat mentah dan isi chat tetap privat.")
    Row(Modifier.fillMaxWidth().toggleable(value=checked,role=androidx.compose.ui.semantics.Role.Checkbox,onValueChange=onChange).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked,null); Text("Saya setuju memakai ringkasan selera untuk Blend.",Modifier.weight(1f))
    }
}
@Composable private fun FolderPlaylistRow(title: String,onOpen:()->Unit,onRemove:()->Unit,artwork:@Composable (Modifier)->Unit={}) {
    Row(verticalAlignment=Alignment.CenterVertically) { artwork(Modifier.size(48.dp).clickable(onClick=onOpen));Spacer(Modifier.width(12.dp));Text(title,Modifier.weight(1f).clickable(onClick=onOpen).padding(vertical=20.dp)); IconButton(onClick=onRemove){Icon(Icons.Rounded.RemoveCircleOutline,"Keluarkan dari folder")} }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorkspaceEditDialog(detail: PlaylistWorkspace,title: String,onTitle:(String)->Unit,state:PlaylistWorkspaceState,
    coverDraft:String?,resetCover:Boolean,picking:Boolean,onPick:()->Unit,onReset:()->Unit,onClose:()->Unit,onRetry:()->Unit,onSave:(String,Boolean)->Unit) {
    val busy=state.busy||picking
    var description by rememberSaveable(detail.id){mutableStateOf(detail.description)}
    var public by rememberSaveable(detail.id){mutableStateOf(detail.isPublic)}
    var showCover by rememberSaveable(detail.id){mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest={if(!busy)onClose()},sheetState=rememberBusyModalSheetState(busy),containerColor=GratifyColors.Surface) {
        Column(Modifier.fillMaxWidth().heightIn(max=720.dp).imePadding()) {
            WorkspaceSheetHeader("Nama & detail","Batalkan",if(busy)"Menyimpan…" else "Simpan",busy,title.isNotBlank(),onClose,{onSave(description,public)})
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top) {
                    Box(Modifier.size(88.dp).clickable(enabled=!busy){showCover=true}) {
                        WorkspaceCover(detail.copy(coverUrl=if(resetCover)null else coverDraft?:detail.coverUrl,coverPath=if(resetCover)null else detail.coverPath),state.owner,Modifier.fillMaxSize())
                        Icon(Icons.Rounded.Edit,"Pilih foto sampul",Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(GratifyColors.Surface).padding(6.dp).size(20.dp))
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        TextField(title,{if(it.length<=100)onTitle(it)},label={Text("Nama")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                        TextField(description,{if(it.length<=500)description=it},placeholder={Text("Tambahkan deskripsi")},enabled=!busy,modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
                    }
                }
                TextButton(onClick={showCover=true},enabled=!busy){Text("Pilih foto sampul")}
                if(coverDraft!=null||detail.coverPath!=null)TextButton(onClick=onReset,enabled=!busy){Text("Gunakan sampul otomatis")}
                WorkspaceFeedback(state,onRetry)
                if(detail.kind!="blend") Row(verticalAlignment=Alignment.CenterVertically){Icon(if(public)Icons.Rounded.Public else Icons.Rounded.Lock,null);Text("Publik di Search",Modifier.weight(1f).padding(start=12.dp));Switch(public,{public=it},enabled=!busy)}
                Text(if(detail.kind=="blend")"Blend tetap privat untuk anggota." else "Privat tetap dapat diakses anggota yang diundang.",style=typo().bodySmall,color=GratifyColors.TextSecondary)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
    if(showCover)WorkspaceCoverChooser(detail,state.owner,coverDraft,resetCover,busy,
        onPick={showCover=false;onPick()},onReset=onReset,onClose={showCover=false})
}

@Composable internal fun WorkspaceCover(detail:PlaylistWorkspace,owner:String?,modifier:Modifier=Modifier) {
    val url=detail.coverUrl
    val images=detail.items.take(4).map{it.thumbnail?:"https://i.ytimg.com/vi/${it.videoId}/mqdefault.jpg"}
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(GratifyColors.SurfaceRaised),contentAlignment=Alignment.Center) {
        if(url!=null) {
            val context=LocalPlatformContext.current
            val request=remember(url,detail.coverPath,detail.legacyCover,owner,context){ImageRequest.Builder(context).data(url)
                .memoryCacheKey("workspace-cover:$owner:${detail.coverPath?:detail.legacyCover?:url}").diskCacheKey("workspace-cover:$owner:${detail.coverPath?:detail.legacyCover?:url}").build()}
            AsyncImage(request,"Sampul ${detail.title}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        } else if(detail.coverPath!=null) {
            Icon(Icons.Rounded.Image,"Sampul belum dapat dimuat",Modifier.size(40.dp),tint=GratifyColors.TextSecondary)
        } else com.tan.gratify.ui.component.PlaylistAutomaticArtwork(images,Modifier.fillMaxSize(),detail.title)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorkspacePeopleSheet(detail: PlaylistWorkspace,state:PlaylistWorkspaceState,model:PlaylistWorkspaceViewModel,onClose:()->Unit,startInviting:Boolean=false,
    onInvitationStarted:()->Unit={}) {
    var target by rememberSaveable(detail.id){mutableStateOf("")}
    var query by rememberSaveable(detail.id){mutableStateOf("")}
    val owner=detail.ownerId==state.owner
    val canInvite=owner||(detail.kind=="blend"&&detail.member)
    var inviting by rememberSaveable(detail.id){mutableStateOf(startInviting)}
    var chooseChat by rememberSaveable(detail.id){mutableStateOf(false)}
    LaunchedEffect(detail.id){if(startInviting&&canInvite){onInvitationStarted();model.makeInvite(detail.id)}}
    ModalBottomSheet(onDismissRequest={if(!state.busy)onClose()},sheetState=rememberBusyModalSheetState(state.busy),containerColor=GratifyColors.Surface) {
        LazyColumn(Modifier.heightIn(max=640.dp),contentPadding=PaddingValues(16.dp)) {
            item { Text(if(inviting)"Undang ke ${if(detail.kind=="blend")"Blend" else "playlist"}" else "Orang",Modifier.fillMaxWidth(),style=typo().titleLarge,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center);WorkspaceFeedback(state,{model.refresh()}) }
            if(canInvite&&!inviting)item {
                ListItem(headlineContent={Text(if(detail.kind=="blend")"Undang teman" else "Tambah kolaborator")},leadingContent={WorkspaceRoundIcon(Icons.Rounded.Add,null)},
                    modifier=Modifier.clickable(enabled=!state.busy){inviting=true;target="";model.makeInvite(detail.id)},colors=ListItemDefaults.colors(containerColor=GratifyColors.Surface))
            }
            if(inviting)item {
                WorkspaceInviteCard(detail,state.owner)
                Text(if(detail.kind=="blend")"Bergabung untuk menyatukan selera musik." else "Undang sebagai kolaborator",Modifier.padding(top=20.dp),style=typo().titleMedium,fontWeight=FontWeight.Bold)
                Text(if(detail.kind=="blend")"Campuran dibuat otomatis setelah data anggota tersedia." else "Kolaborator dapat menambahkan, menghapus, dan mengurutkan lagu.",style=typo().bodySmall,color=GratifyColors.TextSecondary)
                Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={state.inviteLink?.let{shareUrl("Undangan ${detail.title}",it)}},enabled=!state.busy&&state.inviteLink!=null,modifier=Modifier.weight(1f)){Icon(Icons.Rounded.Share,null);Spacer(Modifier.width(6.dp));Text("Bagikan link")}
                    OutlinedButton(onClick={chooseChat=true},enabled=!state.busy,modifier=Modifier.weight(1f)){Icon(Icons.Rounded.ChatBubbleOutline,null);Spacer(Modifier.width(6.dp));Text("Chat Gratify")}
                }
                if(state.inviteLink==null&&!state.busy)TextButton(onClick={target="";model.makeInvite(detail.id)}){Text("Coba buat link lagi")}
                Text("Link berlaku 7 hari. Bagikan hanya kepada orang yang ingin kamu undang.",Modifier.padding(vertical=12.dp),style=typo().bodySmall,color=GratifyColors.TextSecondary)
            }
            if(inviting)item{TextButton(onClick={inviting=false;chooseChat=false}){Text("Kembali ke anggota")}}
            items(if(inviting)emptyList() else detail.members,key={it.userId}) { m -> Row(verticalAlignment=Alignment.CenterVertically){
                UserAvatar(m.avatar,m.name,Modifier.size(42.dp),borderWidth=0.dp)
                Column(Modifier.weight(1f).padding(12.dp)){Text(m.name?:"Pengguna Gratify");Text(if(m.owner)"Pemilik" else "Anggota",style=typo().bodySmall)}
                if(owner&&!m.owner) TextButton(onClick={model.action("remove_member",detail.id,buildJsonObject{put("user_id",m.userId)})},enabled=!state.busy){Text("Keluarkan")}
            } }
            if(owner)item {TextButton(onClick={model.action("revoke_invites",detail.id){model.clearFeedback();inviting=false}},enabled=!state.busy){Text("Cabut semua undangan")}}
            if(canInvite&&chooseChat) item {
                OutlinedTextField(query,{query=it;target="";model.searchPeople(it)},label={Text("Cari teman untuk undangan chat")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                if(state.peopleLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.people.forEach { friend -> TextButton(onClick={target=friend.id;model.makeInvite(detail.id,target)},enabled=!state.busy) {
                    UserAvatar(friend.avatarUrl,friend.displayName,Modifier.size(32.dp),borderWidth=0.dp)
                    Text(friend.displayName?:"Pengguna Gratify",Modifier.padding(start=8.dp))
                } }
                if(state.inviteLink!=null&&target.isNotBlank()) TextButton(onClick={model.sendInvite(target)},enabled=!state.busy){Text("Kirim melalui chat Gratify")}
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}
