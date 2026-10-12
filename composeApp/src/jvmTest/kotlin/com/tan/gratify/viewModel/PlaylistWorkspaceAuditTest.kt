package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.data.sync.AccountAccess
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import java.lang.reflect.Proxy
import kotlin.test.*

class PlaylistWorkspaceAuditTest {
    private class Gateway: PlaylistWorkspaceGateway {
        var owner: String? = "owner-a"
        var blocked: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var library = """{"playlists":[],"folders":[],"placements":[]}"""
        var failSend = false
        var failDetail = false
        var detailFailure = "Playlist unavailable"
        var failMetadata=false
        var failCoverUrl=false
        var uploads=0
        var cover:String?=null
        var detailTitle="Together"
        val cleaned=mutableListOf<String>()
        val writes = mutableListOf<Pair<String,JsonObject>>()
        override fun currentOwner() = owner
        override suspend fun call(action: String, args: JsonObject): JsonElement {
            if (action !in listOf("library","detail","preview","next_name")) writes += action to args
            val captured = library
            if(action==blocked) gate?.await()
            return when(action) {
                "library" -> Json.parseToJsonElement(captured)
                "create" -> buildJsonObject { put("id", args.getValue("id")) }
                "invite" -> buildJsonObject { put("token", "a".repeat(64)) }
                "send_invite" -> { if(failSend) error("synthetic connection lost"); buildJsonObject {put("id","message")} }
                "preview" -> Json.parseToJsonElement("""{"id":"p","title":"Together","kind":"collaborative"}""")
                "detail" -> { if(failDetail) error(detailFailure); buildJsonObject{put("id","p");put("owner_id","owner-a");put("kind","collaborative");put("title",detailTitle);put("member",true);put("cover_path",cover?.let(::JsonPrimitive)?:JsonNull)} }
                "metadata" -> { if(failMetadata)error("synthetic offline");detailTitle=args["title"]!!.jsonPrimitive.content;if(args.containsKey("cover_path"))cover=args["cover_path"]!!.jsonPrimitive.contentOrNull;buildJsonObject{} }
                else -> buildJsonObject {}
            }
        }
        override suspend fun coverUrl(path:String):String?=if(failCoverUrl)null else "https://example.invalid/$path"
        override suspend fun uploadCover(owner:String,id:String,uri:String):String {
            uploads++;if(blocked=="upload")gate?.await();return "$owner/$id/upload.jpg"
        }
        override suspend fun removeUnusedCover(path:String){cleaned+=path}
    }
    private inline fun <reified T> stub(crossinline answer: (String,Array<out Any?>?) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader,arrayOf(T::class.java)) { _, method, args -> answer(method.name,args) } as T
    private suspend fun await(condition: () -> Boolean) = withTimeout(4000) { while(!condition()) delay(10) }
    private suspend fun fixture(block: suspend (Gateway,MutableStateFlow<AccountAccess>,PlaylistWorkspaceViewModel)->Unit) {
        val gateway=Gateway(); val access=MutableStateFlow<AccountAccess>(AccountAccess.Ready("owner-a",false,false))
        val preferences=mutableMapOf<String,String>()
        val model=PlaylistWorkspaceViewModel(gateway,access,stub<SearchRepository>{_,_-> error("Unused search")},
            stub<LocalPlaylistRepository>{name,_-> when(name){"getAllLocalPlaylists"->flowOf(emptyList<com.tan.domain.data.entities.LocalPlaylistEntity>());"reconcileCanonicalPlaylists"->Unit;"cacheCanonicalPlaylist"->1L;else->error(name)}},
            stub<DataStoreManager>{name,args->when(name){"getString"->flowOf(preferences[args!![0]]);"putString"->{preferences[args!![0] as String]=args[1] as String;Unit};else->error(name)}},
            stub<UserRepository>{_,_->flowOf(emptyList<com.tan.domain.data.entities.UserProfile>())},experimentalFeaturesEnabled=true)
        try { await {model.state.value.accountReady&&!model.state.value.loading}; block(gateway,access,model) }
        finally {model.viewModelScope.cancel()}
    }
    @Test fun repeatedCreateTapsAndAccountChangeNeverPublishOldSuccess(): Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,access,model ->
            gateway.blocked="create"; gateway.gate=CompletableDeferred(); var navigations=0
            model.create("collaborative","Together","p",false){navigations++}
            model.create("collaborative","Together","p",false){navigations++}
            await {gateway.writes.size==1}; assertTrue(model.state.value.busy)
            gateway.owner="owner-b"; access.value=AccountAccess.Ready("owner-b",false,false)
            await {model.state.value.owner=="owner-b"}; gateway.gate!!.complete(Unit); delay(40)
            assertEquals(0,navigations); assertNull(model.state.value.success); assertFalse(model.state.value.busy)
        }
    }
    @Test fun oldAccountLibraryAndInvitationAreClearedOnLogout(): Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,access,model ->
            model.visible(token="a".repeat(64)); await {model.state.value.invitation!=null}
            gateway.owner=null; access.value=AccountAccess.SignedOut
            await {!model.state.value.accountReady}
            assertNull(model.state.value.invitation); assertNull(model.state.value.detail); assertNull(model.state.value.inviteLink)
        }
    }
    @Test fun lostChatConfirmationReusesOperationAndCannotSendToAnotherRecipient(): Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,_,model ->
            model.makeInvite("p","peer-b"); await {model.state.value.inviteLink!=null}
            model.sendInvite("peer-c"); delay(20); assertTrue(gateway.writes.none{it.first=="send_invite"})
            gateway.failSend=true; model.sendInvite("peer-b"); await {model.state.value.error!=null&&!model.state.value.busy}
            assertNotNull(model.state.value.inviteLink)
            gateway.failSend=false; model.sendInvite("peer-b"); await {model.state.value.inviteLink==null&&!model.state.value.busy}
            val sends=gateway.writes.filter{it.first=="send_invite"}
            assertEquals(2,sends.size); assertEquals(sends[0].second,sends[1].second)
        }
    }
    @Test fun revokedAccessClearsPreviouslyVisibleSongsAndMembers(): Unit=runBlocking(Dispatchers.Swing) {
        fixture { gateway,_,model ->
            model.visible(id="p"); await {model.state.value.detail!=null}
            gateway.failDetail=true; model.refresh(); await {model.state.value.error!=null&&!model.state.value.loading}
            assertNull(model.state.value.detail)
        }
    }
    @Test fun nameSuggestionsUsePersonalAndSharedLanguageAndSurviveNewRequests(): Unit=runBlocking(Dispatchers.Swing) {
        fixture { _,_,model ->
            assertEquals("Playlist-ku No.1",model.nextName("personal"))
            assertEquals("Playlist kami No.1",model.nextName("collaborative"))
            assertEquals("Blend kami",model.nextName("blend"))
            assertEquals("Folder baru",model.nextName("folder"))
            assertEquals("Playlist-ku No.1",model.nextName("personal"))
            assertEquals("Playlist kami No.1",model.nextName("collaborative"))
        }
    }
    @Test fun failedCoverCommitKeepsUploadForRetryAndDoesNotClaimSuccess():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,_,model->
            model.visible(id="p");await{model.state.value.detail!=null}
            gateway.failMetadata=true;var closes=0
            model.saveDetails("p","New name","",false,"local-cropped.jpg",false){closes++}
            await{model.state.value.error!=null&&!model.state.value.busy}
            assertEquals(0,closes);assertEquals(1,gateway.uploads);assertEquals("Together",model.state.value.detail?.title);assertNull(model.state.value.success)
            gateway.failMetadata=false
            model.saveDetails("p","New name","",false,"local-cropped.jpg",false){closes++}
            await{closes==1&&!model.state.value.busy}
            assertEquals(1,gateway.uploads);assertEquals("New name",model.state.value.detail?.title)
            val writes=gateway.writes.filter{it.first=="metadata"};assertEquals(writes[0].second,writes[1].second)
        }
    }
    @Test fun accountChangeDuringCoverUploadCannotCommitMetadata():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,access,model->
            model.visible(id="p");await{model.state.value.detail!=null}
            gateway.blocked="upload";gateway.gate=CompletableDeferred();var closes=0
            model.saveDetails("p","New name","",false,"local-cropped.jpg",false){closes++}
            await{gateway.uploads==1};assertEquals("Mengunggah sampul…",model.state.value.busyMessage)
            gateway.owner="owner-b";access.value=AccountAccess.Ready("owner-b",false,false)
            await{model.state.value.owner=="owner-b"};gateway.gate!!.complete(Unit);delay(30)
            assertEquals(0,closes);assertTrue(gateway.writes.none{it.first=="metadata"});assertFalse(model.state.value.busy)
        }
    }
    @Test fun resettingCoverUsesNoUploadAndCleansConfirmedOldFile():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,_,model->
            gateway.cover="old.jpg";model.visible(id="p");await{model.state.value.detail?.coverPath=="old.jpg"}
            var saved=false;model.saveDetails("p","Together","",false,null,true){saved=true}
            await{saved&&gateway.cleaned.isNotEmpty()};assertEquals(listOf("old.jpg"),gateway.cleaned);assertEquals(0,gateway.uploads)
            assertNull(model.state.value.detail?.coverPath)
        }
    }
    @Test fun temporaryImageLinkFailureDoesNotFlashAwayAuthorizedCover():Unit=runBlocking(Dispatchers.Swing) {
        fixture{gateway,_,model->
            gateway.cover="cover.jpg";model.visible(id="p");await{model.state.value.detail?.coverUrl!=null}
            val visible=model.state.value.detail!!.coverUrl;gateway.failCoverUrl=true
            model.refresh();await{!model.state.value.loading}
            assertEquals(visible,model.state.value.detail?.coverUrl);assertNull(model.state.value.error)
        }
    }
    @Test fun quietRecoveryClearsPreviousReadFailure():Unit=runBlocking(Dispatchers.Swing){
        fixture{gateway,_,model->
            model.visible(id="p");await{model.state.value.detail!=null}
            gateway.failDetail=true;gateway.detailFailure="JWT expired"
            model.refresh();await{model.state.value.error!=null&&!model.state.value.loading}
            assertFalse("Undangan" in model.state.value.error.orEmpty())
            gateway.failDetail=false;model.refresh(quiet=true);await{model.state.value.detail!=null}
            assertNull(model.state.value.error,"A successful poll must clear its previous read error")
        }
    }
    @Test fun quietReadDoesNotEraseFailedSaveFeedback():Unit=runBlocking(Dispatchers.Swing){
        fixture{gateway,_,model->
            model.visible(id="p");await{model.state.value.detail!=null}
            gateway.failMetadata=true;model.saveDetails("p","Changed","",false,null,false){}
            await{model.state.value.error!=null&&!model.state.value.busy};val failure=model.state.value.error
            gateway.detailTitle="Read confirmed";model.refresh(quiet=true);await{model.state.value.detail?.title=="Read confirmed"}
            assertEquals(failure,model.state.value.error)
        }
    }
    @Test fun onlyInvitationExpiryIsReportedAsExpiredInvitation(){
        assertTrue("Undangan" in workspaceFailure(IllegalStateException("Invitation expired or unavailable")))
        assertTrue("Undangan" in workspaceFailure(IllegalStateException("Invitation unavailable")))
        assertFalse("Undangan" in workspaceFailure(IllegalStateException("JWT expired")))
        assertFalse("Undangan" in workspaceFailure(IllegalStateException("Request timeout expired")))
    }
}
