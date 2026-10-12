package com.tan.gratify

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Read-only transport probe: invalid test token must reach HTTP, never sign in/out. Debug only. */
class SupabaseNetworkAuditActivity : Activity(), KoinComponent {
    private val supabase by inject<SupabaseClient>()
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "Checking secure account connection…" }
        setContentView(status)
        auditScope.launch {
            val defaultEngine = HttpClient().let { client ->
                try { client.engine.javaClass.simpleName } finally { client.close() }
            }
            val existingSession = supabase.auth.currentSessionOrNull()
            val result = try {
                withTimeout(25000) { supabase.auth.retrieveUser("gratify-network-audit-invalid-token") }
                "FAIL: invalid test token unexpectedly accepted"
            } catch (http: RestException) {
                if (http.statusCode in 400..403) "PASS: Supabase returned HTTP ${http.statusCode}; TLS completed"
                else "FAIL: unexpected HTTP ${http.statusCode}"
            } catch (network: HttpRequestException) {
                "FAIL: ${network.message}"
            } catch (error: Exception) {
                "FAIL: ${error.javaClass.simpleName}: ${error.message}"
            }
            val client = HttpClient()
            val health = try {
                val response = withTimeout(15000) {
                    client.get("${BuildKonfig.SUPABASE_URL.trimEnd('/')}/auth/v1/health") {
                        header("apikey", BuildKonfig.SUPABASE_KEY)
                    }
                }
                if (response.status.value == 200) "PASS: Default client Supabase health HTTP 200"
                else "FAIL: health HTTP ${response.status.value}"
            } catch (error: Exception) { "FAIL: health ${error.javaClass.simpleName}" }
            val certificateCheck = try {
                withTimeout(15000) { client.get("https://self-signed.badssl.com/") }
                "FAIL: untrusted certificate accepted"
            } catch (error: Exception) {
                val isCertificateFailure = generateSequence(error as Throwable?) { it.cause }.any {
                    it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException
                }
                if (isCertificateFailure) "PASS: Untrusted TLS certificate rejected"
                else "FAIL: certificate probe ${error.javaClass.simpleName}"
            } finally { client.close() }
            val retainedSession = existingSession == supabase.auth.currentSessionOrNull()
            status.text = "$result\n$health\n$certificateCheck\nDefault engine=$defaultEngine\nSession retained=$retainedSession"
            Log.i("SupabaseNetworkAudit", status.text.toString())
        }
    }

    override fun onDestroy() {
        auditScope.cancel()
        super.onDestroy()
    }
}
