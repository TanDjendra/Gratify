package com.tan.gratify.di

import com.russhwolf.settings.Settings
import org.koin.core.scope.Scope

/** Persistent platform storage shared by sessions and OAuth PKCE verification. */
expect fun Scope.createAuthSettings(): Settings
