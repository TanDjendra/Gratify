package com.tan.gratify.di

import io.github.jan.supabase.auth.SessionManager
import org.koin.core.scope.Scope

expect fun Scope.createSessionManager(): SessionManager
