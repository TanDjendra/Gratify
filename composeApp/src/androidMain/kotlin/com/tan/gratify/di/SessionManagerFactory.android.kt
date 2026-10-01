package com.tan.gratify.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.scope.Scope

actual fun Scope.createAuthSettings(): Settings {
    val context = get<android.content.Context>()
    val prefs = context.getSharedPreferences("supabase_session", android.content.Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
