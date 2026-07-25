package com.tan.gratify.di

import com.russhwolf.settings.PreferencesSettings
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import org.koin.core.scope.Scope
import java.util.prefs.Preferences

actual fun Scope.createSessionManager(): SessionManager {
    val prefs = Preferences.userRoot().node("com/tan/gratify/supabase_session")
    return SettingsSessionManager(PreferencesSettings(prefs))
}
