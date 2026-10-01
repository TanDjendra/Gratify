package com.tan.ktorext.curl

import io.ktor.client.request.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CurlSecurityTest {
    @Test fun secretsNeverAppearEvenWhenDiagnosticsAreEnabled(): Unit = runBlocking {
        val config = CurlLoggerConfig(); assertFalse(config.enabled)
        val request = HttpRequestBuilder().apply {
            url("https://example.com/auth?token=query-secret#fragment-secret")
            headers.append("authorization","Bearer header-secret")
            headers.append("Cookie","SID=cookie-secret")
            headers.append("X-Api-Key","key-secret")
        }
        val content = object : OutgoingContent.ByteArrayContent() {
            override fun bytes(): ByteArray = error("Logging must not read a request body")
        }
        val logged = buildCurlCommand(request,content,config.redactHeaders.map { it.lowercase() }.toSet(),true)
        listOf("query-secret","fragment-secret","header-secret","cookie-secret","key-secret").forEach { assertFalse(logged.contains(it)) }
        assertTrue(logged.contains("<redacted>")); assertTrue(logged.contains("https://example.com/auth"))
    }
}
