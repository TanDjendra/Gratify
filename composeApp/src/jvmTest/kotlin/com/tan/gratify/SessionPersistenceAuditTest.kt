package com.tan.gratify

import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Audit the actual installed SDK, with synthetic sessions and a local auth server only. */
class SessionPersistenceAuditTest {
    private class MemorySessions(var saved: UserSession?) : SessionManager {
        var deletes = 0
        override suspend fun loadSession() = saved
        override suspend fun saveSession(session: UserSession) { saved = session }
        override suspend fun deleteSession() { saved = null; deletes++ }
    }

    private fun session(expired: Boolean): UserSession = Json { ignoreUnknownKeys = true }.decodeFromString<UserSession>(
        """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"synthetic-offline-user","aud":"authenticated","role":"authenticated","created_at":"2026-10-03T00:00:00Z","app_metadata":{},"user_metadata":{}}}""",
    ).copy(expiresAt = Clock.System.now() + if (expired) (-1).hours else 1.hours)

    private suspend fun fixture(expired: Boolean, response: Int?, block: suspend (Auth, MemorySessions, AtomicInteger) -> Unit) {
        val saved = MemorySessions(session(expired))
        val status = AtomicInteger(response ?: 503)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/auth/v1/token") { exchange ->
            val code = status.get()
            val body = if (code == 200) Json.encodeToString(UserSession.serializer(), session(false))
                else """{"msg":"synthetic refresh failure","error_code":"refresh_token_not_found"}"""
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        val port = server.address.port
        if (response == null) server.stop(0)
        val client = createSupabaseClient("http://127.0.0.1:$port", "synthetic-test-key") {
            install(Auth) { sessionManager = saved; retryDelay = 2.seconds }
        }
        try {
            withTimeout(5000) { client.auth.awaitInitialization() }
            // Same delay used by Gratify's splash after awaitInitialization.
            delay(300)
            block(client.auth, saved, status)
        } finally { client.close(); server.stop(0) }
    }

    @Test fun unexpiredStoredSessionRestoresWithoutInternet() = runBlocking {
        fixture(expired = false, response = null) { auth, saved, _ ->
            assertIs<SessionStatus.Authenticated>(auth.sessionStatus.value)
            assertNotNull(auth.currentSessionOrNull())
            assertNotNull(saved.saved)
            assertEquals(0, saved.deletes)
        }
    }

    @Test fun expiredStoredSessionOfflineLooksMissingButIsStillStored() = runBlocking {
        fixture(expired = true, response = null) { auth, saved, _ ->
            assertIs<SessionStatus.RefreshFailure>(auth.sessionStatus.value)
            assertNull(auth.currentSessionOrNull())
            assertNull(auth.currentUserOrNull())
            assertNotNull(saved.saved)
            assertEquals(0, saved.deletes)
        }
    }

    @Test fun serverOutageAlsoLooksMissingWithoutDeletingStoredSession() = runBlocking {
        fixture(expired = true, response = 503) { auth, saved, _ ->
            assertIs<SessionStatus.RefreshFailure>(auth.sessionStatus.value)
            assertNull(auth.currentSessionOrNull())
            assertNotNull(saved.saved)
            assertEquals(0, saved.deletes)
        }
    }

    @Test fun revokedRefreshTokenReallyRemovesStoredSession() = runBlocking {
        fixture(expired = true, response = 400) { auth, saved, _ ->
            assertIs<SessionStatus.NotAuthenticated>(auth.sessionStatus.value)
            assertNull(auth.currentSessionOrNull())
            assertNull(saved.saved)
            assertEquals(1, saved.deletes)
        }
    }

    @Test fun transientFailureRecoversWithoutUserSigningInAgain() = runBlocking {
        fixture(expired = true, response = 503) { auth, saved, status ->
            assertIs<SessionStatus.RefreshFailure>(auth.sessionStatus.value)
            assertNull(auth.currentSessionOrNull())
            status.set(200)
            withTimeout(5000) { auth.sessionStatus.first { it is SessionStatus.Authenticated } }
            assertEquals("synthetic-offline-user", auth.currentUserOrNull()?.id)
            assertNotNull(saved.saved)
            assertEquals(0, saved.deletes)
        }
    }
}
