package com.tan.gratify.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.data.sync.AccountAccess
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import com.tan.gratify.ui.component.rememberBusyModalSheetState
import com.tan.gratify.ui.screen.library.WorkspacePeopleSheet
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import java.lang.reflect.Proxy
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class,ExperimentalMaterial3Api::class)
class ModalSheetStabilityTest {
    private var frame=0L
    private suspend fun tick(s:ImageComposeScene,count:Int=24){repeat(count){frame+=16_000_000;s.render(frame).close();delay(12)}}
    private fun tree(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::tree)
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{tree(it.rootSemanticsNode)}
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.map{it.text}.orEmpty()
    private fun click(s:ImageComposeScene,label:String)=nodes(s).first{label in text(it)&&it.config.contains(SemanticsActions.OnClick)}.config[SemanticsActions.OnClick].action!!.invoke()

    @Test fun changingBusyCallbackReproducesMaterialSheetReset():Unit=runBlocking(Dispatchers.Swing){
        val busy=mutableStateOf(false);var observed:SheetState?=null
        val scene=ImageComposeScene(390,850,Density(1f),coroutineContext=coroutineContext){AppTheme{
            val saving=busy.value
            val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={!saving||it!=SheetValue.Hidden})
            SideEffect{observed=sheet}
            ModalBottomSheet({},sheetState=sheet){Column(androidx.compose.ui.Modifier.height(240.dp)){Text("Reproduction")}}
        }}
        try{
            tick(scene);val before=assertNotNull(observed);assertTrue(before.isVisible)
            busy.value=true;tick(scene,1)
            assertNotSame(before,observed,"The previous guard replaces SheetState on a loading transition")
        }finally{scene.close()}
    }

    @Test fun busyGuardKeepsSheetIdentityAndUsesLatestDismissPermission():Unit=runBlocking(Dispatchers.Swing){
        for(partial in listOf(false,true)){
            val busy=mutableStateOf(false);val result=mutableStateOf("Initial");var observed:SheetState?=null;var uiScope:CoroutineScope?=null
            val scene=ImageComposeScene(320,850,Density(1f,1.5f),coroutineContext=coroutineContext){AppTheme{
                val sheet=rememberBusyModalSheetState(busy.value,skipPartiallyExpanded=!partial)
                val scope=rememberCoroutineScope()
                SideEffect{observed=sheet;uiScope=scope}
                ModalBottomSheet({},sheetState=sheet){Column(androidx.compose.ui.Modifier.height(600.dp)){Text(result.value)}}
            }}
            try{
                tick(scene);val before=assertNotNull(observed);assertTrue(before.isVisible)
                for(value in listOf(true,false,true,false)){
                    busy.value=value;result.value="Updated $value";tick(scene,1)
                    assertSame(before,observed,"Loading and results must not recreate the sheet")
                    assertTrue(before.isVisible)
                }
                suspend fun hide(){val job=uiScope!!.launch{before.hide()};withTimeout(5000){while(!job.isCompleted)tick(scene,2)};job.join()}
                busy.value=true;tick(scene,1);hide();tick(scene,1);assertTrue(before.isVisible,"Saving vetoes swipe/programmatic dismissal")
                busy.value=false;tick(scene,1);hide();tick(scene);assertFalse(before.isVisible,"Dismissal works again after saving")
            }finally{scene.close()}
        }
    }

    private class Gateway:PlaylistWorkspaceGateway{
        var blocking="invite"
        var gate=CompletableDeferred<Unit>()
        val writes=mutableListOf<String>()
        var name="Owner"
        var failDetail=false
        override fun currentOwner()="owner"
        override suspend fun call(action:String,args:JsonObject):JsonElement{
            if(action !in listOf("library","detail"))writes+=action
            if(action==blocking)gate.await()
            return when(action){
                "library"->Json.parseToJsonElement("""{"playlists":[],"folders":[],"placements":[]}""")
                "detail"->{if(failDetail)error("Synthetic offline");Json.parseToJsonElement("""{"id":"p","owner_id":"owner","kind":"collaborative","title":"Together","member":true,"members":[{"user_id":"owner","name":"$name","owner":true}]}""")}
                "invite"->buildJsonObject{put("token","a".repeat(64))}
                else->buildJsonObject{}
            }
        }
    }
    private inline fun <reified T> stub(crossinline reply:(String)->Any?):T=Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,_->reply(method.name)} as T

    @Test fun inviteRevokeAndPollingDoNotRecreateInvitationOrDuplicateRequests():Unit=runBlocking(Dispatchers.Swing){
        val gateway=Gateway()
        val model=PlaylistWorkspaceViewModel(gateway,MutableStateFlow<AccountAccess>(AccountAccess.Ready("owner",false,false)),
            stub<SearchRepository>{error("Unused search")},stub<LocalPlaylistRepository>{name->when(name){"reconcileCanonicalPlaylists"->Unit;"cacheCanonicalPlaylist"->1L;else->flowOf(emptyList<com.tan.domain.data.entities.LocalPlaylistEntity>())}},
            stub<DataStoreManager>{flowOf(null)},stub<UserRepository>{flowOf(emptyList<com.tan.domain.data.entities.UserProfile>())},experimentalFeaturesEnabled=true)
        val show=mutableStateOf(true);val startInvitation=mutableStateOf(true);var closes=0
        model.visible(id="p")
        val scene=ImageComposeScene(390,950,Density(1f),coroutineContext=coroutineContext){AppTheme{
            val state by model.state.collectAsState()
            if(show.value)state.detail?.let{WorkspacePeopleSheet(it,state,model,{closes++;show.value=false},startInviting=startInvitation.value,
                onInvitationStarted={startInvitation.value=false})}
        }}
        suspend fun await(condition:()->Boolean)=withTimeout(5000){while(!condition())tick(scene,2)}
        try{
            await{model.state.value.busy&&gateway.writes.count{it=="invite"}==1};tick(scene)
            assertEquals(1,gateway.writes.count{it=="invite"})
            gateway.gate.complete(Unit);await{!model.state.value.busy&&model.state.value.inviteLink!=null};tick(scene)
            assertTrue(nodes(scene).any{"Undang ke playlist" in text(it)})
            gateway.blocking="revoke_invites";gateway.gate=CompletableDeferred()
            click(scene,"Cabut semua undangan");await{model.state.value.busy};tick(scene)
            assertEquals(1,gateway.writes.count{it=="revoke_invites"})
            gateway.gate.complete(Unit);await{!model.state.value.busy&&model.state.value.inviteLink==null};tick(scene)
            assertTrue(nodes(scene).any{"Orang" in text(it)});assertFalse(nodes(scene).any{"Undang ke playlist" in text(it)})
            repeat(3){gateway.name="Owner $it";model.refresh(quiet=true);tick(scene)}
            gateway.failDetail=true;model.refresh(quiet=true);await{model.state.value.detail==null&&model.state.value.error!=null};tick(scene)
            gateway.failDetail=false;model.refresh(quiet=true);await{model.state.value.detail!=null};tick(scene)
            assertTrue(nodes(scene).any{"Orang" in text(it)})
            assertEquals(1,gateway.writes.count{it=="invite"},"Revocation/polling must not auto-create another link")
            assertEquals(1,gateway.writes.count{it=="revoke_invites"});assertEquals(0,closes)
            val close=nodes(scene).first{it.config.getOrNull(SemanticsProperties.ContentDescription)?.any{label->label in listOf("Close sheet","Tutup sheet")}==true&&it.config.contains(SemanticsActions.OnClick)}
            close.config[SemanticsActions.OnClick].action!!.invoke();tick(scene);assertEquals(1,closes);assertFalse(show.value)
        }finally{scene.close();model.viewModelScope.cancel()}
    }
}
