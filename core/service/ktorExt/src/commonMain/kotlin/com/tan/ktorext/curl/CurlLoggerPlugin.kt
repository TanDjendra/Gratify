package com.tan.ktorext.curl

import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException

/**
 * Config for [CurlLogger].
 */
class CurlLoggerConfig {
    /**
     * Opt-in diagnostic sink. Disabled and silent by default.
     */
    var enabled: Boolean = false
    var logger: (String) -> Unit = {}

    /**
     * Header names (case-insensitive) whose value is replaced with `<redacted>`.
     * Sensitive headers are redacted by default; logging itself is disabled.
     * Set e.g. `setOf("Authorization", "Cookie")` to avoid leaking secrets to logs.
     */
    var redactHeaders: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie", "X-Api-Key", "apikey", "X-Goog-Api-Key", "X-Client-Token")

    /**
     * When true, drops the `Accept-Encoding` request header and appends `--compressed`
     * so the response is auto-decoded by curl instead of printing as compressed bytes.
     */
    var handleCompression: Boolean = true
}

/**
 * Emits a redacted request summary only when explicitly enabled. Query strings and
 * bodies are omitted; sensitive headers are replaced. This is not a request replay.
 *
 * The whole command is emitted as a single line in one [logger] call, so it stays one log entry
 * (no line continuations, no splitting into separate entries).
 *
 * Usage:
 * ```
 * install(CurlLogger) {
 *     logger = { Logger.d(TAG, it) }
 * }
 * ```
 */
val CurlLogger = createClientPlugin("CurlLogger", ::CurlLoggerConfig) {
    val log = pluginConfig.logger
    val redactHeaders = pluginConfig.redactHeaders.mapTo(mutableSetOf()) { it.lowercase() }
    val handleCompression = pluginConfig.handleCompression

    on(SendingRequest) { request, content ->
        if (!pluginConfig.enabled) return@on
        try {
            log(buildCurlCommand(request, content, redactHeaders, handleCompression))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Logging must never break the actual request.
        }
    }
}

/** Builds the full curl command on a single line. */
internal suspend fun buildCurlCommand(
    request: HttpRequestBuilder,
    content: OutgoingContent,
    redactHeaders: Set<String>,
    handleCompression: Boolean,
): String {
    val contentLengthName = HttpHeaders.ContentLength.lowercase()
    val acceptEncodingName = HttpHeaders.AcceptEncoding.lowercase()

    val parts = mutableListOf<String>()
    parts += "curl -X ${request.method.value}"
    parts += "${request.url.protocol.name}://${request.url.host}${request.url.build().encodedPath}".shellQuote()

    val seenHeaders = mutableSetOf<String>()
    request.headers.entries().forEach { (name, values) ->
        val lower = name.lowercase()
        if (lower == contentLengthName) return@forEach // let curl recompute it
        if (handleCompression && lower == acceptEncodingName) return@forEach
        seenHeaders += lower
        values.forEach { value ->
            val shown = if (lower in redactHeaders) "<redacted>" else value
            parts += "-H " + "$name: $shown".shellQuote()
        }
    }
    if ("content-type" !in seenHeaders) {
        content.contentType?.let { parts += "-H " + "${HttpHeaders.ContentType}: $it".shellQuote() }
    }

    if (handleCompression) parts += "--compressed"

    // Request bodies can contain passwords, OTPs, signed URLs or tokens: never print them.

    return parts.joinToString(" ")
}

/** POSIX shell single-quoting: safe even when the value itself contains single quotes. */
private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"
