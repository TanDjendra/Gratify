package com.tan.data.repository

import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class YouTubeLibraryException(val code: String) : Exception(when (code) {
    "CONFIGURATION_REQUIRED" -> "Koneksi YouTube belum diaktifkan oleh pengelola aplikasi."
    "CONNECTION_REQUIRED", "CONSENT_REQUIRED", "ACCOUNT_MISMATCH" -> "Hubungkan Google untuk mengizinkan pembacaan YouTube."
    "QUOTA_EXCEEDED" -> "Batas akses YouTube tercapai. Coba lagi nanti."
    else -> "Data YouTube belum dapat dimuat. Periksa koneksi dan coba lagi."
})

/** All private YouTube requests go through the authenticated backend, never the cookie scraper. */
class OfficialYouTubeLibraryRepository(
    private val supabase: SupabaseClient,
    private val preferences: DataStoreManager,
    private val baseUrl: String,
    private val publicKey: String,
    private val scope: CoroutineScope,
    private val returnScheme: String = "com.tan.gratify",
) : YouTubeLibraryRepository {
    private val client = HttpClient { install(HttpTimeout) { requestTimeoutMillis = 25000 } }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _connection = MutableStateFlow(YouTubeConnectionState())
    override val connection: StateFlow<YouTubeConnectionState> = _connection.asStateFlow()

    override suspend fun isLoginConsentEnabled(): Boolean = try {
        withTimeout(5000) {
            val response = client.get(baseUrl.trimEnd('/') + "/functions/v1/youtube-library?readiness=1") {
                header("apikey", publicKey)
            }
            response.status.value == 200 && json.parseToJsonElement(response.bodyAsText())
                .jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull == true
        }
    } catch (_: TimeoutCancellationException) { false }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }

    override fun cancelConnection() {
        val owner = supabase.auth.currentUserOrNull()?.id ?: return
        // Cancellation must survive disposal of the connection screen's ViewModel.
        scope.launch {
            try { call(owner, buildJsonObject { put("action", "cancel") }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unreachable attempts also expire after five minutes. */ }
        }
    }

    init {
        scope.launch {
            supabase.auth.awaitInitialization()
            supabase.auth.sessionStatus.collectLatest {
                // Do not reuse the old WebView session for personal recommendations or private data.
                if (preferences.cookie.first().isNotEmpty()) preferences.setCookie("", null)
                val uid = supabase.auth.currentUserOrNull()?.id
                if (_connection.value.ownerId != uid) _connection.value = YouTubeConnectionState(uid)
                if (uid == null) _connection.value = YouTubeConnectionState(status = YouTubeConnectionStatus.DISCONNECTED)
                else refreshConnection()
            }
        }
    }

    private suspend fun call(owner: String, body: JsonObject): JsonObject {
        if (supabase.auth.currentUserOrNull()?.id != owner) throw CancellationException("Account changed")
        val jwt = supabase.auth.currentAccessTokenOrNull() ?: throw YouTubeLibraryException("CONNECTION_REQUIRED")
        val response = client.post(baseUrl.trimEnd('/') + "/functions/v1/youtube-library") {
            headers { append(HttpHeaders.Authorization, "Bearer $jwt"); append("apikey", publicKey) }
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (supabase.auth.currentUserOrNull()?.id != owner) throw CancellationException("Account changed")
        val result = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrElse { throw YouTubeLibraryException("UPSTREAM_UNAVAILABLE") }
        if (supabase.auth.currentUserOrNull()?.id != owner) throw CancellationException("Account changed")
        if (response.status.value !in 200..299) {
            val code = if (response.status.value == 404 && result["code"]?.jsonPrimitive?.content == "NOT_FOUND") "CONFIGURATION_REQUIRED"
                else result["code"]?.jsonPrimitive?.content ?: if (response.status.value == 404) "CONFIGURATION_REQUIRED" else "UPSTREAM_UNAVAILABLE"
            if (code in setOf("CONNECTION_REQUIRED", "CONSENT_REQUIRED", "ACCOUNT_MISMATCH"))
                _connection.value = YouTubeConnectionState(owner, YouTubeConnectionStatus.RECONNECT)
            throw YouTubeLibraryException(code)
        }
        return result
    }

    override suspend fun refreshConnection() = mutex.withLock {
        val session = supabase.auth.currentSessionOrNull()
        val owner = session?.user?.id ?: return@withLock
        if (_connection.value.status != YouTubeConnectionStatus.CONNECTED)
            _connection.value = YouTubeConnectionState(owner, YouTubeConnectionStatus.LOADING)
        try {
            val providerToken = session.providerToken
            var grantNeedsConsent = false
            // Only a new provider grant is uploaded. A saved Supabase session must not reconnect
            // YouTube after the user explicitly disconnected it. This is a deduplication marker,
            // not a credential; provider tokens are never duplicated into ordinary preferences.
            val markerKey = "youtube_provider_handled:$owner"
            val marker = providerToken?.hashCode()?.toString()
            if (!providerToken.isNullOrBlank() && preferences.getString(markerKey).first() != marker) {
                try {
                    call(owner, buildJsonObject {
                        put("action", "connect"); put("accessToken", providerToken)
                        session.providerRefreshToken?.let { put("refreshToken", it) }
                    })
                    preferences.putString(markerKey, marker!!)
                } catch (error: YouTubeLibraryException) {
                    if (error.code in setOf("CONFIGURATION_REQUIRED", "UPSTREAM_UNAVAILABLE", "QUOTA_EXCEEDED")) throw error
                    preferences.putString(markerKey, marker!!)
                    grantNeedsConsent = true
                }
            }
            val status = call(owner, buildJsonObject { put("action", "status") })
            _connection.value = YouTubeConnectionState(owner, when {
                status["connected"]?.jsonPrimitive?.booleanOrNull == true -> YouTubeConnectionStatus.CONNECTED
                status["needsReconnect"]?.jsonPrimitive?.booleanOrNull == true || grantNeedsConsent -> YouTubeConnectionStatus.RECONNECT
                else -> YouTubeConnectionStatus.DISCONNECTED
            })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            _connection.value = YouTubeConnectionState(owner, when ((error as? YouTubeLibraryException)?.code) {
                "CONFIGURATION_REQUIRED" -> YouTubeConnectionStatus.CONFIGURATION_REQUIRED
                "CONNECTION_REQUIRED", "CONSENT_REQUIRED", "ACCOUNT_MISMATCH" -> YouTubeConnectionStatus.RECONNECT
                else -> YouTubeConnectionStatus.ERROR
            })
        }
    }

    private suspend fun page(action: String, id: String? = null, cursor: String? = null): YouTubeLibraryPage {
        val owner = supabase.auth.currentUserOrNull()?.id ?: throw YouTubeLibraryException("CONNECTION_REQUIRED")
        val result = call(owner, buildJsonObject {
            put("action", action); id?.let { put("playlistId", it) }; cursor?.let { put("pageToken", it) }
        })
        return json.decodeFromJsonElement(result)
    }
    override suspend fun playlists(pageToken: String?) = page("playlists", cursor = pageToken)
    override suspend fun tracks(playlistId: String, pageToken: String?) = page("tracks", playlistId, pageToken)
    override suspend fun beginConnection(): String {
        val owner = supabase.auth.currentUserOrNull()?.id ?: throw YouTubeLibraryException("CONNECTION_REQUIRED")
        val response = call(owner, buildJsonObject {
            put("action", "authorize"); put("returnScheme", returnScheme)
        })
        val url = response["url"]?.jsonPrimitive?.content ?: throw YouTubeLibraryException("UPSTREAM_UNAVAILABLE")
        val parsed = Url(url)
        if (parsed.protocol != URLProtocol.HTTPS || parsed.host != "accounts.google.com" || parsed.encodedPath != "/o/oauth2/v2/auth")
            throw YouTubeLibraryException("UPSTREAM_UNAVAILABLE")
        return url
    }
    override suspend fun disconnect() = mutex.withLock {
        val owner = supabase.auth.currentUserOrNull()?.id ?: return@withLock
        call(owner, buildJsonObject { put("action", "disconnect") })
        supabase.auth.currentSessionOrNull()?.providerToken?.let {
            preferences.putString("youtube_provider_handled:$owner", it.hashCode().toString())
        }
        _connection.value = YouTubeConnectionState(owner, YouTubeConnectionStatus.DISCONNECTED)
    }
}
