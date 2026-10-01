package com.tan.gratify.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.PreferencesSettings
import org.koin.core.scope.Scope
import java.util.prefs.Preferences

actual fun Scope.createAuthSettings(): Settings {
    val prefs = Preferences.userRoot().node("com/tan/gratify/supabase_session")
    return PreferencesSettings(prefs)
}
