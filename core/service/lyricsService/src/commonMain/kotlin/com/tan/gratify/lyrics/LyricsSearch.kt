package com.tan.gratify.lyrics

import com.tan.gratify.lyrics.models.response.LrclibObject
import kotlin.math.abs
import com.tan.domain.utils.hasPotentiallyFragmentedText
import com.tan.domain.utils.lyricDisplayText

/** Remove video packaging, preserving recording qualifiers such as Live/Remix/Acoustic. */
fun lyricsSearchTitle(title: String): String = title
    .replace(Regex("(?i)[(\\[]\\s*(?:official\\s+)?(?:music\\s+video|video|audio|lyric(?:s|\\s+video)|visuali[sz]er)\\s*[)\\]]"), " ")
    .replace(Regex("\\s+"), " ").trim()

internal fun selectLrclibResult(
    results: List<LrclibObject>, duration: Int?, track: String? = null, artist: String? = null,
): LrclibObject? {
    val knownDuration = duration?.takeIf { it > 0 }
    return results.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
        .filter { knownDuration == null || abs(it.duration - knownDuration) <= 10 }
        .filter { track.isNullOrBlank() || searchSignature(it.trackName.orEmpty(), artist) == searchSignature(track, artist) }
        .filter { artist.isNullOrBlank() || artistSignature(it.artistName.orEmpty()) == artistSignature(artist) }
        .sortedWith(compareBy<LrclibObject> { hasPotentiallyFragmentedTranscript(it.syncedLyrics ?: it.plainLyrics.orEmpty()) }
            .thenBy { it.syncedLyrics.isNullOrBlank() }
            .thenBy { if (knownDuration != null) abs(it.duration - knownDuration) else 0f })
        .firstOrNull()
}

private fun artistSignature(artist: String): String = artist
    .replace(Regex("(?i)\\s*-\\s*Topic$"), "").filter(Char::isLetterOrDigit).lowercase()

private fun searchSignature(title: String, artist: String?): String {
    val cleaned = lyricsSearchTitle(title).trim()
    val unprefixed = if (!artist.isNullOrBlank() && cleaned.startsWith("$artist - ", ignoreCase = true))
        cleaned.drop(artist.length + 3) else cleaned
    return unprefixed.filter(Char::isLetterOrDigit).lowercase()
}

private fun hasPotentiallyFragmentedTranscript(text: String): Boolean = text.lines().any {
    hasPotentiallyFragmentedText(lyricDisplayText(it.replace(Regex("""\[\d{1,6}:\d{2}(?:\.\d{1,3})?\]"""), "")))
}
