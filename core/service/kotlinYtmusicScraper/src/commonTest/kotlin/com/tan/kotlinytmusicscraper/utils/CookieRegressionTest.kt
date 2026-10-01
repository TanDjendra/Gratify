package com.tan.kotlinytmusicscraper.utils

import kotlin.test.*

class CookieRegressionTest {
    @Test fun markdownLinksKeepReadableLabels() {
        assertEquals("Read the guide and cover", com.tan.kotlinytmusicscraper.extension.stripMarkdown("Read [the guide](https://example.com) and ![cover](cover.jpg)"))
    }
    @Test fun paddingAndInconsistentWhitespaceDoNotLoseAuthenticationCookies() {
        assertEquals(mapOf("SID" to "one==", "HSID" to "two", "EMPTY" to "", "PAD" to "x=y=z"),
            parseCookieString("SID=one==;HSID=two; EMPTY=; bad; =invalid; PAD=x=y=z"))
    }
}
