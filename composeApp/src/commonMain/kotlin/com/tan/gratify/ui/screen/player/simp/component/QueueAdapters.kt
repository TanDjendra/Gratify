package com.tan.gratify.ui.screen.player.simp.component
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tan.domain.data.model.browse.album.Track
import com.tan.gratify.ui.screen.player.simp.content.*
import com.tan.gratify.ui.screen.player.simp.theme.typo
import com.tan.gratify.ui.screen.player.simp.icon.*
import coil3.compose.AsyncImage

@Composable fun SongFullWidthItems(track: Track, isPlaying: Boolean, modifier: Modifier,
    onClickListener: (String) -> Unit, onMoreClickListener: (String) -> Unit) {
    Row(modifier.clickable { onClickListener(track.videoId) }.padding(vertical = 6.dp, horizontal = 15.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(8.dp))
        AsyncImage(track.thumbnails?.lastOrNull()?.url, null, placeholder = rememberHolderPainter(), error = rememberHolderPainter(),
            contentScale = ContentScale.FillWidth, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Text(track.title, style = typo().titleSmall, color = Color.White, maxLines = 1, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) { ExplicitBadge(Modifier.size(20.dp).padding(1.dp)); Spacer(Modifier.width(5.dp)) }
                Text(track.artists?.joinToString(", ") { it.name }.orEmpty(), style = typo().bodySmall,
                    color = Color(0xC4FFFFFF), maxLines = 1, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
            }
        }
        RippleIconButton(SimpIcons.MoreVert, tint = Color.White, contentDescription = "Track options") { onMoreClickListener(track.videoId) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun QueueItemActionsSheet(state: NowPlayingContentState, actions: NowPlayingContentActions,
    index: Int, videoId: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF242424)) {
        val valid = state.artworkQueue.getOrNull(index)?.videoId == videoId
        if (index > 0) TextButton({ if (valid) actions.onMoveQueueItem(index,index-1); onDismiss() }, Modifier.fillMaxWidth()) { Text("Move up") }
        if (index < state.artworkQueue.lastIndex) TextButton({ if (valid) actions.onMoveQueueItem(index,index+1); onDismiss() }, Modifier.fillMaxWidth()) { Text("Move down") }
        TextButton({ if (valid) actions.onRemoveQueueItem(index); onDismiss() }, Modifier.fillMaxWidth()) { Text("Remove from queue") }
    }
}
