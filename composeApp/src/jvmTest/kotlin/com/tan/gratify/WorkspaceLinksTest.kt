package com.tan.gratify

import com.tan.domain.utils.parseWorkspaceInvitation
import kotlin.test.*

class WorkspaceLinksTest {
    private val token = "a".repeat(64)
    @Test fun exactDomainAndFallbackResolveSameInvitation() {
        listOf("https://gratify.web.id/app/invite?token=$token", "https://gratify.web.id/app/invite/?token=$token", "gratify-next://invite?token=$token")
            .forEach { assertEquals(token, parseWorkspaceInvitation(it)) }
    }
    @Test fun unrelatedHostPathAndMalformedTokenCannotBecomeInvitation() {
        listOf("https://gratify.web.id.evil.invalid/app/invite?token=$token",
            "http://gratify.web.id/app/invite?token=$token", "https://gratify.web.id/app/watch?token=$token",
            "https://gratify.web.id/app/invite/extra?token=$token", "gratify://invite?token=$token",
            "https://gratify.web.id/app/invite?token=short", "https://gratify.web.id/app/invite",
            "https://user@gratify.web.id/app/invite?token=$token", "https://gratify.web.id:444/app/invite?token=$token",
            "https://gratify.web.id/app/invite?token=$token&token=$token",
            "https://gratify.web.id/app/invite?token=${"A".repeat(64)}")
            .forEach { assertNull(parseWorkspaceInvitation(it), it) }
    }
}
