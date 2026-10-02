package com.tan.gratify.di

import io.github.jan.supabase.auth.AuthConfig

actual val externalAuthWaitsForCallback: Boolean = false
actual fun AuthConfig.configureExternalAuthPlatform() = Unit
