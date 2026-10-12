package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.gratify.ui.theme.GratifyColors

internal fun automaticArtworkCells(images:List<String>):List<String> =
    images.take(4).let{if(it.size<4)it.take(1) else it}

/** Stored song order determines the cover, independent of temporary UI sorting. */
@Composable fun PlaylistAutomaticArtwork(images:List<String>,modifier:Modifier=Modifier,title:String="",
    onSuccessFirstCell:((coil3.request.SuccessResult)->Unit)?=null) {
    val cells=remember(images){automaticArtworkCells(images)}
    Box(modifier.background(GratifyColors.SurfaceRaised).semantics {
        contentDescription=if(cells.size==4)"Sampul otomatis: 4 lagu teratas $title" else "Sampul otomatis $title"
    },contentAlignment=Alignment.Center) {
        @Composable fun cell(url:String,cellModifier:Modifier,first:Boolean=false) {
            val context=LocalPlatformContext.current
            val request=remember(url,context){ImageRequest.Builder(context).data(url)
                .memoryCacheKey(url).diskCacheKey(url).crossfade(false).build()}
            Box(cellModifier,contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.MusicNote,null,Modifier.size(24.dp),tint=GratifyColors.TextSecondary)
                AsyncImage(request,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,
                    onSuccess={if(first)onSuccessFirstCell?.invoke(it.result)})
            }
        }
        if(cells.isEmpty())Icon(Icons.Rounded.MusicNote,null,Modifier.size(32.dp),tint=GratifyColors.TextSecondary)
        else if(cells.size==1)cell(cells.first(),Modifier.fillMaxSize(),true)
        else Column(Modifier.fillMaxSize()) {repeat(2){row->Row(Modifier.weight(1f)){repeat(2){col->
            cell(cells[row*2+col],Modifier.weight(1f).fillMaxHeight(),row==0&&col==0)
        }}}}
    }
}
