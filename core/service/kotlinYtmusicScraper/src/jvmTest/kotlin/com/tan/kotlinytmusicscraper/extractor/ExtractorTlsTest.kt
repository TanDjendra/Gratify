package com.tan.kotlinytmusicscraper.extractor

import com.sun.net.httpserver.HttpsServer
import com.sun.net.httpserver.HttpsConfigurator
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.Files
import java.security.KeyStore
import javax.net.ssl.*
import kotlin.test.*

class ExtractorTlsTest {
    @Test fun initializationAndProxyChangePreserveTlsRejection() {
        val originalFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val originalVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val extractor = Extractor()
        extractor.init(); extractor.setProxy(null)
        NewPipeUtils(NewPipeDownloaderImpl(null))
        assertSame(originalFactory, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(originalVerifier, HttpsURLConnection.getDefaultHostnameVerifier())

        val directory = Files.createTempDirectory("gratify-tls-test")
        val keystore = directory.resolve("test.p12")
        val password = "synthetic-test-only"
        val executable = java.io.File(System.getProperty("java.home"), "bin/keytool${if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""}")
        val process = ProcessBuilder(executable.path, "-genkeypair", "-alias", "test", "-keyalg", "RSA",
            "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", password,
            "-keypass", password, "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1")
            .redirectErrorStream(true).start()
        process.inputStream.readBytes()
        check(process.waitFor() == 0)
        val store = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(keystore).use { load(it, password.toCharArray()) } }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
        val serverContext = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val testClient = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.httpsConfigurator = HttpsConfigurator(serverContext)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("ok".toByteArray()) }
        }
        server.start()
        fun connect(host: String, trusted: Boolean): String {
            val connection = URL("https://$host:${server.address.port}/").openConnection() as HttpsURLConnection
            connection.connectTimeout = 3000; connection.readTimeout = 3000
            if (trusted) connection.sslSocketFactory = testClient.socketFactory
            return try { connection.inputStream.use { it.readBytes().decodeToString() } } finally { connection.disconnect() }
        }
        try {
            assertFailsWith<SSLHandshakeException> { connect("localhost", false) }
            assertFailsWith<SSLHandshakeException> { connect("127.0.0.1", true) }
            assertEquals("ok", connect("localhost", true))
        } finally { server.stop(0); directory.toFile().deleteRecursively() }
    }
}
