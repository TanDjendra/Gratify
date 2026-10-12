package com.tan.gratify.viewModel.auth

import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

internal fun authFailureMessage(error: Exception, text: (StringResource) -> String): String {
    val message = error.message.orEmpty()
    return text(when {
        listOf("rate limit", "too many requests", "over_email_send_rate_limit", "over_request_rate_limit").any { message.contains(it, true) } -> Res.string.auth_rate_limited
        listOf("network", "timeout", "connection", "unable to resolve", "failed to connect").any { message.contains(it, true) } -> Res.string.auth_network_error
        listOf("invalid_credentials", "Invalid login credentials", "user not found").any { message.contains(it, true) } -> Res.string.auth_credentials_error
        listOf("already registered", "already exists", "duplicate").any { message.contains(it, true) } -> Res.string.auth_account_exists
        message.contains("weak_password", true) -> Res.string.auth_password_hint
        else -> Res.string.auth_request_failed
    })
}
