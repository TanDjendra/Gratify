package com.tan.gratify.viewModel.auth

import com.tan.domain.utils.parseProfileUserId
import kotlinx.datetime.Clock

/** Keeps a guest's profile destination through the root authentication flow. */
object ProfileLoginReturn {
    private var pending: Pair<String, Long>? = null
    fun request(userId: String, now: Long = Clock.System.now().toEpochMilliseconds()) {
        pending = parseProfileUserId(userId)?.let { it to now }
    }
    fun consume(authenticated: Boolean, now: Long = Clock.System.now().toEpochMilliseconds()): String? {
        val target = pending
        pending = null
        return target?.first?.takeIf { authenticated && now - target.second in 0..600_000 }
    }
    fun clear() { pending = null }
}
