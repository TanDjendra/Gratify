package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.UserRepository
import com.tan.domain.utils.LocalResource
import com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination
import com.tan.gratify.viewModel.PlaylistWorkspaceViewModel
import com.tan.gratify.viewModel.WorkspaceMember
import com.tan.gratify.viewModel.PlaylistWorkspaceGateway
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Local row IDs address a cache; member access never creates a second route. */
@OptIn(ExperimentalFoundationApi::class,ExperimentalMaterial3Api::class)
@Composable fun UnifiedPlaylistScreen(localId:Long,nav:NavController,
    playlists:LocalPlaylistRepository=koinInject(),users:UserRepository=koinInject(),
    model:PlaylistWorkspaceViewModel=koinViewModel(key="unified-playlist-$localId")) {
    val state by model.state.collectAsStateWithLifecycle()
    val resource by remember(localId,state.owner){playlists.getLocalPlaylist(localId)}.collectAsStateWithLifecycle(LocalResource.Loading())
    val playlist=(resource as? LocalResource.Success)?.data
    var promotedId by rememberSaveable(localId,state.owner){mutableStateOf<String?>(null)}
    var openInvite by rememberSaveable(localId,state.owner){mutableStateOf(false)}
    val canonicalId=playlist?.canonicalPlaylistId?:promotedId
    if(canonicalId!=null) {
        PlaylistWorkspaceScreen(PlaylistWorkspaceDestination("detail",canonicalId,inviteOnOpen=openInvite),nav,model=model,localCacheId=localId)
    } else {
        if(!com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled) {
            LocalPlaylistScreen(id=localId,navController=nav);return
        }
        LifecycleStartEffect(model){model.visible();onStopOrDispose{model.hidden()}}
        val profile by remember(state.owner){state.owner?.let{users.getUserProfile(it)}?:kotlinx.coroutines.flow.flowOf(null)}.collectAsStateWithLifecycle(null)
        Column(Modifier.fillMaxSize()) {
            if(state.busy||state.error!=null)WorkspaceFeedback(state,{model.clearFeedback()})
            LocalPlaylistScreen(id=localId,navController=nav,collaborationBusy=state.busy,
                ownerMember=WorkspaceMember(state.owner.orEmpty(),profile?.displayName?:"Kamu",profile?.avatarUrl,owner=true),
                canInvite=state.accountReady,onInvite={model.promoteLocal(localId){promotedId=it;openInvite=true}})
        }
        if(state.busy)com.tan.gratify.ui.component.LoadingDialog(true,state.busyMessage)
    }
}

@OptIn(ExperimentalFoundationApi::class,ExperimentalMaterial3Api::class)
@Composable fun CanonicalSharedPlaylistScreen(sharedId:String,nav:NavController,gateway:PlaylistWorkspaceGateway=koinInject()) {
    var canonicalId by remember(sharedId,gateway.currentOwner()){mutableStateOf<String?>(null)}
    var resolved by remember(sharedId,gateway.currentOwner()){mutableStateOf(false)}
    LaunchedEffect(sharedId,gateway.currentOwner()) {
        val owner=gateway.currentOwner()
        try {
            val id=kotlinx.coroutines.withTimeout(5_000){
                val candidate=gateway.call("resolve_shared",buildJsonObject{put("shared_id",sharedId)}).jsonObject["id"]?.jsonPrimitive?.contentOrNull
                // Public listeners retain the existing save-to-library flow; members edit the same canonical playlist.
                candidate?.takeIf{gateway.call("detail",buildJsonObject{put("playlist_id",it)}).jsonObject["member"]?.jsonPrimitive?.booleanOrNull==true}
            }
            if(owner==gateway.currentOwner())canonicalId=id
        }catch(e:kotlinx.coroutines.CancellationException){if(e !is kotlinx.coroutines.TimeoutCancellationException)throw e}
        catch(_:Exception){}
        resolved=true
    }
    if(canonicalId!=null)PlaylistWorkspaceScreen(PlaylistWorkspaceDestination("detail",requireNotNull(canonicalId)),nav)
    else if(resolved)com.tan.gratify.ui.screen.playlist.SharedPlaylistScreen(sharedId,navController=nav)
    else Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()}
}
