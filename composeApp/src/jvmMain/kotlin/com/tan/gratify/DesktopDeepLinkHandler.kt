package com.tan.gratify

import com.eygraber.uri.Uri
import com.tan.domain.data.model.intent.GenericIntent
import com.tan.logger.Logger
import java.nio.file.Path

/** Routes content and authentication callbacks without logging their credentials. */
object DesktopDeepLinkHandler {
    private const val TAG = "DesktopDeepLinkHandler"
    private val inbox by lazy {
        DesktopLinkInbox(Path.of(System.getProperty("user.home"), ".gratify", "deep-links"))
    }
    private val lock = Any()
    private var cached: GenericIntent? = null

    var listener: ((GenericIntent) -> Unit)? = null
        set(value) {
            val pending = synchronized(lock) {
                field = value
                if (value != null) cached.also { cached = null } else null
            }
            pending?.let { value?.invoke(it) }
        }

    fun acceptsArgument(value: String): Boolean =
        listOf("gratify://", "com.tan.gratify://", "http://", "https://").any {
            value.startsWith(it, ignoreCase = true)
        }

    fun onNewUri(uri: String) {
        val intent = try { parseToIntent(uri) } catch (_: Exception) {
            Logger.w(TAG, "Invalid desktop link")
            return
        }
        val consumer = synchronized(lock) {
            listener.also { if (it == null) cached = intent }
        }
        consumer?.invoke(intent)
    }

    fun writePendingUri(uri: String) {
        try { inbox.write(uri) } catch (_: Exception) {
            Logger.e(TAG, "Failed to forward desktop link")
        }
    }

    fun consumePendingUri(): Boolean = try {
        inbox.consume()?.let { onNewUri(it); true } ?: false
    } catch (_: Exception) {
        Logger.e(TAG, "Failed to receive desktop link")
        false
    }

    internal fun parseToIntent(value: String): GenericIntent {
        require(value.length <= DesktopLinkInbox.MAX_URI_LENGTH)
        val parsed = Uri.parse(value)
        val actualUri = when {
            parsed.scheme == "gratify" && parsed.host == "open-app" ->
                parsed.getQueryParameter("url")?.let(Uri::parse)
            DesktopAuthCallback.matches(parsed) -> parsed
            parsed.scheme == "gratify" && parsed.host != null -> {
                val query = parsed.query?.let { "?$it" }.orEmpty()
                val suffix = parsed.pathSegments.joinToString("/").let { if (it.isEmpty()) "" else "/$it" }
                Uri.parse("https://gratify.org/app/${parsed.host}$suffix$query")
            }
            else -> parsed
        }
        return GenericIntent(action = "android.intent.action.VIEW", data = actualUri)
    }
}
