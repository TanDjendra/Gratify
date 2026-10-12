package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class ProfileFixRegressionTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name, args.orEmpty()) } as T
    private class Fixture {
        var profile: UserProfile? = UserProfile("target", "Nara", showFollowers = true, showPlaylists = true, showRecentArtists = true)
        var profileError: Exception? = null
        var profileGate: CompletableDeferred<Unit>? = null
        var followersGate: CompletableDeferred<Unit>? = null
        var followersError: Exception? = null
        var profileReads = 0
        var followingReads = 0
        var followWrites = 0
        var forbiddenFallbackReads = 0
        var followers = listOf(UserProfile("follower", "Bima"))
        val offsets = mutableListOf<Int>()
        var sharedError = false
        var libraryOwner: String? = null
        var followGate: CompletableDeferred<Unit>? = null
        var followError: Exception? = null
        val artistWrites = mutableListOf<Int>()
        val preferenceWrites = mutableListOf<Pair<String, String>>()
        var privacyWrites = 0
        var privacyFailure = false
        var playlists = listOf(CloudPlaylistDto(id = "cloud", sharedPlaylistId = "shared", title = "Not history", isPublic = true))
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(3000) { while (!condition()) delay(10) }
    private suspend fun fixture(owner: String? = null, block: suspend (Fixture, UserProfileViewModel, FollowListViewModel) -> Unit) {
        val f = Fixture().apply { libraryOwner = owner }
        val client = createSupabaseClient("http://127.0.0.1:9", "synthetic-test-key") {
            install(Auth) { autoLoadFromStorage = false; autoSaveToStorage = false; alwaysAutoRefresh = false }
        }
        if (owner != null) client.auth.importSession(Json { ignoreUnknownKeys = true }.decodeFromString<UserSession>(
            """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated","created_at":"2026-10-03T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""), autoRefresh = false)
        val player = fake<MediaPlayerHandler> { name, _ -> when (name) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            else -> error("Unexpected player: $name")
        } }
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        val reads = fake<UserRepository> { name, args -> when (name) {
            "fetchUserProfile" -> flow<Result<UserProfile?>> {
                f.profileReads++; val captured = f.profile; val failure = f.profileError
                f.profileGate?.await()
                emit(if (failure == null) Result.success(captured) else Result.failure<UserProfile?>(failure))
            }
            "getVisibleFollowCounts" -> flowOf(Result.success(VisibleFollowCounts(5, 3)))
            "fetchFollowers" -> flow<Result<List<UserProfile>>> {
                val offset = args[1] as Int; f.offsets += offset
                f.followersGate?.await()
                emit(f.followersError?.let { Result.failure<List<UserProfile>>(it) }
                    ?: Result.success(f.followers.drop(offset).take(args[2] as Int)))
            }
            "fetchFollowing" -> flow { f.followingReads++; emit(Result.success(emptyList<UserProfile>())) }
            "fetchIsFollowing" -> flowOf(Result.success(false))
            "followUser", "unfollowUser" -> flow<Result<Unit>> {
                f.followWrites++; f.followGate?.await()
                emit(f.followError?.let { Result.failure(it) } ?: Result.success(Unit))
            }
            "updateTopArtists" -> { f.artistWrites += (args[1] as List<*>).size; flowOf(Result.success(Unit)) }
            else -> error("Unexpected users: $name")
        } }
        val users = object : UserRepository by reads {
            override suspend fun setProfilePrivacy(setting: String, visible: Boolean): Result<Unit> {
                f.privacyWrites++
                return if (f.privacyFailure) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
            }
        }
        val social = fake<SocialRepository> { name, _ -> when (name) {
            "getUserProfilePlaylists" -> flowOf(Result.success(f.playlists))
            else -> { f.forbiddenFallbackReads++; error("Unexpected social: $name") }
        } }
        val settings = fake<DataStoreManager> { name, args -> when (name) {
            "getString" -> flowOf(if (args[0] == "last_synced_user_id") f.libraryOwner else null)
            "putString" -> { f.preferenceWrites += args[0] as String to args[1] as String; Unit }
            else -> error("Unexpected settings: $name")
        } }
        val shared = fake<SharedPlaylistRepository> { name, _ -> when (name) {
            "getSharedPlaylistTracks" -> flowOf(Result.failure<List<com.tan.domain.data.entities.SharedPlaylistTrack>>(IllegalStateException("offline")))
            else -> error("Unexpected shared: $name")
        } }
        val vm = UserProfileViewModel(users, shared, client, social, settings)
        val follows = FollowListViewModel(users, client)
        try { block(f, vm, follows) }
        finally { vm.viewModelScope.cancel(); follows.viewModelScope.cancel(); stopKoin(); client.close() }
    }

    @Test fun missingAccountIsNotInvented(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.profile = null; vm.loadProfile("missing"); await { !vm.state.value.isLoading }
            assertNull(vm.state.value.profile); assertNull(vm.state.value.profileError)
            assertEquals(1, f.profileReads)
        }
    }
    @Test fun savedAvatarAppearsImmediatelyAndAnOlderReadCannotRestoreIt(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            val old = "https://example.invalid/storage/v1/object/public/avatars/target.jpg?cacheNonce=1"
            val replacement = "https://example.invalid/storage/v1/object/public/avatars/target.jpg?cacheNonce=2"
            f.profile = f.profile!!.copy(avatarUrl = old)
            vm.loadProfile("target"); await { !vm.state.value.isLoading }
            f.profileGate = CompletableDeferred()
            vm.onProfileSaved("target", "New name", replacement)
            assertEquals(replacement, vm.state.value.profile?.avatarUrl)
            assertEquals("New name", vm.state.value.profile?.displayName)
            await { f.profileReads == 2 }
            f.profileGate!!.complete(Unit)
            await { !vm.state.value.isLoading && !vm.state.value.playlistLoading }
            assertEquals(replacement, vm.state.value.profile?.avatarUrl)
            assertEquals("New name", vm.state.value.profile?.displayName)
            assertTrue(f.preferenceWrites.contains("AppProfileImage" to replacement))
        }
    }
    @Test fun failedRefreshAfterSavingKeepsTheConfirmedPhotoVisible(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            vm.loadProfile("target"); await { !vm.state.value.isLoading }
            f.profileError = IllegalStateException("synthetic offline")
            val replacement = "https://example.invalid/storage/v1/object/public/avatars/target.jpg?cacheNonce=3"
            vm.onProfileSaved("target", "Nara", replacement)
            await { !vm.state.value.isLoading }
            assertNotNull(vm.state.value.profileError)
            assertEquals(replacement, vm.state.value.profile?.avatarUrl)
        }
    }
    @Test fun savedIdentityCannotBeAppliedToAnotherAccount(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("viewer") { f, vm, _ ->
            vm.loadProfile("target"); await { !vm.state.value.isLoading }
            val before = vm.state.value.profile
            vm.onProfileSaved("target", "Wrong owner", "https://example.invalid/storage/v1/object/public/avatars/target.jpg")
            assertEquals(before, vm.state.value.profile)
            assertEquals(1, f.profileReads)
        }
    }
    @Test fun ownerKeepsPrivatePinsWithoutPublicSharedIdsAndVisitorsExcludeThem(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            f.profile = f.profile!!.copy(showPlaylists = false)
            f.playlists = listOf(CloudPlaylistDto(id="private-cloud",title="Only owner",clientSyncId="private-stable-id",isPublic=false,isProfilePinned=true))
            vm.loadProfile("target"); await { !vm.state.value.playlistLoading && vm.state.value.profile != null }
            assertEquals("Only owner",vm.state.value.publicPlaylists.single().title)
            assertFalse(vm.state.value.publicPlaylists.single().isPublic)
            assertNull(vm.state.value.publicPlaylists.single().id)
        }
        fixture("visitor") { f, vm, _ ->
            f.playlists = listOf(CloudPlaylistDto(id="private-cloud",title="Only owner",isPublic=false,isProfilePinned=true))
            vm.loadProfile("target"); await { !vm.state.value.playlistLoading && vm.state.value.profile != null }
            assertTrue(vm.state.value.publicPlaylists.isEmpty())
        }
    }
    @Test fun failedReadIsDifferentFromMissingAccount(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.profileError = IllegalStateException("offline"); vm.loadProfile("target")
            await { !vm.state.value.isLoading }; assertNotNull(vm.state.value.profileError); assertNull(vm.state.value.profile)
            f.profileError = null; vm.loadProfile("target"); await { vm.state.value.profile != null }
            assertNull(vm.state.value.profileError)
        }
    }
    @Test fun identityRemainsLoadingUntilItsSingleReadFinishes(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.profileGate = CompletableDeferred(); vm.loadProfile("target"); await { f.profileReads == 1 }
            delay(30); assertTrue(vm.state.value.isLoading); assertNull(vm.state.value.profile)
            f.profileGate!!.complete(Unit); await { !vm.state.value.playlistLoading && vm.state.value.profile != null }
            assertEquals(1, f.profileReads); assertEquals(5, vm.state.value.followersCount)
        }
    }
    @Test fun publicPlaylistCannotBecomeFakePlaybackHistory(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            vm.loadProfile("target"); await { vm.state.value.profile != null && !vm.state.value.playlistLoading }
            assertEquals("shared", vm.state.value.publicPlaylists.single().id)
            assertNull(vm.state.value.recentActivityTitle); assertNull(vm.state.value.recentActivityVideoId)
            assertEquals(0, f.forbiddenFallbackReads)
        }
    }
    @Test fun failedRefreshClearsPreviousActivityAndIdentity(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.profile = f.profile!!.copy(nowPlayingVideoId = "song", nowPlayingTitle = "Current song", lastActiveAt = System.currentTimeMillis().toString())
            vm.loadProfile("target"); await { vm.state.value.recentActivityVideoId == "song" }
            f.profileError = IllegalStateException("offline"); vm.loadProfile("target")
            assertNull(vm.state.value.recentActivityTitle)
            await { vm.state.value.profileError != null }; assertNull(vm.state.value.profile)
        }
    }
    @Test fun hiddenExpiredAndFutureActivityAreSuppressed(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            for ((visible, timestamp) in listOf(false to System.currentTimeMillis(), true to (System.currentTimeMillis() - 700_000), true to (System.currentTimeMillis() + 700_000))) {
                f.profile = f.profile!!.copy(showRecentArtists = visible, nowPlayingVideoId = "song", nowPlayingTitle = "Private", lastActiveAt = timestamp.toString())
                vm.loadProfile("target"); await { vm.state.value.profile != null }
                assertNull(vm.state.value.recentActivityVideoId)
            }
        }
    }
    @Test fun oldRequestCannotOverwriteAnotherProfile(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.profileGate = CompletableDeferred(); val gate = f.profileGate!!
            vm.loadProfile("target"); await { f.profileReads == 1 }
            f.profileGate = null; f.profile = UserProfile("second", "Second")
            vm.loadProfile("second"); await { vm.state.value.profile?.id == "second" }
            gate.complete(Unit); delay(50); assertEquals("second", vm.state.value.profile?.id)
        }
    }
    @Test fun guestFollowRequestsLoginWithoutWriting(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            var login = 0; vm.toggleFollow("target") { login++ }
            assertEquals(1, login); assertEquals(0, f.followWrites)
        }
    }
    @Test fun followerTabsKeepIndependentLoading(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, _, vm ->
            f.followersGate = CompletableDeferred(); vm.loadData("target")
            await { f.followingReads == 1 && !vm.state.value.followingLoading }
            assertTrue(vm.state.value.followersLoading); assertTrue(vm.state.value.followers.isEmpty())
            f.followersGate!!.complete(Unit); await { !vm.state.value.followersLoading }
            assertEquals("Bima", vm.state.value.followers.single().displayName)
        }
    }
    @Test fun followerErrorsRemainVisibleAndCanBeRetried(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, _, vm ->
            f.followersError = IllegalStateException("offline"); vm.loadData("target")
            await { !vm.state.value.followersLoading }; assertNotNull(vm.state.value.followersError)
            assertNull(vm.state.value.followingError)
            f.followersError = null; vm.retryFollowers(); await { vm.state.value.followers.size == 1 }
            assertNull(vm.state.value.followersError); assertEquals(listOf(0, 0), f.offsets)
        }
    }
    @Test fun followerPagesAppendAndKeepTheirCursorOnFailure(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, _, vm ->
            f.followers = (0..44).map { UserProfile("id-$it", "Name $it") }; vm.loadData("target")
            await { !vm.state.value.followersLoading }; assertEquals(40, vm.state.value.followers.size); assertTrue(vm.state.value.moreFollowers)
            f.followersError = IllegalStateException("offline"); vm.loadMoreFollowers(); await { !vm.state.value.followersLoading }
            assertEquals(40, vm.state.value.followers.size)
            f.followersError = null; vm.retryFollowers(); await { vm.state.value.followers.size == 45 }
            assertFalse(vm.state.value.moreFollowers); assertEquals(listOf(0, 40, 40), f.offsets)
        }
    }
    @Test fun savingFailureKeepsActionAvailableAndReportsIt(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { _, vm, _ ->
            val local = fake<LocalPlaylistRepository> { name, _ -> error("Must not write on download failure: $name") }
            var error: String? = null
            vm.addSharedPlaylistToLibrary("shared", "Collection", null, local, onSuccess = { fail("Unexpected success") }, onError = { error = it })
            await { error != null }; assertFalse(vm.state.value.savingPlaylist)
        }
    }
    @Test fun ownProfileReadUpdatesCacheWithoutUpsertingTheServer(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            vm.loadProfile("target"); await { vm.state.value.privacyReady && !vm.state.value.countsLoading }
            assertTrue(vm.state.value.isOwnProfile); assertEquals(1, f.profileReads)
            assertTrue(f.preferenceWrites.contains("privacy_show_followers" to "TRUE"))
        }
    }
    @Test fun followingIsPendingAndRejectsRepeatedTapsUntilConfirmed(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("viewer") { f, vm, _ ->
            vm.loadProfile("target"); await { vm.state.value.followReady }
            f.followGate = CompletableDeferred(); vm.toggleFollow("target")
            await { f.followWrites == 1 }; assertTrue(vm.state.value.isFollowBusy)
            vm.toggleFollow("target"); delay(20); assertEquals(1, f.followWrites)
            f.followGate!!.complete(Unit); await { !vm.state.value.isFollowBusy && vm.state.value.followReady }
        }
    }
    @Test fun followingFailurePreservesTheConfirmedStatus(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("viewer") { f, vm, _ ->
            vm.loadProfile("target"); await { vm.state.value.followReady }
            f.followError = IllegalStateException("offline"); vm.toggleFollow("target")
            await { vm.state.value.followError != null && !vm.state.value.isFollowBusy }
            assertFalse(vm.state.value.isFollowing); assertEquals(1, f.followWrites)
        }
    }
    @Test fun removingTheLastArtistPublishesAnEmptyList(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            vm.syncTopArtists(listOf(com.tan.domain.data.entities.ArtistEntity("artist", "Artist", null)))
            await { f.artistWrites == listOf(1) }
            vm.syncTopArtists(emptyList()); await { f.artistWrites == listOf(1, 0) }
        }
    }

    @Test fun anotherLibraryOwnerCannotPublishArtistsToTheCurrentAccount(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            f.libraryOwner = "previous-owner"
            vm.syncTopArtists(emptyList()); delay(50)
            assertTrue(f.artistWrites.isEmpty())
        }
    }

    @Test fun privacySuccessUpdatesConfirmedProfileAndFailureKeepsPriorValue(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("target") { f, vm, _ ->
            vm.loadProfile("target"); await { vm.state.value.privacyReady }
            vm.setProfilePrivacy("privacy_show_followers", false)
            assertFalse(vm.state.value.profile!!.showFollowers); assertNull(vm.state.value.privacyError)
            f.privacyFailure = true; vm.setProfilePrivacy("privacy_show_followers", true)
            assertFalse(vm.state.value.profile!!.showFollowers); assertNotNull(vm.state.value.privacyError)
            assertFalse(vm.state.value.privacySaving); assertEquals(2, f.privacyWrites)
        }
    }
    @Test fun privacyCannotBeWrittenFromAnUnloadedOrAnotherPersonsProfile(): Unit = runBlocking(Dispatchers.Swing) {
        fixture("viewer") { f, vm, _ ->
            vm.setProfilePrivacy("privacy_show_followers", true); assertEquals(0, f.privacyWrites)
            vm.loadProfile("target"); await { vm.state.value.profile != null }
            vm.setProfilePrivacy("privacy_show_followers", true); assertEquals(0, f.privacyWrites)
        }
    }

}
