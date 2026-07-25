package com.tan.kotlinytmusicscraper.utils

import com.tan.kotlinytmusicscraper.models.response.AudioData
import com.tan.logger.Logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.io.encoding.Base64

private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.toHex(): String {
    val result = StringBuilder(size * 2)
    for (b in this) {
        val i = b.toInt()
        result.append(HEX_CHARS[(i shr 4) and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }
    return result.toString()
}

fun sha1(str: String): String = Buffer().writeUtf8(str).sha1().hex()


fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split("; ")
        .filter { it.isNotEmpty() }
        .associate {
            val (key, value) = it.split("=")
            key to value
        }

fun String.parseTime(): Int? {
    try {
        val parts =
            if (this.contains(":")) split(":").map { it.toInt() } else split(".").map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun generateNetscapeCookies(
    cookies: Map<String, String>,
    domain: String = ".example.com",
    path: String = "/",
    secure: Boolean = false,
    httpOnly: Boolean = false,
    expirationTimeSeconds: Long = Clock.System.now().epochSeconds + 86400 * 365,
): String {
    val header =
        "# Netscape HTTP Cookie File\n" +
            "# This is a generated file! Do not edit.\n\n"

    val cookieLines =
        cookies
            .map { (name, value) ->
                // Netscape format: domain, domainFlag, path, secure, expiration, name, value
                buildString {
                    append(domain)
                    append("\t")
                    append("TRUE") // domain flag - TRUE means domain includes subdomains
                    append("\t")
                    append(path)
                    append("\t")
                    append(if (secure) "TRUE" else "FALSE")
                    append("\t")
                    append(expirationTimeSeconds)
                    append("\t")
                    append(name)
                    append("\t")
                    append(value)
                }
            }.joinToString("\n")

    return header + cookieLines
}

fun String.decodeTidalManifest(): AudioData? {
    val decodedBytes = Base64.decode(this)
    val jsonString = decodedBytes.decodeToString()
    val json =
        Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    return try {
        json.decodeFromString<AudioData?>(jsonString)
    } catch (e: Exception) {
        Logger.e("Utils", "Failed to decode Tidal manifest: ${e.message}")
        e.printStackTrace()
        null
    }
}

fun String.decodeBase64(): String {
    val decodedBytes = Base64.decode(this)
    return decodedBytes.decodeToString()
}