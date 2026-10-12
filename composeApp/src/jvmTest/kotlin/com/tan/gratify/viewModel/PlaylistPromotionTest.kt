package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.data.sync.AccountAccess
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import java.lang.reflect.Proxy
import kotlin.test.*

class PlaylistPromotionTest {
    private class Gateway:PlaylistWorkspaceGateway {
        var owner:String?="owner";val writes=mutableListOf<Pair<String,JsonObject>>()
        var blocked:CompletableDeferred<Unit>?=null
        var alreadyLinked=false
        override fun currentOwner()=owner
        override suspend fun call(action:String,args:JsonObject):JsonElement {
            if(action=="promote") { writes+=action to args;blocked?.await();return buildJsonObject{put("id","canonical-id")} }
            return when(action){"library"->buildJsonObject{put("playlists",JsonArray(emptyList()));put("folders",JsonArray(emptyList()));put("placements",JsonArray(emptyList()))}
                "resolve_personal"->buildJsonObject{put("id",if(alreadyLinked)JsonPrimitive("canonical-id") else JsonNull)};else->buildJsonObject{}}
        }
    }
    private inline fun <reified T> stub(crossinline reply:(String)->Any?):T=Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,_->reply(method.name)} as T
    private suspend fun fixture(block:suspend(Gateway,MutableStateFlow<AccountAccess>,PlaylistWorkspaceViewModel,MutableList<String>)->Unit) {
        val gateway=Gateway();val access=MutableStateFlow<AccountAccess>(AccountAccess.Ready("owner",false,false));val syncs=mutableListOf<String>()
        val repo=stub<LocalPlaylistRepository>{when(it){"getAllLocalPlaylists"->flowOf(listOf(LocalPlaylistEntity(id=7,title="Keep my name",thumbnail="cover.jpg",syncId="original-sync-id")))
            "ensureOwnedPlaylistSyncId"->"original-sync-id";"reconcileCanonicalPlaylists"->Unit;else->error(it)}}
        val social=stub<SocialRepository>{when(it){"syncUpPlaylist"->flow<Result<String>>{syncs+="snapshot confirmed";emit(Result.success("canonical-id"))};else->error(it)}}
        val model=PlaylistWorkspaceViewModel(gateway,access,stub<SearchRepository>{error(it)},repo,
            stub<DataStoreManager>{flowOf(null)},stub<UserRepository>{error(it)},social,experimentalFeaturesEnabled=true)
        try{withTimeout(3000){while(!model.state.value.accountReady||model.state.value.loading)delay(10)};block(gateway,access,model,syncs)}
        finally{model.viewModelScope.cancel()}
    }
    @Test fun invitationPreparationSyncsFirstAndNeverRenamesPublishesOrCopies():Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,_,model,syncs ->
            var result:String?=null;model.promoteLocal(7){result=it}
            withTimeout(3000){while(result==null)delay(10)}
            assertEquals(listOf("snapshot confirmed"),syncs);assertEquals("canonical-id",result)
            assertEquals(buildJsonObject{put("sync_id","original-sync-id")},gateway.writes.single().second)
        }
    }
    @Test fun repeatedTapsAndAccountSwitchCannotDeliverOldInvitation():Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,access,model,_ ->
            gateway.blocked=CompletableDeferred();var opened=0
            model.promoteLocal(7){opened++};model.promoteLocal(7){opened++}
            withTimeout(3000){while(gateway.writes.isEmpty())delay(10)};assertEquals(1,gateway.writes.size)
            gateway.owner="other";access.value=AccountAccess.Ready("other",false,false)
            withTimeout(3000){while(model.state.value.owner!="other")delay(10)}
            gateway.blocked!!.complete(Unit);delay(50);assertEquals(0,opened)
        }
    }
    @Test fun retryAfterLostConfirmationCannotReimportStalePersonalSnapshot():Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,_,model,syncs ->
            gateway.alreadyLinked=true;var result:String?=null
            model.promoteLocal(7){result=it}
            withTimeout(3000){while(result==null)delay(10)}
            assertEquals("canonical-id",result);assertTrue(syncs.isEmpty())
            assertEquals(1,gateway.writes.size)
        }
    }
}
