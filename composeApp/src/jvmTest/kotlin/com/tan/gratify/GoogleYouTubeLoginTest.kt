package com.tan.gratify

import com.tan.domain.repository.YOUTUBE_READ_SCOPE
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.viewModel.auth.configureGoogleLogin
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.createSupabaseClient
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class GoogleYouTubeLoginTest {
    private fun oauthUrl(connectYouTube: Boolean) = runBlocking {
        val client = createSupabaseClient("https://synthetic.example.invalid", "synthetic-key") {
            install(Auth) { autoLoadFromStorage = false; autoSaveToStorage = false }
        }
        try {
            client.auth.awaitInitialization()
            Url(client.auth.getOAuthUrl(Google, redirectUrl = "com.tan.gratify://login-callback") {
                configureGoogleLogin(connectYouTube)
            })
        } finally { client.close() }
    }

    @Test fun selectedCollectionAccessIsIncludedInTheSameSdkLoginUrl() {
        val url = oauthUrl(true)
        assertEquals("/auth/v1/authorize", url.encodedPath)
        assertEquals("google", url.parameters["provider"])
        assertTrue(url.parameters["scopes"].orEmpty().split(" ").contains(YOUTUBE_READ_SCOPE))
        assertEquals("offline", url.parameters["access_type"])
        assertEquals("consent", url.parameters["prompt"])
        assertEquals("true", url.parameters["include_granted_scopes"])
        assertEquals("com.tan.gratify://login-callback", url.parameters["redirect_to"])
        assertFalse(url.toString().contains("youtube.force-ssl"))
    }

    @Test fun basicLoginDoesNotRequestYouTubeOrForceAnOfflineConsent() {
        val url = oauthUrl(false)
        assertFalse(url.parameters["scopes"].orEmpty().contains(YOUTUBE_READ_SCOPE))
        assertNull(url.parameters["access_type"])
        assertNull(url.parameters["prompt"])
    }

    @Test fun profileSetupRetainsTheGoogleChoiceWhenNavigationStateIsRestored() {
        val googleRoute = CreateProfileDestination(skipYouTubeConnection = true)
        assertTrue(Json.decodeFromString<CreateProfileDestination>(Json.encodeToString(googleRoute)).skipYouTubeConnection)
        assertFalse(Json.decodeFromString<CreateProfileDestination>("{}").skipYouTubeConnection)
    }
}
