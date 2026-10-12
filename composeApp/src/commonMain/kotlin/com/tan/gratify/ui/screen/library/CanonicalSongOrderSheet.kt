package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.DraggableItem
import com.tan.gratify.ui.component.rememberDragDropState
import com.tan.gratify.ui.component.rememberBusyModalSheetState
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.PlaylistWorkspace
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable internal fun CanonicalSongOrderSheet(detail:PlaylistWorkspace,busy:Boolean,error:String?,
    onClose:()->Unit,onSave:(List<String>,Long)->Unit) {
    var songs by remember(detail.id){mutableStateOf(detail.items)}
    var revision by remember(detail.id){mutableStateOf(detail.revision)}
    val stale=revision!=detail.revision
    val list=rememberLazyListState();val scope=rememberCoroutineScope()
    fun move(from:Int,to:Int){if(!busy&&!stale&&from in songs.indices&&to in songs.indices)songs=songs.toMutableList().apply{add(to,removeAt(from))}}
    val moveCurrent by rememberUpdatedState<(Int,Int)->Unit>(::move)
    val drag=rememberDragDropState(list){from,to->moveCurrent(from,to)}
    ModalBottomSheet(onDismissRequest={if(!busy)onClose()},sheetState=rememberBusyModalSheetState(busy),containerColor=GratifyColors.Surface) {
        WorkspaceSheetHeader("Edit lagu","Batal","Simpan",busy,!stale,onClose,{onSave(songs.map{it.id},revision)})
        Text("Tahan dan geser pegangan untuk mengubah urutan lagu.",Modifier.padding(16.dp),color=GratifyColors.TextSecondary)
        error?.let{Text(it,Modifier.padding(horizontal=16.dp),color=GratifyColors.Error)}
        if(stale) {
            Text("Playlist diubah anggota lain. Muat urutan terbaru sebelum menyimpan.",Modifier.padding(16.dp),color=GratifyColors.TextSecondary)
            TextButton(onClick={songs=detail.items;revision=detail.revision},enabled=!busy){Text("Muat urutan terbaru")}
        }
        LazyColumn(state=list,modifier=Modifier.fillMaxWidth().heightIn(max=440.dp).pointerInput(busy,stale) {
            if(busy||stale)return@pointerInput
            detectDragGesturesAfterLongPress(onDragStart=drag::onDragStart,onDragEnd=drag::onDragInterrupted,onDragCancel=drag::onDragInterrupted,
                onDrag={change,offset->change.consume();drag.onDrag(offset);val scroll=drag.checkForOverScroll();if(scroll!=0f)scope.launch{list.animateScrollBy(scroll)}})
        }) {
            itemsIndexed(songs,key={_,song->song.id}) { index,song->
                DraggableItem(drag,index,Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text(song.title,maxLines=1);Text(song.artist,color=GratifyColors.TextSecondary,maxLines=1)}
                        IconButton(onClick={move(index,index-1)},enabled=!busy&&!stale&&index>0){Icon(Icons.Rounded.KeyboardArrowUp,"Naikkan ${song.title}")}
                        IconButton(onClick={move(index,index+1)},enabled=!busy&&!stale&&index<songs.lastIndex){Icon(Icons.Rounded.KeyboardArrowDown,"Turunkan ${song.title}")}
                        Icon(Icons.Rounded.DragHandle,"Geser ${song.title}",Modifier.padding(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
