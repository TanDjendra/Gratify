package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import com.tan.domain.utils.LocalResource
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class PlaylistManagementAuditTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,m,a -> answer(m.name,a.orEmpty()) } as T
    private class Controls {
        var playlist=LocalPlaylistEntity(id=1,title="1")
        var fail=false
        var deletions=0
        var gate:CompletableDeferred<Unit>?=null
        val writes=mutableListOf<String>()
    }
    private suspend fun await(condition:()->Boolean)=withTimeout(5000) { while(!condition()) delay(10) }
    private suspend fun fixture(block:suspend (Controls,LocalPlaylistViewModel)->Unit) {
        val c=Controls()
        val player=fake<MediaPlayerHandler> { name,_ -> when(name) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false,false,RepeatState.None,false,false,false,false,1f))
            else -> error(name)
        } }
        val preferences=fake<DataStoreManager> { name,_ -> if(name=="getLocalPlaylistFilter") flowOf(DataStoreManager.LOCAL_PLAYLIST_FILTER_CUSTOM_ORDER) else error(name) }
        val local=fake<LocalPlaylistRepository> { name,args -> when(name) {
            "getLocalPlaylist" -> flowOf(LocalResource.Success(c.playlist))
            "deleteLocalPlaylist" -> flow {
                c.deletions++;c.gate?.await();emit(LocalResource.Success("Deleted"))
            }
            "updateTitleLocalPlaylist" -> flow {
                val title=args[1] as String;c.writes+=title;c.gate?.await()
                if(c.fail) emit(LocalResource.Error("storage failed"))
                else {c.playlist=c.playlist.copy(title=title);emit(LocalResource.Success("Saved"))}
            }
            else -> error(name)
        } }
        val client=createSupabaseClient("https://example.invalid","synthetic-key") {
            install(Auth) { autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false }
        }
        startKoin { modules(module { single<MediaPlayerHandler> {player};single {client} }) }
        val vm=LocalPlaylistViewModel(preferences,fake {n,_->error(n)},local,fake {n,_->error(n)},fake {n,_->error(n)},fake {n,_->error(n)})
        try {vm.updatePlaylistState(1);await {vm.uiState.value.title=="1"};block(c,vm)}
        finally {vm.viewModelScope.cancel();client.close();stopKoin()}
    }
    @Test fun failedRenameKeepsOldTitleAndRetryConfirmsOnceWithTrimmedName():Unit=runBlocking(Dispatchers.Swing) {
        fixture {c,vm ->
            c.fail=true;var saved=0;vm.updatePlaylistTitle("  Sore  ",1){saved++}
            await {!vm.editStatus.value.saving};assertTrue(vm.editStatus.value.failed);assertEquals("1",vm.uiState.value.title);assertEquals(0,saved)
            c.fail=false;c.gate=CompletableDeferred();vm.updatePlaylistTitle("  Sore  ",1){saved++};vm.updatePlaylistTitle("Other",1){saved++}
            await {c.writes.size==2};assertTrue(vm.editStatus.value.saving);assertEquals(listOf("Sore","Sore"),c.writes)
            c.gate!!.complete(Unit);await {!vm.editStatus.value.saving && saved==1}
            assertEquals("Sore",vm.uiState.value.title);assertFalse(vm.editStatus.value.failed)
        }
    }
    @Test fun blankLongAndSavedPlaylistNamesCannotWrite():Unit=runBlocking(Dispatchers.Swing) {
        fixture {c,vm ->
            vm.updatePlaylistTitle(" ",1);assertTrue(vm.editStatus.value.failed)
            vm.updatePlaylistTitle("x".repeat(101),1);assertTrue(vm.editStatus.value.failed)
            assertTrue(c.writes.isEmpty())
            c.playlist=c.playlist.copy(sourceSharedPlaylistId="source")
            vm.updatePlaylistState(1);await {vm.uiState.value.isReadOnly}
            vm.updatePlaylistTitle("Changed",1);delay(100);assertTrue(c.writes.isEmpty())
        }
    }
    @Test fun repeatedDeleteWaitsForConfirmedResultAndNavigatesOnlyOnce():Unit=runBlocking(Dispatchers.Swing) {
        fixture {c,vm ->
            c.gate=CompletableDeferred();var closed=0
            vm.deletePlaylist(1){closed++};vm.deletePlaylist(1){closed++}
            await {c.deletions==1};assertEquals(0,closed)
            c.gate!!.complete(Unit);await {closed==1};delay(100)
            assertEquals(1,c.deletions);assertEquals(1,closed)
        }
    }
}
