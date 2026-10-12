package com.tan.gratify.di

import androidx.appcompat.app.AppCompatActivity
import org.koin.dsl.module

/** MainActivity publishes its host; callers must resolve locally, never retain it. */
object AndroidActivityProvider {
    private val activity = WeakActivityReference<AppCompatActivity> { !it.isFinishing && !it.isDestroyed }

    fun attach(owner: AppCompatActivity) = activity.attach(owner)

    fun detach(owner: AppCompatActivity) = activity.detach(owner)

    fun requireActivity(): AppCompatActivity = checkNotNull(activity.currentOrNull()) {
        "No live MainActivity is available for this UI action"
    }
}

// One static definition: neither this factory nor Koin's instance registry owns a host.
// OpenUrl.android.kt resolves AppCompatActivity at each URL/profile-share invocation.
val androidActivityModule = module {
    factory<AppCompatActivity> { AndroidActivityProvider.requireActivity() }
}
