package com.tan.data.repository

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlin.test.*

class RepositoryCancellationTest {
    @Test fun playlistSnapshotResponsePreservesMoreThanOneThousandTracks(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val response = (0 until 1025).joinToString(prefix = "[", postfix = "]") {
            "{\"playlist_id\":\"P\",\"video_id\":\"v$it\",\"title\":\"Song $it\",\"artists\":\"Artist\",\"position\":$it}"
        }.toByteArray()
        server.createContext("/rest/v1/rpc/gratify_get_shared_playlist_tracks") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }; exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "synthetic-test-key") { install(Postgrest) }
        try {
            val tracks = withTimeout(10000) { SharedPlaylistRepositoryImpl(client).getSharedPlaylistTracks("P").firstOrNull()!!.getOrThrow() }
            assertEquals(1025, tracks.size)
            assertEquals((0 until 1025).map { "v$it" }, tracks.map { it.videoId })
        } finally { client.close(); server.stop(0) }
    }

    @Test fun firstOrNullCancelsActualRepositoryFlowsWithoutASecondEmission(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/rest/v1/") { exchange ->
            val response = when (exchange.requestURI.path.substringAfterLast('/')) {
                "public_profiles" -> "[{\"id\":\"A\",\"display_name\":\"A\"}]"
                "gratify_get_shared_playlist_tracks" -> "[{\"playlist_id\":\"P\",\"video_id\":\"V\",\"title\":\"Song\",\"artists\":\"Artist\"}]"
                else -> "[]"
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type","application/json")
            exchange.sendResponseHeaders(200,response.size.toLong()); exchange.responseBody.use { it.write(response) }; exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}","synthetic-test-key") { install(Postgrest) }
        try {
            withTimeout(10000) {
                val users = UserRepositoryImpl(client)
                assertEquals("A",users.getUserProfile("A").firstOrNull()!!.id)
                assertTrue(users.followUser("A","B").firstOrNull()!!.isSuccess)
                assertEquals("V",SharedPlaylistRepositoryImpl(client).getSharedPlaylistTracks("P").firstOrNull()!!.getOrThrow().single().videoId)
            }
        } finally { client.close(); server.stop(0) }
    }
}
