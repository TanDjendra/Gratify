package com.tan.gratify.ui

import com.tan.gratify.ui.navigation.parseSocialNotificationTarget
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.utils.notificationSocialLink
import com.tan.domain.utils.notificationWebLink
import kotlin.test.*

class SocialNotificationNavigationTest {
    private val owner="11111111-1111-4111-8111-111111111111"
    private val target="22222222-2222-4222-8222-222222222222"
    @Test fun messagesAndStoriesHaveDifferentTargets() {
        assertEquals(target,parseSocialNotificationTarget("gratify://friends?owner=$owner&peer=$target",owner)?.peer)
        assertEquals(target,parseSocialNotificationTarget("gratify://friends?owner=$owner&story=$target",owner)?.story)
    }
    @Test fun changedAccountsAndAmbiguousTargetsAreRejected() {
        assertNull(parseSocialNotificationTarget("gratify://friends?owner=$target&peer=$target",owner))
        assertNull(parseSocialNotificationTarget("gratify://friends?owner=$owner&peer=$target&story=$target",owner))
        assertNull(parseSocialNotificationTarget("gratify://friends?owner=$owner&story=not-an-id",owner))
        assertNull(parseSocialNotificationTarget("https://friends?owner=$owner&peer=$target",owner))
    }
    @Test fun inboxBuildsOnlyValidOwnerBoundFriendsTargets() {
        val row=NotificationEntity(channelId="social_story",name="Nara",single=listOf(mapOf("ownerId" to owner,"sourceId" to target)))
        assertEquals(target,parseSocialNotificationTarget(notificationSocialLink(row)!!,owner)?.story)
        assertNull(notificationSocialLink(row.copy(single=listOf(mapOf("ownerId" to owner,"sourceId" to "invalid")))))
        assertNull(notificationSocialLink(row.copy(single=listOf(mapOf("sourceId" to target)))))
    }
    @Test fun announcementLinksRejectMalformedAndNonWebSchemes() {
        assertEquals("https://example.invalid/news",notificationWebLink(" https://example.invalid/news "))
        assertEquals("http://example.invalid/news",notificationWebLink("http://example.invalid/news"))
        for(value in listOf("javascript:alert(1)","httpbroken://example.invalid","https:///missing","https://example.invalid/a b","gratify://friends")) assertNull(notificationWebLink(value),value)
    }
}
