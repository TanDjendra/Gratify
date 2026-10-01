package com.tan.data.sync

import com.tan.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.first

internal suspend fun DataStoreManager.cloudSettings(): Map<String, String> = mapOf(
    "quality" to quality.first(), "downloadQuality" to downloadQuality.first(),
    "normalizeVolume" to normalizeVolume.first(), "skipSilent" to skipSilent.first(),
    "saveStateOfPlayback" to saveStateOfPlayback.first(), "shuffleKey" to shuffleKey.first(),
    "repeatKey" to repeatKey.first(), "lyricsProvider" to lyricsProvider.first(),
    "enableTranslateLyric" to enableTranslateLyric.first(), "translationLanguage" to translationLanguage.first(),
    "playerVolume" to playerVolume.first().toString(), "playbackSpeed" to playbackSpeed.first().toString(),
    "pitch" to pitch.first().toString(),
)

/** Only supported settings use their typed setters. Unknown keys never become arbitrary preferences. */
internal suspend fun DataStoreManager.restoreCloudSettings(values: Map<String, String?>) {
    values["quality"]?.let { setQuality(it) }
    values["downloadQuality"]?.let { setDownloadQuality(it) }
    values["normalizeVolume"]?.let { setNormalizeVolume(it == DataStoreManager.TRUE) }
    values["skipSilent"]?.let { setSkipSilent(it == DataStoreManager.TRUE) }
    values["saveStateOfPlayback"]?.let { setSaveStateOfPlayback(it == DataStoreManager.TRUE) }
    val repeat = when (values["repeatKey"]) {
        DataStoreManager.REPEAT_ONE -> 1
        DataStoreManager.REPEAT_ALL -> 2
        else -> 0
    }
    if ("shuffleKey" in values || "repeatKey" in values) {
        val preservedRepeat = when (repeatKey.first()) {
            DataStoreManager.REPEAT_ONE -> 1
            DataStoreManager.REPEAT_ALL -> 2
            else -> 0
        }
        recoverShuffleAndRepeatKey(values["shuffleKey"]?.let { it == DataStoreManager.TRUE } ?: (shuffleKey.first() == DataStoreManager.TRUE),
            if ("repeatKey" in values) repeat else preservedRepeat)
    }
    values["lyricsProvider"]?.let { setLyricsProvider(it) }
    values["enableTranslateLyric"]?.let { setEnableTranslateLyric(it == DataStoreManager.TRUE) }
    values["translationLanguage"]?.let { setTranslationLanguage(it) }
    values["playerVolume"]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..1f }?.let { setPlayerVolume(it) }
    values["playbackSpeed"]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.25f..4f }?.let { setPlaybackSpeed(it) }
    values["pitch"]?.toIntOrNull()?.takeIf { it in -24..24 }?.let { setPitch(it) }
}
