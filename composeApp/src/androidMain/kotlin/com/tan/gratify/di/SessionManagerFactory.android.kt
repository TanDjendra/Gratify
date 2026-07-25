package com.tan.gratify.di

import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import org.koin.core.scope.Scope

actual fun Scope.createSessionManager(): SessionManager {
    val context = get<android.content.Context>()
    val prefs = context.getSharedPreferences("supabase_session", android.content.Context.MODE_PRIVATE)
    val settings = SharedPreferencesSettings(prefs)
    return SettingsSessionManager(settings)
}
