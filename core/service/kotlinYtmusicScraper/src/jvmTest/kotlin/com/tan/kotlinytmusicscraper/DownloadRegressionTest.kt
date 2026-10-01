package com.tan.kotlinytmusicscraper

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlin.test.*

class DownloadRegressionTest {
    @Test fun aRangeResponseWithAMissingChunkCannotBeReportedAsComplete(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val directory = Files.createTempDirectory("gratify-partial-download-test")
        val size = 3L*1024*1024
        server.createContext("/audio") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            if (exchange.requestMethod == "HEAD") {
                exchange.responseHeaders.add("Content-Length", if (range == null) size.toString() else "1")
                if (range != null) exchange.responseHeaders.add("Content-Range","bytes 0-0/$size")
                exchange.sendResponseHeaders(if (range==null) 200 else 206,-1)
            } else {
                exchange.responseHeaders.add("Content-Range", "bytes ${requireNotNull(range).substringAfter("bytes=")}/$size")
                exchange.sendResponseHeaders(206,1)
                exchange.responseBody.use { it.write(byteArrayOf(1)) }
            }
            exchange.close()
        }
        server.start()
        try {
            val progress = mutableListOf<Triple<Boolean,Float,Int>>(); var failed = false
            try { withTimeout(15000) { Ytmusic().download("http://127.0.0.1:${server.address.port}/audio",directory.resolve("partial").toString(),0).collect { progress += it } } }
            catch (e: Exception) { if (e is TimeoutCancellationException) throw e; failed=true }
            assertTrue(failed); assertFalse(progress.any { it.first })
            assertFalse(Files.exists(directory.resolve("partial")))
        } finally { server.stop(0); directory.toFile().deleteRecursively() }
    }
    @Test fun exhaustedRetriesNeverEmitSuccessAndALaterSuccessfulAttemptClearsTheError(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val directory = Files.createTempDirectory("gratify-download-test")
        val requests = AtomicInteger(0); val recover = AtomicBoolean(false)
        val ytmusic = Ytmusic()
        server.createContext("/audio") { exchange ->
            if (exchange.requestMethod == "HEAD") {
                exchange.responseHeaders.add("Content-Length","5"); exchange.sendResponseHeaders(200,-1)
            } else {
                val number = requests.incrementAndGet()
                if (!recover.get() || number==1) exchange.sendResponseHeaders(503,-1)
                else {
                    exchange.sendResponseHeaders(200,5); exchange.responseBody.use { it.write("audio".toByteArray()) }
                }
            }
            exchange.close()
        }
        server.start()
        val url = "http://127.0.0.1:${server.address.port}/audio"
        try {
            val progress = mutableListOf<Triple<Boolean,Float,Int>>()
            var failed = false
            try { withTimeout(15000) { ytmusic.download(url,directory.resolve("failed").toString(),1).collect { progress += it } } }
            catch (e: Exception) { if (e is TimeoutCancellationException) throw e; failed=true }
            assertTrue(failed); assertEquals(2,requests.get()); assertFalse(progress.any { it.first })
            requests.set(0); recover.set(true)
            val successful = withTimeout(15000) { ytmusic.download(url,directory.resolve("success").toString(),1).toList() }
            assertEquals(2,requests.get()); assertTrue(successful.last().first); assertEquals(1f,successful.last().second)
            assertEquals("audio",Files.readString(directory.resolve("success")))
        } finally { server.stop(0); directory.toFile().deleteRecursively() }
    }
}
