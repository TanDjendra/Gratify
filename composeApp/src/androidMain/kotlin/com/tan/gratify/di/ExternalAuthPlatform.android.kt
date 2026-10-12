package com.tan.gratify.di

import io.github.jan.supabase.auth.AuthConfig
import android.content.Context
import org.koin.mp.KoinPlatform.getKoin

actual val externalAuthWaitsForCallback: Boolean = false
actual val externalAuthScheme: String
    get() = getKoin().get<Context>().packageName
actual fun AuthConfig.configureExternalAuthPlatform() = Unit
