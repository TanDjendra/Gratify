package com.tan.kotlinytmusicscraper

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import kotlinx.coroutines.Job
import kotlin.test.*

class YtmusicProxyTest {
    private fun client(music: Ytmusic): HttpClient =
        Ytmusic::class.java.getDeclaredField("httpClient").apply { isAccessible = true }.get(music) as HttpClient

    @Test fun repeatedDisabledProxyKeepsActiveClientForStartupRequests() {
        val music = Ytmusic()
        val original = client(music)
        try {
            repeat(5) { music.proxy = null }
            assertSame(original, client(music))
            assertTrue(original.coroutineContext[Job]!!.isActive)
        } finally { client(music).close() }
    }

    @Test fun actualProxyChangeRebuildsButEquivalentSettingsKeepNewClient() {
        val music = Ytmusic()
        try {
            val original = client(music)
            music.proxy = ProxyBuilder.http("http://127.0.0.1:8899")
            val configured = client(music)
            assertNotSame(original, configured)
            music.proxy = ProxyBuilder.http("http://127.0.0.1:8899")
            assertSame(configured, client(music))
            assertTrue(configured.coroutineContext[Job]!!.isActive)
            music.proxy = null
            assertNotSame(configured, client(music))
        } finally { client(music).close() }
    }
}
