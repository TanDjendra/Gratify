package com.tan.data.repository

import com.sun.net.httpserver.HttpServer
import com.tan.domain.repository.VisibleFollowCounts
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.entities.NowPlayingUpdate
import kotlinx.serialization.json.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class ProfileReadRegressionTest {
    private data class Request(val method: String, val path: String, val query: String, val prefer: String?, val body: String)
    private suspend fun server(code: Int = 200, body: (Request) -> String = { "[]" }, block: suspend (UserRepositoryImpl, List<Request>) -> Unit) {
        val requests = CopyOnWriteArrayList<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/") { exchange ->
            val request = Request(exchange.requestMethod, exchange.requestURI.path, URLDecoder.decode(exchange.requestURI.rawQuery.orEmpty(), "UTF-8"), exchange.requestHeaders.getFirst("Prefer"), exchange.requestBody.bufferedReader().readText())
            requests += request
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (request.method == "HEAD") {
                exchange.responseHeaders.add("Content-Range", "0-0/7")
                exchange.sendResponseHeaders(code, -1)
            } else {
                val response = (if (code == 200) body(request) else "{\"code\":\"OFFLINE\",\"message\":\"synthetic failure\"}").toByteArray()
                exchange.sendResponseHeaders(code, response.size.toLong()); exchange.responseBody.use { it.write(response) }
            }
            exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "synthetic-test-key") { install(Postgrest) }
        try { withTimeout(10_000) { block(UserRepositoryImpl(client), requests) } }
        finally { client.close(); server.stop(0) }
    }
    @Test fun successfulEmptyResponseMeansMissingAccount(): Unit = runBlocking {
        server { users, requests ->
            val result = users.fetchUserProfile("A").first(); assertTrue(result.isSuccess); assertNull(result.getOrThrow())
            assertEquals(1, requests.size); assertEquals("GET", requests.single().method)
        }
    }
    @Test fun identitySaveExplicitlyRemovesAvatarWithoutChangingPrivacyActivityOrNotes(): Unit = runBlocking {
        server { users, requests ->
            users.upsertUserProfile(UserProfile("owner", "Nara", null, note = "Keep this", showPlaylists = true)).first().getOrThrow()
            val json = Json.parseToJsonElement(requests.single().body).jsonArray.single().jsonObject
            assertEquals(setOf("id", "display_name", "avatar_url"), json.keys)
            assertEquals("Nara", json["display_name"]!!.jsonPrimitive.content); assertEquals(JsonNull, json["avatar_url"])
            assertEquals("POST", requests.single().method)
        }
    }
    @Test fun playbackUpdateCannotOverwriteIdentityAndExplicitlyClearsStoppedActivity(): Unit = runBlocking {
        server { users, requests ->
            users.updateNowPlaying("owner", NowPlayingUpdate(displayName = "Stale name", avatarUrl = "Stale avatar", lastActiveAt = "123")).first().getOrThrow()
            val json = Json.parseToJsonElement(requests.single().body).jsonObject
            assertEquals(setOf("now_playing_video_id", "now_playing_title", "now_playing_artist", "last_active_at"), json.keys)
            assertEquals(JsonNull, json["now_playing_video_id"]); assertEquals(JsonNull, json["now_playing_title"])
            assertEquals(JsonNull, json["now_playing_artist"]); assertEquals("123", json["last_active_at"]!!.jsonPrimitive.content)
            assertEquals("PATCH", requests.single().method); assertTrue(requests.single().query.contains("id=eq.owner"))
        }
    }
    @Test fun failedProfileReadRetainsItsFailure(): Unit = runBlocking {
        server(500) { users, _ -> assertTrue(users.fetchUserProfile("A").first().isFailure) }
    }
    @Test fun headCountsAvoidDownloadingProfilesOrRelationships(): Unit = runBlocking {
        server { users, requests ->
            assertEquals(VisibleFollowCounts(7, 7), users.getVisibleFollowCounts("A").first().getOrThrow())
            assertEquals(2, requests.size)
            assertTrue(requests.all { it.method == "HEAD" && it.path.endsWith("/follows") && it.prefer?.contains("count=exact") == true })
            assertTrue(requests.first().query.contains("following_id=eq.A"))
        }
    }
    @Test fun failedCountsAreNotReportedAsZero(): Unit = runBlocking {
        server(503) { users, _ -> assertTrue(users.getVisibleFollowCounts("A").first().isFailure) }
    }
    @Test fun pagedFollowersKeepTheServerOrderAndKnownMissingProfiles(): Unit = runBlocking {
        server(body = { request -> if (request.path.endsWith("follows"))
            "[{\"follower_id\":\"C\",\"following_id\":\"A\"},{\"follower_id\":\"D\",\"following_id\":\"A\"}]"
            else "[{\"id\":\"D\",\"display_name\":\"D name\"}]" }) { users, requests ->
            val page = users.fetchFollowers("A", 40, 40).first().getOrThrow()
            assertEquals(listOf("C", "D"), page.map { it.id }); assertEquals("D name", page[1].displayName)
            assertTrue(requests.first().query.contains("offset=40")); assertTrue(requests.first().query.contains("limit=40"))
            assertTrue(requests.first().query.contains("order=follower_id.asc"))
        }
    }
    @Test fun relationErrorsRemainFailures(): Unit = runBlocking {
        server(403) { users, _ -> assertTrue(users.fetchFollowers("A").first().isFailure) }
    }
    @Test fun searchSelectsOnlyIdentityFieldsAndPagesDeterministically(): Unit = runBlocking {
        server(body = { "[{\"id\":\"A\",\"display_name\":\"Nara\"}]" }) { users, requests ->
            assertEquals("Nara", users.searchUsersPage("Nara", 40, 40).first().single().displayName)
            val query = requests.single().query
            assertTrue(query.contains("select=id,display_name,avatar_url"), query)
            assertTrue(query.contains("order=display_name.asc") && query.contains(",id.asc"), query)
            assertTrue(query.contains("offset=40") && query.contains("limit=40"), query)
            assertTrue(requests.all { it.method == "GET" })
        }
    }
    @Test fun searchFailureDoesNotBecomeEmptyResults(): Unit = runBlocking {
        server(500) { users, _ -> assertFails { users.searchUsersPage("Nara").first() } }
    }
    @Test fun existingFriendsSearchDoesNotInheritTheProfilePageLimit(): Unit = runBlocking {
        server(body = { (0..44).joinToString(prefix = "[", postfix = "]") { "{\"id\":\"user-$it\",\"display_name\":\"Nara $it\"}" } }) { users, requests ->
            assertEquals(45, users.searchUsers("Nara").first().size)
            assertFalse(requests.single().query.contains("limit="))
            assertFalse(requests.single().query.contains("offset="))
        }
    }

}
