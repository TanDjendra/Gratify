package com.tan.gratify.ui

import com.tan.gratify.ui.screen.home.*
import kotlin.test.*

class SettingsValidationTest {
    @Test fun apiAddressRequiresARealHttpAddress() {
        listOf("", "https://api.example.com/v1/", "http://localhost:8080/v1").forEach { assertTrue(validSettingsApiUrl(it), it) }
        listOf("httpevil", "javascript:alert(1)", "https://", "https://example.com/a b", "file:///private").forEach { assertFalse(validSettingsApiUrl(it), it) }
    }
    @Test fun headersMustBeJsonTextWithoutHeaderLineBreaks() {
        listOf("", "{}", """{"X-Client":"Gratify","Authorization":"synthetic"}""").forEach { assertTrue(validSettingsHeaders(it), it) }
        listOf("{garbage}", "[]", """{"key":true}""", """{"key":null}""", """{"key":"a\r\nb"}""").forEach { assertFalse(validSettingsHeaders(it), it) }
    }
    @Test fun everySearchEntryPointsAtAnExistingCategorySection() {
        assertEquals(10, SettingCategory.entries.size)
        settingsSearchEntries.forEach { assertTrue(it.section in it.category.sections()) }
    }
    @Test fun commonFeatureNamesRemainSearchableWhenTranslated() {
        val crossfade = settingsSearchEntries.single { it.section == "crossfade_settings" }
        assertTrue(matchesSettingsSearch("  CROSSFADE  ", "Memudar (BETA)", "Pemutaran", crossfade.keywords))
        assertTrue(matchesSettingsSearch("memudar", "Memudar (BETA)", "Pemutaran", crossfade.keywords))
        assertTrue(matchesSettingsSearch("pemutaran", "Memudar (BETA)", "Pemutaran", crossfade.keywords))
        assertFalse(matchesSettingsSearch("unknown feature", "Memudar (BETA)", "Pemutaran", crossfade.keywords))
        val backup = settingsSearchEntries.first { it.keywords == "backup cadangan" }
        assertTrue(matchesSettingsSearch("cadangan", "Backup", "Penyimpanan", backup.keywords))
    }
}
