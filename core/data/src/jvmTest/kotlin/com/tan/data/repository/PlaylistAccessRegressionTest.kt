package com.tan.data.repository

import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kotlin.test.*

class PlaylistAccessRegressionTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private data class Request(val path: String, val body: String)
    private suspend fun server(ownerId: String? = owner, code: Int = 200,
        body: (Request) -> String = { "{}" }, block: suspend (SupabaseClient,List<Request>) -> Unit) {
        val requests=CopyOnWriteArrayList<Request>()
        val http=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        http.createContext("/") { exchange ->
            val request=Request(exchange.requestURI.path, exchange.requestBody.readBytes().decodeToString())
            requests+=request
            val response=(if(code==200) body(request) else """{"message":"denied","code":"42501"}""").toByteArray()
            exchange.responseHeaders.add("Content-Type","application/json")
            exchange.sendResponseHeaders(code,response.size.toLong()); exchange.responseBody.use { it.write(response) }; exchange.close()
        }
        http.start()
        val client=createSupabaseClient("http://127.0.0.1:${http.address.port}","synthetic-test-key") {
            install(Auth) { autoLoadFromStorage=false; autoSaveToStorage=false; alwaysAutoRefresh=false }
            install(Postgrest); install(Storage)
        }
        try {
            if(ownerId!=null) client.auth.importSession(Json { ignoreUnknownKeys=true }.decodeFromString<UserSession>(
                """{"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$ownerId","aud":"authenticated","role":"authenticated","created_at":"2026-10-03T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""), autoRefresh=false)
            withTimeout(10_000) { block(client,requests) }
        } finally { client.close(); http.stop(0) }
    }

    @Test fun legacyCloudLinkResolvesRealSharedIdentity(): Unit = runBlocking {
        var sharedReads=0
        server(body={ req -> when(req.path) {
            "/rest/v1/shared_playlists" -> if(++sharedReads==1) "[]" else """[{"id":"source-id","user_id":"$owner","title":"Playlist","client_sync_id":"stable-sync-identity"}]"""
            "/rest/v1/cloud_playlists" -> """[{"id":"cloud-id","user_id":"$owner","title":"Playlist","client_sync_id":"stable-sync-identity"}]"""
            else -> "[]"
        } }) { client,_ ->
            assertEquals("source-id",SharedPlaylistRepositoryImpl(client).getSharedPlaylist("cloud-id").getOrThrow().id)
        }
    }
    @Test fun saveUsesAuthenticatedExpectedOwnerAndIdempotentServerRpc(): Unit = runBlocking {
        server { client,requests ->
            val repository=SharedPlaylistRepositoryImpl(client)
            repository.recordPlaylistSave("source-id",owner).getOrThrow()
            repository.removePlaylistSave("source-id",owner).getOrThrow()
            assertEquals(2,requests.size)
            val json=requests.map { Json.parseToJsonElement(it.body).jsonObject }
            assertTrue(requests.all { it.path.endsWith("/rpc/gratify_set_playlist_saved") })
            assertEquals(owner,json[0]["p_expected_owner"]!!.jsonPrimitive.content)
            assertTrue(json[0]["p_saved"]!!.jsonPrimitive.boolean)
            assertFalse(json[1]["p_saved"]!!.jsonPrimitive.boolean)
        }
    }
    @Test fun wrongOwnerCannotSendSaveOrRemoval(): Unit = runBlocking {
        server { client,requests ->
            val repository=SharedPlaylistRepositoryImpl(client)
            assertTrue(repository.recordPlaylistSave("source-id","different-owner").isFailure)
            assertTrue(repository.removePlaylistSave("source-id","different-owner").isFailure)
            assertTrue(requests.isEmpty())
        }
    }
    @Test fun serverRejectionDoesNotBecomeSuccessfulSave(): Unit = runBlocking {
        server(code=403) { client,_ -> assertTrue(SharedPlaylistRepositoryImpl(client).recordPlaylistSave("private-source",owner).isFailure) }
    }
    @Test fun coverSigningIsCachedAndExternalArtworkNeedsNoRequest(): Unit = runBlocking {
        server(body={ """{"signedURL":"/object/sign/playlist-covers/$owner/stable-sync-identity/image.jpg?token=test-signature"}""" }) { client,requests ->
            val covers=PlaylistCoverStorage(client)
            val url="http://127.0.0.1/storage/v1/object/public/playlist-covers/$owner/stable-sync-identity/image.jpg"
            val signed=covers.displayUrl(url)
            assertNotNull(signed); assertTrue(signed.contains("test-signature"))
            assertEquals(signed,covers.displayUrl(url)); assertEquals(1,requests.size)
            assertEquals("https://i.ytimg.com/vi/song/mqdefault.jpg",covers.displayUrl("https://i.ytimg.com/vi/song/mqdefault.jpg"))
            assertEquals(1,requests.size)
            assertFalse(storedPlaylistCoverUrl(signed)!!.contains("token="))
            assertEquals("$owner/stable-sync-identity/image.jpg",playlistCoverPath(signed))
        }
    }
    @Test fun deniedPrivateCoverNeverFallsBackToPublicReadUrl(): Unit = runBlocking {
        server(code=403) { client,_ ->
            assertNull(PlaylistCoverStorage(client).displayUrl("https://test/storage/v1/object/public/playlist-covers/$owner/stable-sync-identity/image.jpg"))
        }
    }
    @Test fun resolvingMultipleCoversPreservesPlaylistOrder(): Unit=runBlocking {
        server(body={ req -> """{"signedURL":"${req.path.removePrefix("/storage/v1")}?token=test-signature"}""" }) { client,requests ->
            val input=(1..7).map { "https://test/storage/v1/object/public/playlist-covers/$owner/stable-sync-identity/$it.jpg" }
            val output=PlaylistCoverStorage(client).map(input,{it}) { _,url -> requireNotNull(url) }
            assertEquals(input.map { playlistCoverPath(it) },output.map { playlistCoverPath(it) })
            assertEquals(7,requests.size)
        }
    }
    @Test fun discoveryUsesPublicOnlyRpcRatherThanOwnerReadableTable(): Unit = runBlocking {
        server(body = { request ->
            assertEquals("/rest/v1/rpc/gratify_get_public_playlists", request.path)
            """[{"id":"public-source","user_id":"$owner","title":"Public","is_public":true}]"""
        }) { client, requests ->
            val playlists = SharedPlaylistRepositoryImpl(client).getSharedPlaylists().first().getOrThrow()
            assertEquals(listOf("public-source"), playlists.map { it.id })
            assertEquals(1, requests.size)
            assertTrue(playlists.single().isPublic)
        }
    }
}
