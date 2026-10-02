package com.tan.gratify

import com.eygraber.uri.Uri
import com.sun.net.httpserver.HttpServer
import com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.FileTime
import kotlin.test.*

class DesktopDeepLinkTest {
    private val callback = "com.tan.gratify://login-callback?flow=recovery#access_token=synthetic-access&refresh_token=synthetic-refresh&expires_in=3600&token_type=bearer&type=recovery"

    @AfterTest fun reset() {
        DesktopDeepLinkHandler.listener = null
        PasswordRecoveryCoordinator.clear()
    }

    @Test fun authArgumentsAndFragmentsReachTheListenerUnchanged() {
        assertTrue(DesktopDeepLinkHandler.acceptsArgument(callback))
        DesktopDeepLinkHandler.onNewUri(callback)
        var received: String? = null
        DesktopDeepLinkHandler.listener = { received = it.data?.toString() }
        assertEquals(callback, received)
        assertTrue(DesktopAuthCallback.matches(Uri.parse(received!!)))
        assertTrue(DesktopAuthCallback.isRecovery(Uri.parse(received!!)))
    }

    @Test fun contentRoutesAndOpenAppStillWork() {
        val links = mapOf("gratify://watch?v=video" to "https://gratify.org/app/watch?v=video",
            "gratify://playlist?list=list" to "https://gratify.org/app/playlist?list=list",
            "gratify://channel/UCsynthetic" to "https://gratify.org/app/channel/UCsynthetic",
            "gratify://album?id=album" to "https://gratify.org/app/album?id=album")
        links.forEach { (input, expected) -> assertEquals(expected, DesktopDeepLinkHandler.parseToIntent(input).data.toString()) }
        assertNull(DesktopDeepLinkHandler.parseToIntent("gratify://open-app").data)
        assertEquals("https://gratify.org/app/watch?v=video", DesktopDeepLinkHandler.parseToIntent(
            "gratify://open-app?url=https%3A%2F%2Fgratify.org%2Fapp%2Fwatch%3Fv%3Dvideo").data.toString())
    }

    @Test fun callbackFailureCannotEnablePasswordReset(): Unit = runBlocking {
        val handler = DesktopAuthCallback { error("synthetic confidential failure") }
        assertTrue(handler.handle(Uri.parse(callback)))
        assertTrue(PasswordRecoveryCoordinator.pending.value)
        assertNull(PasswordRecoveryCoordinator.verifiedUserId.value)
    }

    @Test fun unrelatedLinksNeverAuthenticate(): Unit = runBlocking {
        var attempts = 0
        val handler = DesktopAuthCallback { attempts++; "synthetic-user" }
        listOf("https://evil.invalid/login-callback?flow=recovery", "gratify://login-callback?flow=recovery",
            "com.tan.gratify://other?flow=recovery").forEach { assertFalse(handler.handle(Uri.parse(it))) }
        assertEquals(0, attempts)
        assertFalse(PasswordRecoveryCoordinator.pending.value)
    }

    @Test fun callbackCancellationPropagatesWithoutVerifying(): Unit = runBlocking {
        val handler = DesktopAuthCallback { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { handler.handle(Uri.parse(callback)) }
        assertNull(PasswordRecoveryCoordinator.verifiedUserId.value)
    }

    @Test fun actualSdkVerifiesServerUserBeforeImportingRecoverySession(): Unit = runBlocking {
        withAuthServer(200) { auth, requests ->
            assertTrue(DesktopAuthCallback(auth).handle(Uri.parse(callback)))
            assertEquals(1, requests())
            assertEquals("synthetic-user-a", auth.currentUserOrNull()?.id)
            assertEquals("synthetic-user-a", PasswordRecoveryCoordinator.verifiedUserId.value)
        }
    }

    @Test fun actualSdkRejectsInvalidAndMalformedCallbacksWithoutReplacingSession(): Unit = runBlocking {
        withAuthServer(401) { auth, requests ->
            val handler = DesktopAuthCallback(auth)
            handler.handle(Uri.parse(callback))
            assertEquals(1, requests())
            assertNull(auth.currentSessionOrNull())
            assertNull(PasswordRecoveryCoordinator.verifiedUserId.value)
            handler.handle(Uri.parse("com.tan.gratify://login-callback?flow=recovery#type=recovery"))
            assertEquals(1, requests())
            assertNull(PasswordRecoveryCoordinator.verifiedUserId.value)
        }
    }

    @Test fun privateInboxConsumesOnceAndHandlesWriteAfterAnEarlyRestoreSignal() {
        val root = Files.createTempDirectory("gratify-desktop-links-")
        try {
            val inbox = DesktopLinkInbox(root.resolve("links"))
            assertNull(inbox.consume()) // restore request arrives before the second process writes
            inbox.write(callback)
            val file = root.resolve("links/pending-uri.txt")
            Files.getFileAttributeView(file, AclFileAttributeView::class.java)?.let { acl ->
                assertTrue(acl.acl.all { it.principal() == acl.owner })
            }
            assertEquals(callback, inbox.consume())
            assertNull(inbox.consume())
            assertFalse(Files.exists(file))
            assertEquals(0L, Files.list(file.parent).use { it.count() })
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun staleOversizedAndUnsupportedInboxEntriesAreRemoved() {
        val root = Files.createTempDirectory("gratify-desktop-links-")
        try {
            val inbox = DesktopLinkInbox(root)
            val file = root.resolve("pending-uri.txt")
            inbox.write(callback)
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 6 * 60 * 1000))
            assertNull(inbox.consume())
            Files.writeString(file, "gratify://" + "x".repeat(DesktopLinkInbox.MAX_URI_LENGTH))
            assertNull(inbox.consume())
            Files.writeString(file, "file:///synthetic")
            assertNull(inbox.consume())
            assertFalse(Files.exists(file))
            assertFailsWith<IllegalArgumentException> { inbox.write("gratify://" + "x".repeat(DesktopLinkInbox.MAX_URI_LENGTH)) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun windowsProtocolCommandPreservesQuotesAndSpacesWithoutAShell() {
        assertEquals(listOf("gratify", "com.tan.gratify"), WindowsProtocolRegistrar.schemes)
        val expected = WindowsProtocolRegistrar.commandValue("C:\\Synthetic App & Music\\Gratify.exe")
        assertEquals("\"C:\\Synthetic App & Music\\Gratify.exe\" \"%1\"", expected)
        if (!System.getProperty("os.name").contains("Windows", true)) return
        // A disposable key only; never reads or changes an installed URI handler.
        val key = "HKCU\\Software\\GratifyAudit\\${java.util.UUID.randomUUID()}"
        var written = false
        try {
            val write = ProcessBuilder(WindowsProtocolRegistrar.registrationCommand(key, null, expected)).redirectErrorStream(true).start()
            val writeOutput = write.inputStream.bufferedReader().use { it.readText() }
            val writeExit = write.waitFor()
            written = writeExit == 0
            assertEquals(0, writeExit, writeOutput)
            val query = ProcessBuilder("reg.exe", "query", key, "/ve").redirectErrorStream(true).start()
            val output = query.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, query.waitFor())
            assertTrue(output.trim().endsWith(expected))
        } finally {
            if (written) {
                val remove = ProcessBuilder("reg.exe", "delete", key, "/f").redirectErrorStream(true).start()
                remove.inputStream.bufferedReader().use { it.readText() }
                assertEquals(0, remove.waitFor())
            }
        }
    }

    private suspend fun withAuthServer(status: Int, block: suspend (Auth, () -> Int) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests = 0
        server.createContext("/auth/v1/user") { exchange ->
            requests++
            val body = if (status == 200) """{"id":"synthetic-user-a","aud":"authenticated","role":"authenticated","email":"desktop-a@example.invalid","created_at":"2026-10-02T00:00:00Z","app_metadata":{},"user_metadata":{}}"""
                else """{"msg":"Invalid token"}"""
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "synthetic-test-key") {
            install(Auth) { autoLoadFromStorage = false; autoSaveToStorage = false; alwaysAutoRefresh = false }
        }
        try { block(client.auth) { requests } } finally { client.close(); server.stop(0) }
    }
}
