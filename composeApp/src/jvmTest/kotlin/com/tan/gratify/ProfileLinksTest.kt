package com.tan.gratify

import com.tan.domain.utils.parseProfileUserId
import com.tan.domain.utils.profileUrl
import kotlin.test.*

class ProfileLinksTest {
    private val id = "67ce1d48-ef0f-42e7-82dc-c5668b330ae4"
    @Test fun sharedLinkAndLegacyQrResolveToTheSameAccount() {
        listOf(profileUrl(id), "gratify://profile/$id", "gratify://app/profile/$id", "${profileUrl(id)}?source=qr#profile")
            .forEach { assertEquals(id, parseProfileUserId(it, false), it) }
        assertEquals(id, parseProfileUserId(id.uppercase()))
        assertNull(parseProfileUserId(id, false))
    }
    @Test fun unrelatedQrCannotBecomeAnAccountRoute() {
        listOf("", "random", "not-a-user", "https://evil.invalid/profile/$id", "https://gratify.org.evil.invalid/app/profile/$id",
            "http://gratify.org/app/profile/$id", "gratify://app/profile/no-id", "https://gratify.org/app/profile/$id/extra",
            "gratify://profile/", "gratify://watch?v=$id").forEach { assertNull(parseProfileUserId(it), it) }
    }
    @Test fun desktopCanonicalizesAllSupportedProfileSchemes() {
        listOf(profileUrl(id), "gratify://profile/$id", "gratify://app/profile/$id")
            .forEach { assertEquals(profileUrl(id), DesktopDeepLinkHandler.parseToIntent(it).data?.toString()) }
    }
    @Test fun profileLinkCannotBeBuiltForAnEmptyOrMalformedAccount() {
        assertFailsWith<IllegalArgumentException> { profileUrl("") }
        assertFailsWith<IllegalArgumentException> { profileUrl("garbage") }
    }
}
