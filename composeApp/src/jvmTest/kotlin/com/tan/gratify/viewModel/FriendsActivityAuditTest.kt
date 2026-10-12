package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
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
import kotlinx.serialization.decodeFromString
import java.lang.reflect.Proxy
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.*

class FriendsActivityAuditTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            answer(method.name, args.orEmpty())
        } as T

    private class Fixture {
        val now = System.currentTimeMillis()
        var following = (0..44).map { UserProfile("friend-$it", "Teman $it", showRecentArtists = true,
            nowPlayingTitle = "Ruang tenang", nowPlayingVideoId = "song-$it", lastActiveAt = now.toString()) }
        var followers = following
        var own = UserProfile("self", "Alia", note = "Catatan lama", noteUpdatedAt = now.toString())
        var readFailure = false
        var writeFailure = false
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var profileGate: CompletableDeferred<Unit>? = null
        val followingOffsets = mutableListOf<Int>()
        val followerOffsets = mutableListOf<Int>()
        val notes = mutableListOf<String?>()
        var forbiddenPlaylistReads = 0
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(10) }

    private suspend fun fixture(block: suspend (Fixture, FriendsActivityViewModel, suspend (String) -> Unit) -> Unit) {
        val f = Fixture()
        val client = createSupabaseClient("http://127.0.0.1:9", "synthetic-test-key") {
            install(Auth) { autoLoadFromStorage = false; autoSaveToStorage = false; alwaysAutoRefresh = false }
        }
        suspend fun login(id: String) {
            client.auth.importSession(Json { ignoreUnknownKeys = true }.decodeFromString<UserSession>(
                """{"access_token":"synthetic-$id","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$id","aud":"authenticated","role":"authenticated","created_at":"2026-10-08T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""), autoRefresh = false)
        }
        login("self")
        val player = fake<MediaPlayerHandler> { name, _ -> when (name) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            else -> error("Unexpected player: $name")
        } }
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        val users = fake<UserRepository> { name, args -> when (name) {
            "fetchFollowing", "fetchFollowers" -> flow<Result<List<UserProfile>>> {
                f.readGate?.await()
                val offset = args[1] as Int
                val values = if (name == "fetchFollowing") f.following else f.followers
                (if (name == "fetchFollowing") f.followingOffsets else f.followerOffsets).add(offset)
                emit(if (f.readFailure) Result.failure(IllegalStateException("offline"))
                    else Result.success(values.drop(offset).take(args[2] as Int)))
            }
            "fetchUserProfile" -> flow<Result<UserProfile>> {
                val snapshot = f.own
                f.profileGate?.await()
                emit(Result.success(snapshot))
            }
            "updateNote" -> flow<Result<Unit>> {
                f.notes.add(args[1] as String?)
                f.writeGate?.await()
                emit(if (f.writeFailure) Result.failure(IllegalStateException("offline")) else Result.success(Unit))
            }
            else -> error("Unexpected users: $name")
        } }
        val social = fake<SocialRepository> { name, _ -> f.forbiddenPlaylistReads++; error("Unexpected public playlist fallback: $name") }
        val vm = FriendsActivityViewModel(users, client, social)
        try { block(f, vm, ::login) }
        finally { vm.stop(); vm.viewModelScope.cancel(); client.close(); stopKoin() }
    }

    @Test fun activityRespectsPrivacyExpiryFutureTimesAndMissingMetadata() {
        val now = System.currentTimeMillis()
        val valid = UserProfile("friend", "Nara", showRecentArtists = true, nowPlayingTitle = "Ruang tenang",
            nowPlayingVideoId = "video", lastActiveAt = now.toString())
        assertTrue(friendActivityAt(valid, now).isOnline)
        for (profile in listOf(valid.copy(showRecentArtists = false), valid.copy(lastActiveAt = (now - 600_001).toString()),
            valid.copy(lastActiveAt = (now + 1).toString()), valid.copy(lastActiveAt = "invalid"),
            valid.copy(nowPlayingVideoId = ""), valid.copy(nowPlayingTitle = ""))) {
            val item = friendActivityAt(profile, now)
            assertFalse(item.isOnline)
            assertEquals("", item.videoId)
            assertEquals("", item.songTitle)
        }
    }

    @Test fun mutualFriendsIncludeLaterPagesAndNeverUsePublicPlaylistAsHistory() = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.following = f.following.map { it.copy(nowPlayingTitle = null, nowPlayingVideoId = null) } + UserProfile("outsider", "Other")
            vm.loadFriendsActivity(); await { !vm.state.value.isLoading }
            assertEquals(45, vm.state.value.friendsList.size)
            assertEquals(listOf(0, 40), f.followingOffsets)
            assertEquals(listOf(0, 40), f.followerOffsets)
            assertTrue(vm.state.value.friendsList.all { it.videoId.isBlank() && !it.isOnline })
            assertEquals(0, f.forbiddenPlaylistReads)
        }
    }

    @Test fun failedReadPreservesKnownFriendsAndShowsRecoveryInsteadOfFalseEmpty() = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            vm.loadFriendsActivity(); await { !vm.state.value.isLoading }
            val before = vm.state.value.friendsList
            f.readFailure = true; vm.loadFriendsActivity(); await { !vm.state.value.isLoading }
            assertNotNull(vm.state.value.error)
            assertEquals(before, vm.state.value.friendsList)
            f.readFailure = false; vm.loadFriendsActivity(); await { !vm.state.value.isLoading }
            assertNull(vm.state.value.error)
        }
    }

    @Test fun failedNoteDoesNotPretendToSaveAndRetryCommitsTrimmedTextOnce() = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            vm.loadFriendsActivity(); await { vm.state.value.myNote != null }
            f.writeFailure = true
            var success = 0
            vm.updateMyNote("  Baru  ") { success++ }; await { !vm.state.value.noteSaving }
            assertEquals("Catatan lama", vm.state.value.myNote)
            assertNotNull(vm.state.value.noteError)
            assertEquals(0, success)
            f.writeFailure = false; f.writeGate = CompletableDeferred()
            vm.updateMyNote("  Baru  ") { success++ }
            vm.updateMyNote("Double click") { success++ }
            assertTrue(vm.state.value.noteSaving)
            f.writeGate!!.complete(Unit); await { !vm.state.value.noteSaving }
            assertEquals("Baru", vm.state.value.myNote)
            assertEquals(1, success)
            assertEquals(listOf<String?>("Baru", "Baru"), f.notes)
            assertNull(vm.state.value.noteError)
            vm.updateMyNote(""); await { !vm.state.value.noteSaving }
            assertNull(vm.state.value.myNote)
        }
    }

    @Test fun delayedProfileReadCannotOverwriteAJustSavedNote() = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, _ ->
            f.profileGate = CompletableDeferred()
            vm.loadFriendsActivity(); await { !vm.state.value.isLoading }
            vm.updateMyNote("Catatan terbaru"); await { !vm.state.value.noteSaving }
            f.profileGate!!.complete(Unit); delay(50)
            assertEquals("Catatan terbaru", vm.state.value.myNote)
        }
    }

    @Test fun accountChangeClearsOldActivityAndOldSaveCannotCloseNewAccountsEditor() = runBlocking(Dispatchers.Swing) {
        fixture { f, vm, login ->
            vm.start(); await { vm.state.value.myNote != null }
            f.writeGate = CompletableDeferred()
            var closed = false
            vm.updateMyNote("Old account draft") { closed = true }
            await { f.notes.isNotEmpty() }
            f.readGate = CompletableDeferred()
            login("other"); await { vm.state.value.currentUserId == "other" }
            assertTrue(vm.state.value.friendsList.isEmpty())
            assertNull(vm.state.value.myNote)
            f.writeGate!!.complete(Unit); delay(50)
            assertFalse(closed)
            assertNull(vm.state.value.myNote)
            f.readGate!!.complete(Unit)
        }
    }
}
