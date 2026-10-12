package com.tan.domain.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val DataStoreManager.lyricsAnimationsEnabled: Flow<Boolean>
    // Line highlighting is the only supported mode, including restored legacy preferences.
    get() = getString("lyrics_motion").map { false }

@Suppress("UNUSED_PARAMETER")
suspend fun DataStoreManager.setLyricsAnimationsEnabled(enabled: Boolean) {
    putString("lyrics_motion", "no_animation")
}
