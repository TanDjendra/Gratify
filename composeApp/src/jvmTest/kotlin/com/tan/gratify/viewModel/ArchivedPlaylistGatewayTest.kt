package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.data.sync.AccountAccess
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import com.tan.domain.utils.PlaylistFeaturePolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import java.lang.reflect.Proxy
import kotlin.test.*

class ArchivedPlaylistGatewayTest {
    private class Gateway:PlaylistWorkspaceGateway {
        val calls=mutableListOf<String>();var kind="collaborative"
        override fun currentOwner()="owner"
        override suspend fun call(action:String,args:JsonObject):JsonElement {
            calls+=action
            return if(action=="detail")Json.parseToJsonElement("""{"id":"p","owner_id":"owner","kind":"$kind","title":"Keep me","member":true,"items":[],"members":[]} """)
            else buildJsonObject{}
        }
    }
    private inline fun <reified T> stub(crossinline reply:(String)->Any?):T=Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,m,_->reply(m.name)} as T
    private suspend fun fixture(block:suspend(Gateway,PlaylistWorkspaceViewModel)->Unit) {
        val gateway=Gateway()
        val model=PlaylistWorkspaceViewModel(gateway,MutableStateFlow<AccountAccess>(AccountAccess.Ready("owner",false,false)),
            stub<SearchRepository>{error(it)},stub<LocalPlaylistRepository>{when(it){"getAllLocalPlaylists"->flowOf(emptyList<LocalPlaylistEntity>());"cacheCanonicalPlaylist"->1L;else->error("Unexpected cache action: $it")}},
            stub<DataStoreManager>{flowOf(null)},stub<UserRepository>{error(it)})
        try{withTimeout(3000){while(!model.state.value.accountReady||model.state.value.loading)delay(10)};block(gateway,model)}finally{model.viewModelScope.cancel()}
    }
    @Test fun archivedInvitationsAndFoldersNeverReachServer():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,model->
            assertTrue(gateway.calls.isEmpty())
            model.visible(token="a".repeat(64));delay(30)
            model.makeInvite("p");model.join("a".repeat(64),true){}
            model.promoteLocal(7){};model.create("blend","Blend","draft",true){}
            model.create("collaborative","Together","draft",false){}
            model.folder("folder_create",null,title="Folder")
            model.action("revoke_invites","p")
            delay(30);assertTrue(gateway.calls.isEmpty());assertEquals(PlaylistFeaturePolicy.archivedMessage,model.state.value.error)
        }
    }
    @Test fun archivedModeRetainsCanonicalPlaylistReadAndOrdinaryMetadataWrites():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,model->
            model.visible("p");withTimeout(3000){while(model.state.value.detail==null)delay(10)}
            assertEquals("Keep me",model.state.value.detail!!.title)
            var saved=false;model.action("metadata","p",buildJsonObject{put("title","Updated")}){saved=true}
            withTimeout(3000){while(!saved)delay(10)}
            assertTrue("metadata" in gateway.calls);assertFalse("library" in gateway.calls)
        }
    }
    @Test fun retainedBlendDoesNotRefreshOrPopulateOrdinaryPlaylistCache():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,model->gateway.kind="blend";model.visible("p")
            withTimeout(3000){while(model.state.value.detail==null)delay(10)}
            assertEquals(listOf("detail"),gateway.calls);assertEquals("blend",model.state.value.detail!!.kind)
        }
    }
}
