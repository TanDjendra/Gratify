package com.tan.gratify.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionManagerTest {
    @Test
    fun newerReleaseIsRecognized() {
        assertTrue(VersionManager.isVersionLower("2.1.0", "v2.1.1"))
        assertTrue(VersionManager.isVersionLower("2.1.0-dev", "2.1.1"))
    }

    @Test
    fun equalAndOlderReleasesAreIgnored() {
        assertFalse(VersionManager.isVersionLower("2.1.0", "v2.1.0"))
        assertFalse(VersionManager.isVersionLower("2.10.0", "2.9.9"))
    }

    @Test
    fun malformedVersionsCannotForceAnUpdate() {
        assertFalse(VersionManager.isVersionLower("2.1.0", "v2.bad.1"))
        assertFalse(VersionManager.isVersionLower("unknown", "3.0.0"))
    }
}
