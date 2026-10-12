package com.tan.gratify.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tan.data.sync.AccountAccess
import com.tan.domain.data.entities.*
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import com.tan.domain.utils.Resource
import com.tan.domain.utils.toSongEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.*

class PlaylistBuilderAuditTest {
    private fun track(id: String) = Track(null,null,"3:00",180,true,false,null,null,"Lagu $id",id,null,null,null,null)
    private fun result(id: String) = SongsResult(null,null,null,"3:00",180,null,false,"song",null,"Lagu $id",id,null,"")
    private inline fun <reified T> fake(crossinline answer: (String,Array<out Any?>)->Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,method,args -> answer(method.name,args.orEmpty()) } as T
    private class Controls {
        val access = MutableStateFlow<AccountAccess>(AccountAccess.Ready("A",false,false))
        var recentFailure = false
        var saveFailure = false
        var saveGate: CompletableDeferred<Unit>? = null
        val searchGates = mutableMapOf<String,CompletableDeferred<Unit>>()
        val queries = mutableListOf<String>()
        val writes = mutableListOf<Triple<String,List<String>,String?>>()
        val covers = mutableListOf<String?>()
        var searchError = false
        var emptySearch = false
        var target: LocalPlaylistEntity? = LocalPlaylistEntity(id=1, title="1", tracks=(1..6).map { "existing-$it" })
        val appends = mutableListOf<Triple<Long,List<String>,String?>>()
        val removals = mutableListOf<Triple<Long,String,String?>>()
    }
    private suspend fun await(test: ()->Boolean) = withTimeout(5000) { while(!test()) delay(10) }
    private suspend fun fixture(block: suspend (Controls,(SavedStateHandle)->AddSongsToPlaylistViewModel)->Unit) {
        val controls=Controls(); val models=mutableListOf<AddSongsToPlaylistViewModel>()
        val player=fake<MediaPlayerHandler> { name,_ -> when(name) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false,false,RepeatState.None,false,false,false,false,1f))
            else -> error(name)
        } }
        val songs=fake<SongRepository> { name,_ -> when(name) {
            "getRecentSong" -> { if(controls.recentFailure) error("storage unavailable"); listOf(track("recent").toSongEntity()) }
            "getLikedSongs" -> flowOf(listOf(track("liked").toSongEntity(),track("liked").toSongEntity()))
            "getRelatedData" -> flowOf(Resource.Success(listOf(track("related"),track("related"),track("existing-1")) to null))
            else -> error(name)
        } }
        val search=fake<SearchRepository> { name,args -> when(name) {
            "getSearchDataSong" -> flow<Resource<List<SongsResult>>> {
                val query=args[0] as String; controls.queries.add(query); controls.searchGates[query]?.await()
                emit(if(controls.searchError) Resource.Error("offline") else Resource.Success(if(controls.emptySearch) emptyList() else listOf(result(query),result(query))))
            }
            else -> error(name)
        } }
        val playlists=fake<LocalPlaylistRepository> { name,args -> when(name) {
            "getLocalPlaylist" -> flowOf(com.tan.domain.utils.LocalResource.Success(controls.target))
            "addTracksToLocalPlaylistForOwner" -> {
                val songs = (args[1] as List<*>).map { (it as SongEntity).videoId }
                controls.appends.add(Triple(args[0] as Long,songs,args[2] as String?))
                val action: suspend ()->Int = {
                    controls.saveGate?.await(); if(controls.saveFailure) error("disk full")
                    val added=songs.filter { it !in controls.target?.tracks.orEmpty() }
                    controls.target=controls.target?.copy(tracks=controls.target?.tracks.orEmpty()+added)
                    added.size
                }
                @Suppress("UNCHECKED_CAST")
                action.startCoroutineUninterceptedOrReturn(args.last() as Continuation<Int>)
            }
            "removeTrackFromLocalPlaylistForOwner" -> {
                val id=(args[1] as SongEntity).videoId
                controls.removals.add(Triple(args[0] as Long,id,args[2] as String?))
                val action: suspend ()->Boolean = {
                    controls.saveGate?.await(); if(controls.saveFailure) error("disk full")
                    val existed=id in controls.target?.tracks.orEmpty()
                    controls.target=controls.target?.copy(tracks=controls.target?.tracks.orEmpty().filterNot { it==id })
                    existed
                }
                @Suppress("UNCHECKED_CAST")
                action.startCoroutineUninterceptedOrReturn(args.last() as Continuation<Boolean>)
            }
            "createLocalPlaylistWithSongsForOwner" -> {
                val playlist=args[0] as LocalPlaylistEntity; val selected=args[1] as List<*>
                controls.covers.add(playlist.thumbnail)
                controls.writes.add(Triple(playlist.title,selected.map { (it as SongEntity).videoId },args[2] as String?))
                val action: suspend ()->Long = { controls.saveGate?.await(); if(controls.saveFailure) error("disk full"); 42L }
                @Suppress("UNCHECKED_CAST")
                action.startCoroutineUninterceptedOrReturn(args.last() as Continuation<Long>)
            }
            else -> error(name)
        } }
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        try { block(controls,{ saved -> AddSongsToPlaylistViewModel(search,songs,playlists,saved,controls.access).also(models::add) }) }
        finally { models.forEach { it.viewModelScope.cancel() }; stopKoin() }
    }

    @Test fun searchClearsStaleResultsDeduplicatesAndDistinguishesFailureFromNoMatches(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val vm=create(SavedStateHandle()); vm.bindTitle("Sore")
            vm.searchSongs("  first  "); await { !vm.uiState.value.isSearching }
            assertEquals(listOf("first"),vm.uiState.value.searchResults.map { it.videoId })
            c.searchGates["second"]=CompletableDeferred(); vm.searchSongs("second")
            assertTrue(vm.uiState.value.searchResults.isEmpty()); assertTrue(vm.uiState.value.isSearching)
            vm.clearSearch(); c.searchGates["second"]!!.complete(Unit); delay(350)
            assertTrue(vm.uiState.value.searchResults.isEmpty()); assertFalse(vm.uiState.value.isSearching)
            c.searchError=true; vm.searchSongs("fail"); await { !vm.uiState.value.isSearching }
            assertTrue(vm.uiState.value.searchError); assertTrue(vm.uiState.value.searchResults.isEmpty())
            c.searchError=false; c.emptySearch=true; vm.searchSongs("none"); await { !vm.uiState.value.isSearching }
            assertFalse(vm.uiState.value.searchError); assertTrue(vm.uiState.value.searchResults.isEmpty())
            c.recentFailure=true; vm.loadRecentSongs(); await { !vm.uiState.value.recentLoading }
            assertTrue(vm.uiState.value.recentError)
            c.recentFailure=false; vm.loadRecentSongs(); await { !vm.uiState.value.recentLoading }; assertFalse(vm.uiState.value.recentError)
        }
    }
    @Test fun pickerAppendsToExistingPlaylistWithoutRenamingOrCreatingAnotherAndRestoresSelections(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val saved=SavedStateHandle(mapOf("targetPlaylistId" to 1L))
            val vm=create(saved); await { !vm.uiState.value.targetLoading && !vm.uiState.value.accountBlocked }
            assertEquals("1",vm.uiState.value.title); assertEquals(6,vm.uiState.value.existingTrackIds.size)
            assertFalse(vm.hasUnsavedChanges()); assertFalse(vm.canSave())
            vm.addTrack(track("existing-1")); assertTrue(vm.uiState.value.selectedTracks.isEmpty())
            vm.addTrack(track("new-1")); vm.addTrack(track("new-2")); vm.updateTitle("Renamed")
            assertEquals("1",vm.uiState.value.title)
            val restored=create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            await { !restored.uiState.value.targetLoading && !restored.uiState.value.accountBlocked }
            assertEquals(listOf("new-1","new-2"),restored.uiState.value.selectedTracks.map { it.videoId })
            c.saveFailure=true; restored.savePlaylist(); await { !restored.uiState.value.isSaving }
            assertNotNull(restored.uiState.value.saveError); assertNull(restored.uiState.value.createdPlaylistId)
            c.saveFailure=false; restored.savePlaylist(); restored.savePlaylist(); await { !restored.uiState.value.isSaving }
            assertEquals(1L,restored.uiState.value.createdPlaylistId)
            assertEquals(Triple(1L,listOf("new-1","new-2"),"A"),c.appends.last())
            assertEquals(2,c.appends.size); assertTrue(c.writes.isEmpty())
        }
    }
    @Test fun missingOrSavedPlaylistCannotBeEditedAndOwnerSwitchBlocksExistingPicker(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            c.target=null
            val vm=create(SavedStateHandle(mapOf("targetPlaylistId" to 1L)))
            await { vm.uiState.value.targetUnavailable }; vm.addTrack(track("new")); vm.savePlaylist()
            assertTrue(c.appends.isEmpty()); assertFalse(vm.canSave())
            c.target=LocalPlaylistEntity(id=1,title="Saved",sourceSharedPlaylistId="other-owner")
            vm.loadTargetPlaylist(); await { !vm.uiState.value.targetLoading }; assertTrue(vm.uiState.value.targetUnavailable)
            c.target=LocalPlaylistEntity(id=1,title="1",tracks=listOf("old"))
            vm.loadTargetPlaylist(); await { !vm.uiState.value.targetLoading }; vm.addTrack(track("new"))
            c.access.value=AccountAccess.Ready("B",false,false); await { vm.uiState.value.ownerChanged }
            assertTrue(vm.uiState.value.selectedTracks.isEmpty()); assertTrue(vm.uiState.value.existingTrackIds.isEmpty())
            vm.savePlaylist(); assertTrue(c.appends.isEmpty())
        }
    }
    @Test fun failedSaveKeepsDraftAndFreezesEditsUntilRetryFinishes(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val vm=create(SavedStateHandle()); vm.bindTitle("  Sore  "); vm.addTrack(track("one")); vm.addTrack(track("two"))
            c.saveFailure=true; vm.savePlaylist(); await { !vm.uiState.value.isSaving }
            assertNotNull(vm.uiState.value.saveError); assertNull(vm.uiState.value.createdPlaylistId)
            assertEquals(listOf("one","two"),vm.uiState.value.selectedTracks.map { it.videoId })
            c.saveFailure=false; c.saveGate=CompletableDeferred(); vm.updateTitle("  Perjalanan  "); vm.savePlaylist(); vm.savePlaylist()
            vm.removeTrack("one"); vm.addTrack(track("three")); vm.updateTitle("Changed")
            assertEquals("  Perjalanan  ",vm.uiState.value.title); assertEquals(2,vm.uiState.value.selectedTracks.size)
            c.saveGate!!.complete(Unit); await { !vm.uiState.value.isSaving }
            assertEquals(42L,vm.uiState.value.createdPlaylistId); assertEquals(2,c.writes.size)
            assertEquals(Triple("Perjalanan",listOf("one","two"),"A"),c.writes.last())
        }
    }
    @Test fun editedNameSelectionAndQueryRestoreAndEmptyPlaylistIsAllowed(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val saved=SavedStateHandle(); val vm=create(saved); vm.bindTitle("Awal"); vm.updateTitle("Baru"); vm.searchSongs("draft")
            vm.addTrack(track("one")); vm.addTrack(track("one")); vm.addTrack(track(""))
            val restored=create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); restored.bindTitle("Awal")
            assertEquals("Baru",restored.uiState.value.title); assertEquals("draft",restored.uiState.value.searchQuery)
            assertEquals(listOf("one"),restored.uiState.value.selectedTracks.map { it.videoId })
            val empty=create(SavedStateHandle()); empty.bindTitle("Kosong"); empty.savePlaylist(); await { !empty.uiState.value.isSaving }
            assertEquals(emptyList(),c.writes.last().second); assertEquals(42L,empty.uiState.value.createdPlaylistId)
        }
    }
    @Test fun musicFirstDraftRestoresReorderedSongsAndSavesThatExactOrder(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val saved = SavedStateHandle(); val vm = create(saved); vm.bindTitle("")
            listOf("one", "two", "three").forEach { vm.addTrack(track(it).copy(thumbnails = listOf(com.tan.domain.data.model.searchResult.songs.Thumbnail(160, "https://example.com/$it.jpg", 160)))) }
            vm.savePlaylist(); assertTrue(vm.uiState.value.titleInvalid); assertTrue(c.writes.isEmpty())
            vm.moveTrack("three", -1); vm.moveTrack("three", -1)
            vm.moveTrack("three", -1); vm.moveTrack("missing", 1); vm.moveTrack("one", 99)
            assertEquals(listOf("three", "one", "two"), vm.uiState.value.selectedTracks.map { it.videoId })
            val restored = create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            restored.bindTitle(""); assertEquals("", restored.uiState.value.title)
            assertEquals(vm.uiState.value.selectedTracks, restored.uiState.value.selectedTracks)
            restored.updateTitle("  Malam  "); c.saveGate = CompletableDeferred(); restored.savePlaylist()
            restored.moveTrack("three", 1)
            assertEquals(listOf("three", "one", "two"), restored.uiState.value.selectedTracks.map { it.videoId })
            c.saveGate!!.complete(Unit); await { !restored.uiState.value.isSaving }
            assertEquals(Triple("Malam", listOf("three", "one", "two"), "A"), c.writes.single())
            assertEquals("https://example.com/three.jpg", c.covers.single())
        }
    }

    @Test fun accountSwitchClearsPrivateDraftAndCannotSaveItUnderAnotherOwner(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val saved=SavedStateHandle(); val vm=create(saved); vm.bindTitle("Private draft"); vm.addTrack(track("one")); vm.searchSongs("private")
            c.saveGate=CompletableDeferred(); vm.savePlaylist(); await { c.writes.isNotEmpty() }
            c.access.value=AccountAccess.Loading; await { vm.uiState.value.accountBlocked }
            c.access.value=AccountAccess.Ready("B",false,false); await { vm.uiState.value.ownerChanged }
            c.saveGate!!.complete(Unit); delay(50)
            assertEquals("",vm.uiState.value.title); assertTrue(vm.uiState.value.selectedTracks.isEmpty()); assertNull(vm.uiState.value.createdPlaylistId)
            assertFalse(vm.canSave()); vm.savePlaylist("Must not save"); assertEquals(1,c.writes.size)
            val restored=create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); assertTrue(restored.uiState.value.ownerChanged)
        }
    }
    @Test fun temporaryAccountLoadingKeepsOneSaveAndDefersItsResultUntilTheSameOwnerReturns(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c,create ->
            val vm=create(SavedStateHandle()); vm.bindTitle("Sore"); assertTrue(vm.hasUnsavedChanges())
            c.saveGate=CompletableDeferred(); vm.savePlaylist(); await { c.writes.isNotEmpty() }
            c.access.value=AccountAccess.Loading; await { vm.uiState.value.accountBlocked }
            assertFalse(vm.canSave()); c.saveGate!!.complete(Unit); await { !vm.uiState.value.isSaving }
            assertEquals(42L,vm.uiState.value.createdPlaylistId); assertTrue(vm.uiState.value.accountBlocked)
            c.access.value=AccountAccess.Ready("A",false,false); await { !vm.uiState.value.accountBlocked }
            assertEquals(42L,vm.uiState.value.createdPlaylistId); assertFalse(vm.canSave()); assertEquals(1,c.writes.size)
        }
    }
    @Test fun immediateAddsWaitForPersistenceDeduplicateTapsPreserveOrderAndRetryFailures(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c, create ->
            val vm = create(SavedStateHandle(mapOf("targetPlaylistId" to 1L)))
            await { !vm.uiState.value.targetLoading && !vm.uiState.value.accountBlocked }
            c.saveGate = CompletableDeferred()
            vm.appendTrack(track("new-1")); vm.appendTrack(track("new-1")); vm.appendTrack(track("new-2"))
            vm.appendTrack(track("existing-1")); vm.appendTrack(track(""))
            await { c.appends.size == 1 }
            assertEquals(setOf("new-1", "new-2"), vm.uiState.value.pendingTrackIds)
            assertFalse("new-1" in vm.uiState.value.existingTrackIds)
            assertNull(vm.uiState.value.createdPlaylistId)
            c.saveGate!!.complete(Unit)
            await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertEquals(listOf("new-1", "new-2"), c.appends.flatMap { it.second })
            assertTrue(c.appends.all { it.first == 1L && it.third == "A" })
            assertEquals(8, vm.uiState.value.existingTrackIds.size)
            assertEquals(2, vm.uiState.value.addedCount)
            assertTrue(vm.uiState.value.selectedTracks.isEmpty()); assertTrue(c.writes.isEmpty())
            c.saveGate = null; c.saveFailure = true
            vm.appendTrack(track("retry")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertTrue("retry" in vm.uiState.value.failedTrackIds)
            assertFalse("retry" in vm.uiState.value.existingTrackIds)
            c.saveFailure = false
            vm.appendTrack(track("retry")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertFalse("retry" in vm.uiState.value.failedTrackIds)
            assertTrue("retry" in vm.uiState.value.existingTrackIds)
        }
    }
    @Test fun emptyCreationDiscardsOldRestoredSelectionsAndCreatesOnlyOnce(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c, create ->
            val vm = create(SavedStateHandle()); vm.bindTitle("  1  "); vm.addTrack(track("old-draft"))
            c.saveGate = CompletableDeferred(); vm.createEmptyPlaylist(); vm.createEmptyPlaylist()
            await { c.writes.isNotEmpty() }
            assertEquals(Triple("1", emptyList(), "A"), c.writes.single())
            c.saveGate!!.complete(Unit); await { !vm.uiState.value.isSaving }
            assertEquals(42L, vm.uiState.value.createdPlaylistId)
            vm.createEmptyPlaylist(); assertEquals(1, c.writes.size)
        }
    }
    @Test fun secondTapUndoesPersistedAdditionAndReaddKeepsCountsAndOwnerCorrect(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c, create ->
            val vm=create(SavedStateHandle(mapOf("targetPlaylistId" to 1L)))
            await { !vm.uiState.value.targetLoading && !vm.uiState.value.accountBlocked }
            vm.toggleTrack(track("new")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertEquals(7,vm.uiState.value.existingTrackIds.size); assertEquals(1,vm.uiState.value.addedCount)
            assertEquals(setOf("new"),vm.uiState.value.sessionAddedTrackIds)
            vm.loadTargetPlaylist(); await { !vm.uiState.value.targetLoading }
            assertEquals(1,vm.uiState.value.addedCount)
            vm.toggleTrack(track("new")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertEquals(6,vm.uiState.value.existingTrackIds.size); assertEquals(0,vm.uiState.value.addedCount)
            assertTrue(vm.uiState.value.sessionAddedTrackIds.isEmpty()); assertEquals(2,vm.uiState.value.membershipChangeCount)
            assertEquals(listOf(Triple<Long,String,String?>(1L,"new","A")),c.removals)
            vm.toggleTrack(track("new")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertEquals(7,vm.uiState.value.existingTrackIds.size); assertEquals(1,vm.uiState.value.addedCount)
            assertEquals(2,c.appends.size)
            vm.toggleTrack(track("existing-1")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertEquals(6,vm.uiState.value.existingTrackIds.size); assertEquals(1,vm.uiState.value.addedCount)
            vm.toggleTrack(track("existing-1")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertEquals(7,vm.uiState.value.existingTrackIds.size); assertEquals(1,vm.uiState.value.addedCount)
        }
    }
    @Test fun failedUndoRetainsCheckedMembershipAndPendingRepeatedTapsCannotRemoveTwice(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c, create ->
            val vm=create(SavedStateHandle(mapOf("targetPlaylistId" to 1L)))
            await { !vm.uiState.value.targetLoading && !vm.uiState.value.accountBlocked }
            vm.toggleTrack(track("new")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            c.saveFailure=true
            vm.toggleTrack(track("new")); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertTrue("new" in vm.uiState.value.existingTrackIds); assertEquals(1,vm.uiState.value.addedCount)
            assertTrue("new" in vm.uiState.value.failedTrackIds); assertEquals(1,vm.uiState.value.membershipChangeCount)
            c.saveFailure=false; c.saveGate=CompletableDeferred()
            vm.toggleTrack(track("new")); vm.toggleTrack(track("new"))
            await { c.removals.size==2 }
            assertEquals(setOf("new"),vm.uiState.value.pendingTrackIds)
            c.saveGate!!.complete(Unit); await { vm.uiState.value.pendingTrackIds.isEmpty() }
            assertFalse("new" in vm.uiState.value.existingTrackIds); assertFalse("new" in vm.uiState.value.failedTrackIds)
            assertEquals(0,vm.uiState.value.addedCount); assertEquals(2,vm.uiState.value.membershipChangeCount)
            assertEquals(2,c.removals.size)
        }
    }
    @Test fun ownerSwitchCancelsPendingRemovalAndQueuedAdd(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c, create ->
            val vm=create(SavedStateHandle(mapOf("targetPlaylistId" to 1L)))
            await { !vm.uiState.value.targetLoading && !vm.uiState.value.accountBlocked }
            c.saveGate=CompletableDeferred()
            vm.toggleTrack(track("existing-1")); vm.toggleTrack(track("new"))
            await { c.removals.size==1 }
            c.access.value=AccountAccess.Ready("B",false,false)
            await { vm.uiState.value.ownerChanged }
            c.saveGate!!.complete(Unit); delay(50)
            assertTrue(c.appends.isEmpty()); assertEquals("A",c.removals.single().third)
            assertTrue("existing-1" in c.target!!.tracks.orEmpty())
            assertEquals(0,vm.uiState.value.membershipChangeCount)
            vm.toggleTrack(track("existing-1")); assertEquals(1,c.removals.size)
        }
    }
    @Test fun accountSwitchCancelsPendingIndividualAddsAndRecommendationsStayScoped(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { c, create ->
            val vm = create(SavedStateHandle(mapOf("targetPlaylistId" to 1L)))
            await { !vm.uiState.value.targetLoading && !vm.uiState.value.likedLoading && vm.uiState.value.likedSongs.isNotEmpty() && !vm.uiState.value.recommendationsLoading }
            assertEquals(listOf("liked"), vm.uiState.value.likedSongs.map { it.videoId })
            assertEquals(listOf("related", "existing-1", "recent", "liked"), vm.uiState.value.recommendations.map { it.videoId })
            c.saveGate = CompletableDeferred(); vm.appendTrack(track("private")); vm.appendTrack(track("queued"))
            await { c.appends.size == 1 }
            c.access.value = AccountAccess.Ready("B", false, false)
            await { vm.uiState.value.ownerChanged }
            c.saveGate!!.complete(Unit); delay(50)
            assertTrue(vm.uiState.value.pendingTrackIds.isEmpty())
            assertTrue(vm.uiState.value.likedSongs.isEmpty()); assertTrue(vm.uiState.value.recommendations.isEmpty())
            assertFalse("private" in vm.uiState.value.existingTrackIds); assertEquals(0, vm.uiState.value.addedCount)
            vm.appendTrack(track("other")); assertEquals(1, c.appends.size)
        }
    }

}
