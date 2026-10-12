package com.tan.gratify.viewModel

import com.sun.net.httpserver.HttpServer
import com.tan.data.repository.UserRepositoryImpl
import com.tan.domain.manager.DataStoreManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import java.awt.image.BufferedImage
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AvatarSaveGatewayTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private data class Request(val path: String, val method: String, val body: ByteArray, val upsert: String?, val type: String?)
    private fun user(metadata: JsonObject = buildJsonObject {}) = """{"id":"$owner","aud":"authenticated","role":"authenticated","created_at":"2026-10-12T00:00:00Z","app_metadata":{},"user_metadata":$metadata}"""
    private suspend fun fixture(uploadFails: () -> Boolean = { false }, block: suspend (SupabaseClient, SupabaseProfileEditorGateway, List<Request>, List<Pair<String,String>>, String) -> Unit) {
        val requests = CopyOnWriteArrayList<Request>()
        val caches = mutableListOf<Pair<String,String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val r = Request(exchange.requestURI.path, exchange.requestMethod, exchange.requestBody.readBytes(),
                exchange.requestHeaders.getFirst("x-upsert"), exchange.requestHeaders.getFirst("Content-Type"))
            requests += r
            val upload = r.path.startsWith("/storage/v1/object/avatars/")
            val rejected = upload && uploadFails()
            val body = when {
                rejected -> """{"statusCode":"500","error":"Unavailable","message":"synthetic upload failure"}"""
                upload -> """{"Key":"avatars/$owner.jpg","Id":"synthetic-object"}"""
                r.path == "/auth/v1/user" -> user(Json.parseToJsonElement(r.body.decodeToString()).jsonObject["data"]!!.jsonObject)
                r.path == "/rest/v1/profiles" -> "[]"
                else -> error("Unexpected request ${r.path}")
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (rejected) 500 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }; exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "synthetic-test-key") {
            install(Auth) { autoLoadFromStorage = false; autoSaveToStorage = false; alwaysAutoRefresh = false }
            install(Postgrest); install(Storage)
        }
        val preferences = Proxy.newProxyInstance(DataStoreManager::class.java.classLoader, arrayOf(DataStoreManager::class.java)) { _, method, args ->
            check(method.name == "putString")
            caches += (args[0] as String) to (args[1] as String)
            Unit
        } as DataStoreManager
        val path = Files.createTempFile("gratify-avatar-test-", ".jpg")
        ImageIO.write(BufferedImage(512,512,BufferedImage.TYPE_INT_RGB), "jpeg", path.toFile())
        try {
            client.auth.importSession(Json { ignoreUnknownKeys = true }.decodeFromString<UserSession>(
                """{"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600,"token_type":"bearer","user":${user()}}"""), autoRefresh = false)
            block(client, SupabaseProfileEditorGateway(client, UserRepositoryImpl(client), preferences), requests, caches, path.toString())
        } finally { client.close(); server.stop(0); Files.deleteIfExists(path) }
    }
    @Test fun replacingAvatarUsesOwnerJpegUpsertAndUploadRejectionCanBeRetried(): Unit = runBlocking {
        var reject = true
        fixture({ reject }) { _, gateway, requests, _, path ->
            val failure = assertFailsWith<ProfileWriteException> { gateway.uploadAvatar(owner, path) }
            assertEquals(ProfileSaveProblem.PHOTO_UPLOAD_FAILED, failure.problem)
            reject = false
            val url = gateway.uploadAvatar(owner, path)
            assertTrue(url.contains("/storage/v1/object/public/avatars/$owner.jpg?cacheNonce="))
            assertNotNull(url.substringAfter("cacheNonce=").toLongOrNull())
            assertEquals(2, requests.size)
            for (r in requests) {
                assertEquals("/storage/v1/object/avatars/$owner.jpg", r.path)
                assertEquals("POST", r.method); assertEquals("true", r.upsert)
                assertTrue(r.type!!.startsWith("image/jpeg"))
                assertEquals(0xff.toByte(), r.body[0]); assertEquals(0xd8.toByte(), r.body[1])
            }
        }
    }
    @Test fun wrongOwnerAndMissingCropFileNeverReachStorage(): Unit = runBlocking {
        fixture { _, gateway, requests, _, _ ->
            assertEquals(ProfileSaveProblem.ACCOUNT_CHANGED, assertFailsWith<ProfileWriteException> {
                gateway.uploadAvatar("other-owner", "missing-photo")
            }.problem)
            assertEquals(ProfileSaveProblem.PHOTO_FAILED, assertFailsWith<ProfileWriteException> {
                gateway.uploadAvatar(owner, "missing-photo")
            }.problem)
            assertTrue(requests.isEmpty())
        }
    }
    @Test fun sdkMetadataProfileAndCacheWritesKeepTheSameReplacementIdentity(): Unit = runBlocking {
        fixture { client, gateway, requests, caches, _ ->
            val avatar = "https://example.invalid/storage/v1/object/public/avatars/$owner.jpg?cacheNonce=2"
            gateway.writeMetadata(owner, "Nara", avatar)
            assertEquals(owner, client.auth.currentUserOrNull()?.id)
            gateway.writeProfile(owner, "Nara", avatar)
            gateway.cacheIdentity(owner, "Nara", avatar)
            assertEquals(listOf("/auth/v1/user", "/rest/v1/profiles"), requests.map { it.path })
            val profile = Json.parseToJsonElement(requests.last().body.decodeToString()).jsonArray.single().jsonObject
            assertEquals(setOf("id", "display_name", "avatar_url"), profile.keys)
            assertEquals(avatar, profile["avatar_url"]!!.jsonPrimitive.content)
            assertEquals(listOf("AppProfileName" to "Nara", "AccountName" to "Nara", "AppProfileImage" to avatar), caches)
        }
    }
}
