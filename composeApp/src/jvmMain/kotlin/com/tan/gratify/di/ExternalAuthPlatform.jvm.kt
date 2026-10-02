package com.tan.gratify.di

import io.github.jan.supabase.auth.AuthConfig
import kotlin.time.Duration.Companion.minutes

actual val externalAuthWaitsForCallback: Boolean = true
actual fun AuthConfig.configureExternalAuthPlatform() {
    httpCallbackConfig {
        timeout = 5.minutes
        htmlTitle = "Gratify — Login Google"
        redirectHtml = """
            <!doctype html><html lang="id"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="no-referrer"><title>Gratify</title></head>
            <body style="background:#121212;color:#fff;font-family:system-ui;text-align:center;padding:15vh 24px">
            <h1 style="color:#1ed760">Login berhasil</h1><p>Kembali ke aplikasi Gratify. Tab ini boleh ditutup.</p>
            <script>history.replaceState({},document.title,location.pathname)</script></body></html>
        """.trimIndent()
    }
}
