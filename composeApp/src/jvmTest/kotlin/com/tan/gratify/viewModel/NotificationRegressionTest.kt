package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.*
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.Json
import org.koin.core.context.*
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class NotificationRegressionTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,method,args -> answer(method.name,args.orEmpty()) } as T
    private suspend fun waitFor(predicate:()->Boolean) = withTimeout(4000) { while(!predicate())delay(10) }
    private class Fixture {
        val rows=MutableStateFlow<List<NotificationEntity>?>(emptyList())
        val seen=mutableSetOf<String>()
        var followWrites=0
        var followGate:CompletableDeferred<Unit>?=null
        var followFails=false
        var followerReads=0
        var readFails=false
        var deleteFails=false
        var deletes=0
        var remoteFails=true
        var remoteCalls=0
        var remoteGate:CompletableDeferred<Unit>?=null
        var socialFails=false
        var followingFails=false
        var followingRows=emptyList<UserProfile>()
        var socialRows=emptyList<NotificationEntity>()
        var databaseOwner="owner"
        lateinit var switchUser: suspend (String) -> Unit
    }
    private suspend fun fixture(setup:(Fixture)->Unit = {}, block:suspend(Fixture,NotificationViewModel)->Unit) {
        val f=Fixture().also(setup)
        val client=createSupabaseClient("http://127.0.0.1:9","synthetic-test-key") {
            install(Auth) { autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false }
        }
        client.auth.importSession(Json { ignoreUnknownKeys=true }.decodeFromString<UserSession>(
            """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"owner","aud":"authenticated","role":"authenticated","created_at":"2026-10-03T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""),autoRefresh=false)
        f.switchUser = { id -> client.auth.importSession(Json { ignoreUnknownKeys=true }.decodeFromString<UserSession>(
            """{"access_token":"synthetic-$id","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$id","aud":"authenticated","role":"authenticated","created_at":"2026-10-03T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""),autoRefresh=false) }
        val player=fake<MediaPlayerHandler> { name,_ -> when(name) {
            "getNowPlayingState"->MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState"->MutableStateFlow(ControlState(false,false,RepeatState.None,false,false,false,false,1f))
            else->error("Unexpected player $name")
        } }
        val common=fake<CommonRepository> { name,args-> when(name) {
            "getAllNotifications"->flow { if(f.readFails) error("synthetic database failure"); emitAll(f.rows) }
            "notificationAccountOwner"->f.databaseOwner
            "insertNotificationOnce"->{val row=args[0] as NotificationEntity;val inserted=f.seen.add(row.eventKey());if(inserted)f.rows.value=f.rows.value.orEmpty()+row.copy(id=f.rows.value.orEmpty().size.toLong()+1);inserted}
            "deleteNotification"->{f.deletes++; if(f.deleteFails) error("synthetic write failure"); f.rows.value=f.rows.value.orEmpty().filterNot { it.id==args[0] as Long };Unit}
            else->error("Unexpected notification call $name")
        } }
        val users=fake<UserRepository> { name,args->when(name) {
            "fetchFollowers"->{f.followerReads++;flowOf(Result.success(listOf(UserProfile("peer","Nara"))))}
            "fetchFollowing"->flowOf(if(f.followingFails)Result.failure(IllegalStateException("offline"))else Result.success(f.followingRows.drop(args[1] as Int).take(args[2] as Int)))
            "followUser"->flow<Result<Unit>> {f.followWrites++;f.followGate?.await();emit(if(f.followFails)Result.failure(IllegalStateException("offline"))else Result.success(Unit))}
            else->error("Unexpected user call $name")
        } }
        startKoin { modules(module { single<MediaPlayerHandler> {player} }) }
        val vm=NotificationViewModel(common,users,client, fetchSocial = { if(f.socialFails) error("social unavailable") else f.socialRows }) {
            f.remoteCalls++; f.remoteGate?.await()
            if(f.remoteFails) throw IllegalStateException("announcement endpoint unavailable")
            listOf(RemoteNotification("Announcement", "Sample body", "https://example.invalid/update"))
        }
        try { block(f,vm) }
        finally {vm.viewModelScope.cancel();client.close();stopKoin()}
    }
    @Test fun announcementFailureDoesNotPreventFollowerSyncAndListUpdates():Unit=runBlocking(Dispatchers.Swing) {
        fixture { f,vm->
            waitFor { vm.listNotification.value?.isNotEmpty()==true }
            assertTrue(f.followerReads>0)
            vm.deleteNotification(vm.listNotification.value!!.single().id)
            waitFor { vm.listNotification.value?.isEmpty()==true }
        }
    }
    @Test fun followFailureKeepsButtonRetryableAndDoesNotPretendSuccess():Unit=runBlocking(Dispatchers.Swing) {
        fixture { f,vm->
            waitFor {vm.followingReady.value};f.followFails=true
            vm.followBack("peer")
            waitFor {vm.error.value!=null}
            assertFalse("peer" in vm.following.value);assertFalse("peer" in vm.followingBusy.value)
            f.followFails=false;vm.clearError();vm.followBack("peer")
            waitFor {"peer" in vm.following.value}
            assertEquals(2,f.followWrites)
        }
    }
    @Test fun doubleTapSendsOneFollowAndWaitsForConfirmation():Unit=runBlocking(Dispatchers.Swing) {
        fixture {f,vm->
            waitFor {vm.followingReady.value};f.followGate=CompletableDeferred()
            vm.followBack("peer");vm.followBack("peer")
            waitFor {f.followWrites==1}
            assertTrue("peer" in vm.followingBusy.value);assertFalse("peer" in vm.following.value)
            f.followGate!!.complete(Unit)
            waitFor {"peer" in vm.following.value}
            assertFalse("peer" in vm.followingBusy.value)
        }
    }
    @Test fun databaseReadCanRecoverWithoutReopeningTheScreen():Unit=runBlocking(Dispatchers.Swing) {
        fixture(setup = {it.readFails=true}) {f,vm->
            waitFor {vm.readFailed.value && !vm.refreshing.value}
            assertNotNull(vm.listNotification.value)
            f.readFails=false;vm.refresh();waitFor {!vm.readFailed.value && vm.listNotification.value!!.isNotEmpty()}
            assertEquals("Nara",vm.listNotification.value!!.single().name)
        }
    }
    @Test fun unavailableFollowStatusDoesNotOfferAFakeFollowAndRetryRestoresIt():Unit=runBlocking(Dispatchers.Swing) {
        fixture(setup = {it.followingFails=true}) {f,vm->
            waitFor {!vm.refreshing.value && NotificationSyncProblem.FOLLOWING in vm.syncProblems.value}
            assertFalse(vm.followingReady.value);vm.followBack("peer");assertEquals(0,f.followWrites)
            f.followingFails=false;vm.refresh();waitFor {vm.followingReady.value}
            vm.followBack("peer");waitFor {"peer" in vm.following.value};assertEquals(1,f.followWrites)
        }
    }
    @Test fun followingPagesIncludeAccountsPastTheOldThousandRowLimit():Unit=runBlocking(Dispatchers.Swing) {
        fixture(setup = {it.followingRows=(0..1004).map { id->UserProfile("peer-$id","Name $id") }}) {_,vm->
            waitFor {vm.followingReady.value}
            assertEquals(1005,vm.following.value.size);assertTrue("peer-1004" in vm.following.value)
        }
    }
    @Test fun failedSocialSyncCanRefreshIndependentlyOfAnnouncements():Unit=runBlocking(Dispatchers.Swing) {
        fixture(setup = {it.socialFails=true}) {f,vm->
            waitFor {!vm.refreshing.value && NotificationSyncProblem.SOCIAL in vm.syncProblems.value}
            f.socialFails=false;f.socialRows=listOf(NotificationEntity(channelId="social_message",name="Bima",
                single=listOf(mapOf("ownerId" to "owner","browseId" to "event-1"))))
            vm.refresh();waitFor {!vm.refreshing.value && vm.listNotification.value!!.any {it.channelId=="social_message"}}
            assertFalse(NotificationSyncProblem.SOCIAL in vm.syncProblems.value)
            assertTrue(NotificationSyncProblem.ANNOUNCEMENTS in vm.syncProblems.value)
        }
    }
    @Test fun failedDeletionKeepsCardAndCanBeRetried():Unit=runBlocking(Dispatchers.Swing) {
        fixture {f,vm->
            waitFor {vm.followingReady.value && !vm.refreshing.value}
            val id=vm.listNotification.value!!.single().id
            f.deleteFails=true;vm.deleteNotification(id);waitFor {vm.error.value!=null}
            assertEquals(id,vm.listNotification.value!!.single().id);assertTrue(vm.deleting.value.isEmpty())
            f.deleteFails=false;vm.clearError();vm.deleteNotification(id);waitFor {vm.listNotification.value!!.isEmpty()}
            assertEquals(2,f.deletes)
        }
    }
    @Test fun refreshFailureKeepsCacheAndRetryUpdatesWithoutDuplicating():Unit=runBlocking(Dispatchers.Swing) {
        fixture {f,vm->
            waitFor {vm.followingReady.value && !vm.refreshing.value}
            assertTrue(NotificationSyncProblem.ANNOUNCEMENTS in vm.syncProblems.value)
            val cached=vm.listNotification.value!!.single()
            f.remoteFails=false;vm.refresh();waitFor {!vm.refreshing.value && vm.listNotification.value!!.size==2}
            assertTrue(vm.listNotification.value!!.contains(cached));assertTrue(vm.syncProblems.value.isEmpty())
            vm.refresh();waitFor {!vm.refreshing.value};assertEquals(2,vm.listNotification.value!!.size)
        }
    }
    @Test fun refreshIgnoresRepeatedTapsWhileAnUpdateIsPending():Unit=runBlocking(Dispatchers.Swing) {
        fixture {f,vm->
            waitFor {vm.followingReady.value && !vm.refreshing.value}
            val before=f.remoteCalls;f.remoteGate=CompletableDeferred();vm.refresh();vm.refresh()
            waitFor {f.remoteCalls==before+1};assertTrue(vm.refreshing.value)
            f.remoteGate!!.complete(Unit);waitFor {!vm.refreshing.value};assertEquals(before+1,f.remoteCalls)
        }
    }
    @Test fun accountBoundActionRejectsAnotherOwnerAndReportsNavigationFailure():Unit=runBlocking(Dispatchers.Swing) {
        fixture {_,vm->
            waitFor {vm.followingReady.value}
            var opened=0
            val row=NotificationEntity(channelId="social_message",name="Nara",single=listOf(mapOf("ownerId" to "other")))
            vm.openAccountNotification(row) {opened++};waitFor {vm.error.value!=null};assertEquals(0,opened)
            vm.clearError();vm.openAccountNotification(row.copy(single=listOf(mapOf("ownerId" to "owner")))) {error("synthetic unavailable route")}
            waitFor {vm.error.value!=null};assertEquals(0,opened)
        }
    }
    @Test fun accountSwitchClearsCachedCardsEvenIfTheNewDatabaseReadFails():Unit=runBlocking(Dispatchers.Swing) {
        fixture { f,vm ->
            waitFor { vm.followingReady.value && !vm.refreshing.value }
            assertTrue(vm.listNotification.value!!.isNotEmpty())
            f.readFails=true;f.switchUser("other")
            waitFor { vm.readFailed.value }
            assertTrue(vm.listNotification.value.isNullOrEmpty())
            assertTrue(vm.following.value.isEmpty());assertFalse(vm.followingReady.value)
            f.databaseOwner="other";f.readFails=false
            f.rows.value=listOf(NotificationEntity(id=90,channelId="social_message",name="Previous account",
                single=listOf(mapOf("ownerId" to "owner"))))
            // Allow the account change refresh to complete, then explicitly retry the failed reader.
            waitFor { !vm.refreshing.value };vm.refresh();waitFor { !vm.readFailed.value }
            assertFalse(vm.listNotification.value.orEmpty().any { it.name=="Previous account" })
        }
    }
}
