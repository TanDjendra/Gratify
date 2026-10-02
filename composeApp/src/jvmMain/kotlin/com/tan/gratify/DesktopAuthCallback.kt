package com.tan.gratify

import com.eygraber.uri.Uri
import com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator
import com.tan.logger.Logger
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Validates the returned user on the server before enabling a password reset. */
internal class DesktopAuthCallback(private val authenticate: suspend (Uri) -> String) {
    constructor(auth: Auth) : this({ uri ->
        auth.awaitInitialization()
        val code = uri.getQueryParameter("code")
        if (code != null) {
            auth.exchangeCodeForSession(code).user?.id ?: error("No verified callback user")
        } else {
            val params = fragmentParameters(uri)
            val token = params["access_token"]?.takeIf(String::isNotBlank) ?: error("Missing access token")
            val refresh = params["refresh_token"]?.takeIf(String::isNotBlank) ?: error("Missing refresh token")
            val expiry = params["expires_in"]?.toLongOrNull()?.takeIf { it > 0 } ?: error("Invalid expiry")
            require(params["token_type"].equals("bearer", ignoreCase = true))
            val user = auth.retrieveUser(token)
            auth.importSession(UserSession(accessToken = token, refreshToken = refresh, expiresIn = expiry,
                tokenType = "bearer", user = user, type = params["type"].orEmpty()), source = SessionSource.External)
            user.id
        }
    })

    companion object {
        fun matches(uri: Uri) = uri.scheme == "com.tan.gratify" && uri.host == "login-callback"
        private fun fragmentParameters(uri: Uri): Map<String, String> =
            Uri.parse("https://callback.invalid/?${uri.fragment.orEmpty()}").let { fragment ->
                listOf("access_token", "refresh_token", "expires_in", "token_type", "type")
                    .mapNotNull { key -> fragment.getQueryParameter(key)?.let { key to it } }.toMap()
            }
        fun isRecovery(uri: Uri) = uri.getQueryParameter("flow") == "recovery" ||
            uri.getQueryParameter("type") == "recovery" || fragmentParameters(uri)["type"] == "recovery"
    }
    private val mutex = Mutex()

    suspend fun handle(uri: Uri): Boolean {
        if (!matches(uri)) return false
        mutex.withLock {
            val recovery = isRecovery(uri)
            if (recovery) PasswordRecoveryCoordinator.request()
            try {
                val userId = authenticate(uri)
                require(userId.isNotBlank())
                if (recovery) PasswordRecoveryCoordinator.verify(userId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                Logger.w("DesktopAuthCallback", "Authentication callback failed")
            }
        }
        return true
    }
}
