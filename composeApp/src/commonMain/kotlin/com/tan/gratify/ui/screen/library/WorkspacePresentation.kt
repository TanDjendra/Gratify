package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.component.CollectionDetailHeader
import com.tan.gratify.ui.component.CollectionDetailAction
import com.tan.gratify.ui.component.SongFullWidthItems
import com.tan.gratify.ui.component.playlistTitleGradient
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*

internal val WorkspaceGreen=Color(0xFF1ED760)
internal val WorkspaceBackdrop=Brush.verticalGradient(listOf(Color(0xFF4A4A4A),GratifyColors.Background))

@Composable internal fun PlaylistMemberRow(members:List<WorkspaceMember>,canInvite:Boolean,
    onInvite:()->Unit,onPeople:()->Unit,canViewPeople:Boolean=true) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        if(canInvite)IconButton(onClick=onInvite,modifier=Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Add,"Undang kolaborator",Modifier.size(32.dp).clip(CircleShape).background(GratifyColors.SurfaceRaised).padding(4.dp))
        }
        Row(Modifier.weight(1f).heightIn(min=48.dp).semantics{contentDescription="Anggota playlist"}.clickable(enabled=canViewPeople,onClick=onPeople),verticalAlignment=Alignment.CenterVertically) {
            members.take(3).forEach{m->UserAvatar(m.avatar,m.name,Modifier.padding(start=4.dp).size(28.dp),borderWidth=0.dp)}
            Text(members.firstOrNull{it.owner}?.name?:"Pemilik playlist",Modifier.padding(start=8.dp),style=typo().labelLarge,
                maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun WorkspaceSheetHeader(title:String,cancelLabel:String,saveLabel:String,
    busy:Boolean,saveEnabled:Boolean,onClose:()->Unit,onSave:()->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
        @Composable fun cancel(){TextButton(onClick=onClose,enabled=!busy){Text(cancelLabel)}}
        @Composable fun save(){TextButton(onClick=onSave,enabled=!busy&&saveEnabled){Text(saveLabel,color=if(busy||!saveEnabled)GratifyColors.TextSecondary else WorkspaceGreen)}}
        if(maxWidth<350.dp||androidx.compose.ui.platform.LocalDensity.current.fontScale>1.2f)Column {
            Text(title,Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),style=typo().titleMedium,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){cancel();Spacer(Modifier.weight(1f));save()}
        } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            cancel();Text(title,Modifier.weight(1f),style=typo().titleMedium,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);save()
        }
    }
}

@Composable internal fun WorkspaceRoundIcon(icon:ImageVector,label:String?,modifier:Modifier=Modifier) {
    Box(modifier.size(56.dp).clip(CircleShape).background(GratifyColors.SurfaceRaised),contentAlignment=Alignment.Center) {
        Icon(icon,label,Modifier.size(28.dp),tint=GratifyColors.TextPrimary)
    }
}

/** A common naming step keeps personal and collaborative creation consistent. */
@Composable internal fun PlaylistNameEntry(heading:String,title:String,onTitle:(String)->Unit,
    enabled:Boolean,busy:Boolean,onCreate:()->Unit,onCancel:()->Unit,createLabel:String="Buat",
    cancelLabel:String="Batal",notice:String?=null,error:String?=null) {
    var field by rememberSaveable(stateSaver=TextFieldValue.Saver) {mutableStateOf(TextFieldValue(title,TextRange(0,title.length)))}
    // Synchronize after composition, without a queued coroutine restoring an older keystroke.
    SideEffect{if(field.text!=title)field=TextFieldValue(title,TextRange(0,title.length))}
    BoxWithConstraints(Modifier.fillMaxSize().background(WorkspaceBackdrop)) {
        val height=maxHeight
        val compact=height<540.dp||androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
        @Composable fun actions() {
            Row(Modifier.widthIn(max=360.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick=onCancel,enabled=!busy,shape=CircleShape,border=BorderStroke(1.dp,GratifyColors.Outline),modifier=Modifier.weight(0.8f).heightIn(min=52.dp)) {Text(cancelLabel,textAlign=TextAlign.Center)}
                Button(onClick=onCreate,enabled=enabled&&title.isNotBlank(),shape=CircleShape,
                    colors=ButtonDefaults.buttonColors(containerColor=WorkspaceGreen,contentColor=Color.Black),modifier=Modifier.weight(1.2f).heightIn(min=52.dp)) {
                    if(busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                    Text(createLabel,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                }
            }
        }
        Column(Modifier.widthIn(max=600.dp).fillMaxSize().align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
        Column(Modifier.then(if(compact)Modifier.weight(1f) else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState()).then(if(compact)Modifier else Modifier.heightIn(min=height)).padding(horizontal=24.dp,vertical=28.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(heading,style=typo().headlineSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Spacer(Modifier.height(44.dp))
            BasicTextField(field,{if(it.text.length<=100){field=it;onTitle(it.text)}},enabled=enabled,singleLine=true,
                textStyle=typo().headlineLarge.copy(color=GratifyColors.TextPrimary,fontSize=32.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center),
                cursorBrush=SolidColor(WorkspaceGreen),modifier=Modifier.fillMaxWidth().heightIn(min=64.dp),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={if(enabled&&title.isNotBlank())onCreate()}))
            HorizontalDivider(color=GratifyColors.Outline)
            notice?.let{Text(it,Modifier.padding(top=16.dp),style=typo().bodySmall,color=GratifyColors.TextSecondary,textAlign=TextAlign.Center)}
            error?.let{Text(it,Modifier.padding(top=16.dp),color=GratifyColors.Error,textAlign=TextAlign.Center)}
            if(!compact){Spacer(Modifier.height(36.dp));actions()}
        }
        if(compact)Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){actions()}
        }
    }
}

@Composable internal fun BlendSetupContent(consent:Boolean,onConsent:(Boolean)->Unit,busy:Boolean,
    onInvite:()->Unit,feedback:@Composable ()->Unit={},member:WorkspaceMember?=null,waiting:Boolean=false) {
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=24.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Box(Modifier.width(220.dp).height(148.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.align(Alignment.CenterEnd).size(136.dp).clip(CircleShape).background(GratifyColors.SurfaceRaised),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Add,null,Modifier.size(60.dp),tint=GratifyColors.TextPrimary)
            }
            UserAvatar(member?.avatar,member?.name?:"Kamu",Modifier.align(Alignment.CenterStart).size(136.dp),borderWidth=0.dp)
        }
        Text(if(waiting)"Undangan Blend menunggu teman" else "Undang teman ke Blend",style=typo().headlineLarge,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        Text("Satukan selera musik kalian dalam playlist yang disusun otomatis. Undang teman lewat link atau chat Gratify.",style=typo().bodyLarge,textAlign=TextAlign.Center)
        Text("Hingga 10 orang. Campuran mulai terbentuk setelah teman bergabung dan data selera kalian tersedia.",style=typo().bodyMedium,color=GratifyColors.TextSecondary,textAlign=TextAlign.Center)
        Text("Blend baru muncul di Pustaka setelah teman bergabung. Lagu disusun otomatis dari selera kalian.",style=typo().bodySmall,color=GratifyColors.TextSecondary,textAlign=TextAlign.Center)
        if(!waiting) BlendConsent(consent,onConsent)
        else Text("Nama dan foto profilmu terlihat oleh anggota. Lagu dipilih dari favorit dan ringkasan dengar yang tersinkron.",style=typo().bodySmall,color=GratifyColors.TextSecondary,textAlign=TextAlign.Center)
        feedback()
    }
    Box(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),contentAlignment=Alignment.Center) {
        Button(onClick=onInvite,enabled=!busy&&(waiting||consent),shape=CircleShape,
            colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),modifier=Modifier.heightIn(min=52.dp).widthIn(min=140.dp)) {
            if(busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
            Text(if(busy)"Menyiapkan…" else "Undang",fontWeight=FontWeight.Bold)
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspacePlaylistHeader(detail:PlaylistWorkspace,owner:String?,busy:Boolean,
    onPeople:()->Unit,onEdit:()->Unit,onPlay:()->Unit,onAdd:()->Unit,
    onBack:()->Unit={},onOptions:()->Unit={},onShuffle:()->Unit={},isPlaying:Boolean=false,
    onInvite:()->Unit=onPeople,downloadAction:@Composable ()->Unit={},
    experimentalEnabled:Boolean=com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled,
    desktop:Boolean=com.tan.gratify.getPlatform()==com.tan.gratify.Platform.Desktop) {
    if(detail.kind=="collaborative") {
        val mine=detail.ownerId==owner
        Column(Modifier.fillMaxWidth()) {
        CollectionDetailHeader(title=detail.title,kind=stringResource(Res.string.playlist),subtitle="",
            metadata=stringResource(Res.string.album_length,detail.items.size.toString(),""),
            accent=playlistTitleGradient(detail.title).first(),onBack=onBack,onPlay=onPlay,onShuffle=onShuffle,
            isPlaying=isPlaying,canPlay=detail.items.isNotEmpty(),compactPlaylist=true,desktop=desktop,
            controlsEnabled=!busy,onEdit=if(mine)onEdit else null,
            artwork={WorkspaceCover(detail,owner,it)},
            subtitleContent={
                PlaylistMemberRow(if(experimentalEnabled)detail.members else detail.members.filter{it.owner},experimentalEnabled&&mine&&!busy,onInvite,onPeople,experimentalEnabled&&detail.member&&!busy)
            },description=if(detail.description.isBlank())null else ({Text(detail.description,style=typo().bodyMedium,color=GratifyColors.TextSecondary)}),
            actions={downloadAction();CollectionDetailAction(Icons.Rounded.MoreHoriz,"Pilihan lainnya",enabled=!busy,onClick=onOptions)})
        OwnedPlaylistVisibility(detail.isPublic,true,enabled=mine&&!busy,onClick=onEdit)
        OwnedPlaylistAddSongsAction(readOnly=!detail.member,unavailable=busy,onAdd=onAdd)
        }
        return
    }
    val mine=detail.ownerId==owner
    val editor=detail.member&&detail.kind=="collaborative"
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(GratifyColors.SurfaceRaised,GratifyColors.Background))).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact=maxWidth<330.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
            @Composable fun details(modifier:Modifier) {
                Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(detail.title,style=typo().headlineMedium,fontWeight=FontWeight.Bold)
                    if(mine) FilledTonalButton(onClick=onEdit,enabled=!busy,shape=CircleShape){Text("Ubah",fontWeight=FontWeight.Bold)}
                    PlaylistMemberRow(detail.members,detail.member&&!busy,onInvite,onPeople,detail.member&&!busy)
                }
            }
            if(compact)Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                WorkspaceCover(detail,owner,Modifier.size(160.dp));details(Modifier.fillMaxWidth())
            } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                WorkspaceCover(detail,owner,Modifier.size(148.dp));details(Modifier.weight(1f))
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(if(detail.isPublic)Icons.Rounded.Public else Icons.Rounded.Lock,null,Modifier.size(18.dp),tint=GratifyColors.TextSecondary)
            Text(if(detail.kind=="blend")"Blend • privat untuk anggota" else if(detail.isPublic)"Playlist kolaboratif • publik" else "Playlist kolaboratif • privat untuk anggota",style=typo().bodySmall,color=GratifyColors.TextSecondary)
        }
        if(detail.description.isNotBlank())Text(detail.description,style=typo().bodyMedium,color=GratifyColors.TextSecondary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(editor) Button(onClick=onAdd,enabled=!busy,shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),modifier=Modifier.heightIn(min=52.dp)) {
                Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(8.dp));Text("Tambahkan ke playlist ini",fontWeight=FontWeight.Bold)
            }
            if(detail.items.isNotEmpty()) FilledIconButton(onClick=onPlay,shape=CircleShape,modifier=Modifier.size(52.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=WorkspaceGreen,contentColor=Color.Black)) {
                Icon(Icons.Rounded.PlayArrow,"Putar playlist",Modifier.size(30.dp))
            }
        }
    }
}

/** Uses the personal playlist row, including artwork caching, playing state and its menu. */
@Composable internal fun WorkspaceSongRow(song:WorkspaceSong,isPlaying:Boolean,onPlay:()->Unit,
    onMore:()->Unit,onAddToQueue:()->Unit,desktop:Boolean=com.tan.gratify.getPlatform()==com.tan.gratify.Platform.Desktop) {
    SongFullWidthItems(track=song.playbackTrack(),isPlaying=isPlaying,onClickListener={onPlay()},
        onMoreClickListener={onMore()},onAddToQueue={onAddToQueue()},modifier=Modifier.fillMaxWidth(),collectionStyle=true,desktop=desktop)
}

@Composable internal fun WorkspaceInviteCard(detail:PlaylistWorkspace,owner:String?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WorkspaceBackdrop).padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            WorkspaceCover(detail,owner,Modifier.size(80.dp))
            Column(Modifier.weight(1f)) {Text(detail.title,style=typo().titleLarge,fontWeight=FontWeight.Bold);Text(if(detail.kind=="blend")"Blend bersama" else "Undangan kolaborator",style=typo().bodySmall)}
        }
        Text("Gratify",Modifier.align(Alignment.End),style=typo().titleMedium,fontWeight=FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorkspaceCoverChooser(detail:PlaylistWorkspace,owner:String?,draft:String?,reset:Boolean,
    busy:Boolean,onPick:()->Unit,onReset:()->Unit,onClose:()->Unit) {
    ModalBottomSheet(onDismissRequest={if(!busy)onClose()},containerColor=GratifyColors.Surface,
        sheetState=com.tan.gratify.ui.component.rememberBusyModalSheetState(busy)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text("Gambar sampul",style=typo().titleLarge,fontWeight=FontWeight.Bold)
            WorkspaceCover(detail.copy(coverUrl=if(reset)null else draft?:detail.coverUrl,coverPath=if(reset)null else detail.coverPath),owner,Modifier.widthIn(max=320.dp).fillMaxWidth().aspectRatio(1f))
            Button(onClick=onPick,enabled=!busy,shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),modifier=Modifier.heightIn(min=52.dp)) {Text("Ubah gambar sampul",fontWeight=FontWeight.Bold)}
            TextButton(onClick={onReset();onClose()},enabled=!busy){Text("Gunakan sampul otomatis")}
            Text("Pilih foto, lalu geser dan cubit untuk mengatur crop persegi.",style=typo().bodySmall,color=GratifyColors.TextSecondary,textAlign=TextAlign.Center)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
