package com.tan.gratify.di

import io.github.jan.supabase.auth.AuthConfig

/** Mobile launches the browser and returns; desktop waits for its callback server. */
expect val externalAuthWaitsForCallback: Boolean
expect fun AuthConfig.configureExternalAuthPlatform()
