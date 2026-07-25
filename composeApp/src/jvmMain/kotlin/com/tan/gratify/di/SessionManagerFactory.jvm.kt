package com.tan.gratify.di

import com.russhwolf.settings.MapSettings
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import org.koin.core.scope.Scope

actual fun Scope.createSessionManager(): SessionManager {
    return SettingsSessionManager(MapSettings())
}
