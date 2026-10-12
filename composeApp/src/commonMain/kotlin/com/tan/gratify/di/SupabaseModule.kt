package com.tan.gratify.di

import com.tan.data.sync.UserDataSyncManager
import com.tan.gratify.BuildKonfig
import com.tan.ktorext.getEngine
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val supabaseModule = module {
    single {
        com.tan.data.sync.AccountSessionCoordinator(get(), get(), get(),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            recoveryPending = { com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator.pending.value })
    }
    single<com.tan.domain.repository.YouTubeLibraryRepository>(createdAtStart = true) {
        com.tan.data.repository.OfficialYouTubeLibraryRepository(get(), get(),
            BuildKonfig.SUPABASE_URL, BuildKonfig.SUPABASE_KEY,
            CoroutineScope(SupervisorJob() + Dispatchers.Default), returnScheme = externalAuthScheme)
    }
    single<SupabaseClient> {
        val authSettings = createAuthSettings()
        createSupabaseClient(
            supabaseUrl = BuildKonfig.SUPABASE_URL,
            supabaseKey = BuildKonfig.SUPABASE_KEY,
        ) {
            // Choose the platform engine explicitly. CIO's hostname-unaware TLS
            // check fails on Android when Network Security Config has domain rules.
            httpEngine = getEngine().create()
            install(Auth) {
                sessionManager = SettingsSessionManager(authSettings)
                codeVerifierCache = SettingsCodeVerifierCache(authSettings)
                // Match the installed Android package so coexisting apps do not
                // consume each other's OAuth/recovery callbacks.
                scheme = externalAuthScheme
                host = "login-callback"
                configureExternalAuthPlatform()
            }
            install(Postgrest)
            install(Storage)
        }
    }

    single {
        UserDataSyncManager(
            userDataSyncRepository = get(),
            socialRepository = get(),
            supabase = get(),
            dataStoreManager = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            mediaPlayerHandler = get(),
            userRepository = get(),
        )
    }
}
