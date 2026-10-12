package com.tan.domain.utils

import com.eygraber.uri.Uri

/** Only our exact domain/path or unique fallback scheme can be an invitation. */
fun parseWorkspaceInvitation(value: String): String? = try {
    val uri = Uri.parse(value.trim())
    val accepted = uri.userInfo == null && (uri.port == -1 || uri.port == 443) && ((uri.scheme == "https" && uri.host == "gratify.web.id" && uri.path in listOf("/app/invite","/app/invite/")) ||
        (uri.scheme == "gratify-next" && uri.host == "invite" && uri.pathSegments.isEmpty())
    )
    if (accepted) uri.getQueryParameters("token").singleOrNull()?.takeIf { Regex("[a-f0-9]{64}").matches(it) } else null
} catch (_: IllegalArgumentException) { null }
