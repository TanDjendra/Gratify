package com.tan.gratify.di

import com.tan.data.sync.UserDataSyncManager
import com.tan.gratify.BuildKonfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val supabaseModule = module {
    single<SupabaseClient> {
        val sm = createSessionManager()
        createSupabaseClient(
            supabaseUrl = BuildKonfig.SUPABASE_URL,
            supabaseKey = BuildKonfig.SUPABASE_KEY,
        ) {
            install(Auth) {
                sessionManager = sm
                // Deeplink redirect untuk OAuth (mis. Login Google).
                // WAJIB cocok dengan intent-filter di androidApp/src/main/AndroidManifest.xml:
                //   <data android:scheme="com.tan.gratify" android:host="login-callback" />
                // Tanpa ini, scheme/host = null → signInWith(Google) tidak punya redirect
                // dan handleDeeplinks menolak callback ("wrong scheme or host"),
                // sehingga login stuck setelah memilih akun.
                scheme = "com.tan.gratify"
                host = "login-callback"
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
        )
    }
}
