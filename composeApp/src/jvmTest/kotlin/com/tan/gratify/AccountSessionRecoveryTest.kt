package com.tan.gratify

import com.sun.net.httpserver.HttpServer
import com.tan.data.sync.*
import com.tan.domain.data.model.sync.SyncReport
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.domain.mediaservice.player.MediaPlayerInterface
import com.tan.domain.repository.*
import com.tan.domain.data.entities.GoogleAccountEntity
import com.tan.gratify.viewModel.SettingsViewModel
import androidx.lifecycle.viewModelScope
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class AccountSessionRecoveryTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name, args.orEmpty()) } as T

    private class Stored(var session: UserSession?) : SessionManager {
        var deletes = 0
        override suspend fun loadSession() = session
        override suspend fun saveSession(session: UserSession) { this.session = session }
        override suspend fun deleteSession() { session = null; deletes++ }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private fun session(id: String, expired: Boolean) = json.decodeFromString<UserSession>(
        """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$id","aud":"authenticated","role":"authenticated","email":"$id@example.invalid","created_at":"2026-10-03T00:00:00Z","app_metadata":{},"user_metadata":{}}}""",
    ).copy(expiresAt = Clock.System.now() + if (expired) (-1).hours else 1.hours)

    private inner class Fixture(expired: Boolean, storedOwner: String?, previous: String?, dbOwner: String?, logged: Boolean, response: Int) {
        val flag = MutableStateFlow(if (logged) DataStoreManager.TRUE else DataStoreManager.FALSE)
        val values = MutableStateFlow(mapOf("last_synced_user_id" to previous.orEmpty(), "AppProfileName" to "My Gratify profile"))
        val cookie = MutableStateFlow("previous-youtube-cookie")
        val pageId = MutableStateFlow("previous-youtube-page")
        var owner = dbOwner
        var activationFailure = false
        val activations = mutableListOf<String>()
        var clears = 0
        var pauses = 0
        var cloudReads = 0
        val status = AtomicInteger(response)
        val stored = Stored(storedOwner?.let { session(it, expired) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = fake<DataStoreManager> { name, args -> when (name) {
            "getLoggedIn" -> flag
            "setLoggedIn" -> { flag.value = if (args[0] as Boolean) DataStoreManager.TRUE else DataStoreManager.FALSE; Unit }
            "getString" -> values.map { it[args[0] as String] }.distinctUntilChanged()
            "putString" -> { values.value = values.value + (args[0] as String to args[1] as String); Unit }
            "captureLegacyProfilePrivacy" -> emptyMap<String, Boolean>()
            "getCookie" -> cookie
            "getPageId" -> pageId
            "setCookie" -> { cookie.value = args[0] as String; pageId.value = args[1] as? String ?: ""; Unit }
            "getYoutubeSubtitleLanguage" -> flowOf("en")
            "getHelpBuildLyricsDatabase", "getSpdc" -> flowOf(DataStoreManager.FALSE)
            "clearPerUserData" -> { clears++; values.value = values.value + ("AppProfileName" to ""); Unit }
            else -> error("Unexpected preferences $name")
        } }
        val repository = fake<UserDataSyncRepository> { name, args -> when (name) {
            "getLocalAccountOwner" -> owner
            "activateLocalAccount" -> {
                if (activationFailure) error("Synthetic database unavailable")
                owner = args[0] as String; activations += owner.orEmpty(); Unit
            }
            "syncDown", "syncUp" -> { cloudReads++; flowOf(Result.success(SyncReport())) }
            else -> error("Unexpected sync $name")
        } }
        val social = fake<SocialRepository> { name, _ -> when (name) {
            "syncDownPlaylists" -> flowOf(Result.success(0))
            "flushPlaylistRemovals" -> Unit
            else -> error("Unexpected social $name")
        } }
        val users = fake<UserRepository> { name, _ -> when (name) {
            "getUserProfile" -> flowOf(null)
            else -> error("Unexpected users $name")
        } }
        val player = fake<MediaPlayerInterface> { name, _ -> if (name == "pause") { pauses++; Unit } else error("Unexpected player $name") }
        val handler = fake<MediaPlayerHandler> { name, _ -> when (name) {
            "getPlayer" -> player
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            "resetSongAndQueue" -> Unit
            else -> error("Unexpected handler $name")
        } }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/auth/v1/token") { exchange ->
                val code = status.get()
                val body = if (code == 200) json.encodeToString(UserSession.serializer(), session(storedOwner ?: "A", false))
                    else """{"msg":"synthetic refresh failure","error_code":"refresh_token_not_found"}"""
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }; exchange.close()
            }
            createContext("/auth/v1/logout") { exchange ->
                val bytes = """{"msg":"synthetic service unavailable"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(503, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }; exchange.close()
            }
            start()
        }
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "synthetic-test-key") {
            install(Auth) { sessionManager = stored; retryDelay = 1.seconds }
        }
        val sync = UserDataSyncManager(repository, social, client, preferences, scope, handler, users)
        lateinit var coordinator: AccountSessionCoordinator
        fun start() { coordinator = AccountSessionCoordinator(client, preferences, sync, scope) }
        suspend fun awaitAccess(predicate: (AccountAccess) -> Boolean): AccountAccess =
            withTimeout(5000) { coordinator.access.first(predicate) }
        suspend fun close() { scope.cancel(); client.close(); server.stop(0) }
    }
    private suspend fun fixture(expired: Boolean = true, storedOwner: String? = "A", previous: String? = "A",
        dbOwner: String? = "A", logged: Boolean = true, response: Int = 503, block: suspend Fixture.() -> Unit) {
        val f = Fixture(expired, storedOwner, previous, dbOwner, logged, response)
        try { f.block() } finally { f.close() }
    }

    private suspend fun Fixture.withSettings(block: suspend (SettingsViewModel) -> Unit) {
        val accounts = fake<AccountRepository> { name, _ -> when (name) {
            "getGoogleAccounts" -> flowOf(listOf(GoogleAccountEntity(email = "youtube@example.invalid", isUsed = true)))
            "updateGoogleAccountUsed" -> flowOf(1)
            "getAccountInfo" -> flowOf(emptyList<com.tan.domain.data.model.account.AccountInfo>())
            else -> error("Unexpected accounts $name")
        } }
        val common = fake<CommonRepository> { name, _ -> if (name == "getDatabasePath") null else error("Unexpected common $name") }
        startKoin { modules(module { single<MediaPlayerHandler> { handler } }) }
        val vm = SettingsViewModel(preferences, common,
            fake<SongRepository> { name, _ -> error("Unexpected song $name") }, accounts,
            fake<CacheRepository> { name, _ -> error("Unexpected cache $name") })
        try { block(vm) } finally { vm.viewModelScope.cancel(); stopKoin() }
    }

    @Test fun expiredSessionKeepsTheSameOfflineLibraryAndLoginFlag() = runBlocking {
        fixture { start(); val ready = awaitAccess { it is AccountAccess.Ready } as AccountAccess.Ready
            assertTrue(ready.offline); assertEquals("A", ready.owner)
            assertEquals(DataStoreManager.TRUE, flag.value); assertEquals("A", owner)
            assertTrue(activations.isEmpty()); assertEquals(0, cloudReads); assertEquals(0, clears)
        }
    }
    @Test fun connectionRecoveryResumesCloudWithoutChangingTheOfflineOwner() = runBlocking {
        fixture { start(); awaitAccess { it is AccountAccess.Ready && it.offline }
            status.set(200); awaitAccess { it is AccountAccess.Ready && !it.offline }
            withTimeout(3000) { while (cloudReads == 0) delay(10) }
            assertEquals("A", owner); assertTrue(activations.isEmpty()); assertEquals(0, clears)
            assertEquals("My Gratify profile", values.value["AppProfileName"])
            assertEquals(DataStoreManager.TRUE, flag.value)
        }
    }
    @Test fun offlineSessionCannotExposeAnotherOwnersLibrary() = runBlocking {
        fixture(dbOwner = "B") { start(); awaitAccess { it == AccountAccess.RecoveryRequired }
            assertEquals("B", owner); assertTrue(activations.isEmpty()); assertEquals(0, cloudReads)
            assertEquals(DataStoreManager.TRUE, flag.value); assertNotNull(stored.session)
        }
    }
    @Test fun mismatchedStoredIdentityCannotEnableOfflineAccess() = runBlocking {
        fixture(storedOwner = "B") { start(); awaitAccess { it == AccountAccess.RecoveryRequired }
            assertEquals("A", owner); assertTrue(activations.isEmpty()); assertEquals(0, clears)
        }
    }
    @Test fun freshInstallationWithoutASessionDoesNotOpenPrivateData() = runBlocking {
        fixture(storedOwner = null, previous = null, dbOwner = null, logged = false) {
            start(); awaitAccess { it == AccountAccess.SignedOut }; assertTrue(activations.isEmpty())
        }
    }
    @Test fun aNewLoginWaitsForItsFlowToCommitLocalOwnership() = runBlocking {
        fixture(expired = false, previous = null, logged = false) {
            start(); awaitAccess { it == AccountAccess.SignedOut }
            assertTrue(activations.isEmpty())
            values.value = values.value + ("last_synced_user_id" to "A")
            flag.value = DataStoreManager.TRUE
            val ready = awaitAccess { it is AccountAccess.Ready } as AccountAccess.Ready
            assertEquals("A", ready.owner); assertNotNull(client.auth.currentSessionOrNull())
        }
    }
    @Test fun databaseFailureRetainsSessionAndRetryCanRestoreAccess() = runBlocking {
        fixture(expired = false) { activationFailure = true; start()
            awaitAccess { it == AccountAccess.RecoveryRequired }
            assertNotNull(client.auth.currentSessionOrNull()); assertEquals(DataStoreManager.TRUE, flag.value)
            assertEquals(0, stored.deletes); activationFailure = false; coordinator.retry()
            awaitAccess { it is AccountAccess.Ready }; assertEquals("A", owner)
        }
    }
    @Test fun revokedSessionHidesAndArchivesTheOutgoingAccount() = runBlocking {
        fixture(response = 400) { start(); awaitAccess { it == AccountAccess.SignedOut }
            assertEquals("", owner); assertEquals(listOf(""), activations)
            assertEquals(DataStoreManager.FALSE, flag.value); assertNull(stored.session)
            assertEquals(1, pauses); assertEquals(1, clears)
        }
    }
    @Test fun explicitLogoutWhileRefreshFailedCannotReopenTheOfflineLibrary() = runBlocking {
        fixture { start(); awaitAccess { it is AccountAccess.Ready }
            sync.performLogout(); awaitAccess { it == AccountAccess.SignedOut }
            assertEquals("", owner); assertNull(stored.session); assertEquals(DataStoreManager.FALSE, flag.value)
            coordinator.retry(); delay(50); assertEquals(AccountAccess.SignedOut, coordinator.access.value)
        }
    }
    @Test fun authenticatedAccountSwitchActivatesOnlyTheIncomingAccount() = runBlocking {
        fixture(expired = false, storedOwner = "B") { start()
            val ready = awaitAccess { it is AccountAccess.Ready } as AccountAccess.Ready
            assertEquals("B", owner); assertEquals("B", ready.owner); assertEquals(listOf("B"), activations)
            assertEquals("B", values.value["last_synced_user_id"]); assertEquals(1, clears); assertEquals(1, pauses)
        }
    }
    @Test fun validStoredSessionRepairsAnOlderIncorrectLoginFlag() = runBlocking {
        fixture(expired = false, logged = false) { start(); awaitAccess { it is AccountAccess.Ready }
            assertEquals(DataStoreManager.TRUE, flag.value); assertEquals("A", owner)
        }
    }
    @Test fun offlineSessionRepairsAnOlderFalseFlagOnlyForItsPersistedOwner() = runBlocking {
        fixture(logged = false) { start(); awaitAccess { it is AccountAccess.Ready && it.offline }
            assertEquals(DataStoreManager.TRUE, flag.value); assertEquals("A", owner)
            assertTrue(activations.isEmpty()); assertEquals(0, cloudReads)
        }
    }
    @Test fun logoutAlsoClearsAValidSessionWhenTheRemoteServerFails() = runBlocking {
        fixture(expired = false) { start(); awaitAccess { it is AccountAccess.Ready }
            sync.performLogout(); awaitAccess { it == AccountAccess.SignedOut }
            assertEquals("", owner); assertNull(stored.session); assertEquals(DataStoreManager.FALSE, flag.value)
        }
    }
    @Test fun disconnectingYouTubeLeavesGratifySessionAndProfileIntact() = runBlocking {
        fixture(expired = false) { start(); awaitAccess { it is AccountAccess.Ready }
            withSettings { vm -> vm.logOutAllYouTube()
                withTimeout(3000) { cookie.first { it.isEmpty() } }
                assertEquals(DataStoreManager.TRUE, flag.value); assertNotNull(client.auth.currentSessionOrNull())
                assertEquals("My Gratify profile", values.value["AppProfileName"]); assertEquals(0, clears)
                assertEquals("A", owner); assertEquals(0, stored.deletes)
            }
        }
    }
    @Test fun switchingYouTubeDoesNotOverwriteOrClearGratifyProfile() = runBlocking {
        fixture(expired = false) { start(); awaitAccess { it is AccountAccess.Ready }
            withSettings { vm -> vm.setUsedAccount(GoogleAccountEntity(email = "other@example.invalid", name = "YouTube Name", cache = "new-youtube-cookie"))
                withTimeout(3000) { cookie.first { it == "new-youtube-cookie" } }
                assertEquals("YouTube Name", values.value["YouTubeAccountName"])
                assertEquals("My Gratify profile", values.value["AppProfileName"])
                assertEquals(DataStoreManager.TRUE, flag.value); assertEquals(0, clears)
                assertEquals("A", client.auth.currentUserOrNull()?.id)
            }
        }
    }
    @Test fun failedYouTubeSignInRestoresCookieWithoutChangingGratifyLogin() = runBlocking {
        fixture(expired = false) { start(); awaitAccess { it is AccountAccess.Ready }
            withSettings { vm -> assertFalse(vm.addAccount("invalid-youtube-cookie"))
                assertEquals("previous-youtube-cookie", cookie.value); assertEquals("previous-youtube-page", pageId.value)
                assertEquals(DataStoreManager.TRUE, flag.value); assertEquals(0, clears)
                assertEquals("My Gratify profile", values.value["AppProfileName"])
            }
        }
    }
}
