package com.tan.gratify.crashlytics

import android.content.Context
import com.tan.domain.data.player.PlayerError
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

fun reportCrash(throwable: Throwable) { Sentry.captureException(throwable) }
fun configCrashlytics(applicationContext: Context, dsn: String) {
    if (dsn.isBlank()) return
    SentryAndroid.init(applicationContext) { options ->
        options.dsn = dsn
        options.isSendDefaultPii = false
        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
        options.maxBreadcrumbs = 0
        options.tracesSampleRate = 0.0
        options.setBeforeSend { event, _ ->
            event.user = null
            event.request = null
            event.message = null
            event.exceptions?.forEach { it.value = "Exception details redacted" }
            event.extras?.clear()
            event.breadcrumbs?.clear()
            event
        }
    }
}
fun pushPlayerError(error: PlayerError) {
    Sentry.captureMessage("Player error ${error.errorCode}: ${error.errorCodeName}")
}
