package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.LyricsProvider
import com.tan.domain.utils.hasVerifiedWordBoundaries
import com.tan.domain.utils.reconcileWordBoundaries
import com.tan.domain.utils.hasDisplayableLyrics
import com.tan.domain.utils.needsWordBoundaryReference
import com.tan.domain.utils.assessTextQuality
import com.tan.domain.utils.hasConfirmedLyricText
import com.tan.domain.utils.toSyncedLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ResolvedLyrics(val lyrics: Lyrics, val provider: LyricsProvider)

internal fun Lyrics.hasReadableLines() = !error && lines.orEmpty().any {
    com.tan.domain.utils.lyricDisplayText(it.words).any(Char::isLetterOrDigit)
}

internal fun lyricsProviderOrder(preferred: String, spotifyAvailable: Boolean): List<LyricsProvider> {
    val first = when (preferred) {
        DataStoreManager.LRCLIB -> LyricsProvider.LRCLIB
        DataStoreManager.YOUTUBE -> LyricsProvider.YOUTUBE
        DataStoreManager.BETTER_LYRICS -> LyricsProvider.BETTER_LYRICS
        else -> LyricsProvider.SIMPMUSIC
    }
    // Upstream: Better/YouTube failure -> SimpMusic -> optional Spotify -> LRCLIB.
    // Keep Gratify's remaining services as a last resort, without recursive jobs.
    return (listOf(first, LyricsProvider.SIMPMUSIC) +
        (if (spotifyAvailable) listOf(LyricsProvider.SPOTIFY) else emptyList()) +
        listOf(LyricsProvider.LRCLIB, LyricsProvider.BETTER_LYRICS, LyricsProvider.GRATIFYMUSIC,
            LyricsProvider.YOUTUBE)).distinct()
}

/** Publish safe text only. Timing format and text quality are independent. */
internal suspend fun resolveLyricsFallback(
    providers: List<LyricsProvider>,
    preferWordTiming: Boolean,
    timeoutMs: Long = 8000,
    totalTimeoutMs: Long = 20000,
    referenceLyrics: Lyrics? = null,
    onCandidate: suspend (ResolvedLyrics) -> Unit = {},
    fetch: suspend (LyricsProvider) -> Lyrics?,
): ResolvedLyrics? = withContext(Dispatchers.Default) {
    var best: ResolvedLyrics? = null
    // Only confirmed cached boundaries can influence a fresh provider. Legacy
    // cache text is not evidence, even when its timing-format label looks valid.
    val reference = referenceLyrics?.toSyncedLyrics()?.let { lyrics ->
        lyrics.copy(lines = lyrics.lines?.filter { it.hasConfirmedLyricText() })
    }
    // Keep any safe candidate when the overall search budget expires.
    withTimeoutOrNull(totalTimeoutMs) {
        for (provider in providers) {
            val previous = best
            val previousSafe = previous?.lyrics?.hasDisplayableLyrics() == true
            if (previousSafe && previous?.lyrics?.syncType != "RICH_SYNCED" &&
                (!preferWordTiming || provider != LyricsProvider.BETTER_LYRICS)) continue
            val candidate = try {
                withTimeoutOrNull(timeoutMs) { fetch(provider) }?.assessTextQuality()?.reconcileWordBoundaries(reference)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (candidate?.hasReadableLines() != true) continue
            val candidateSafe = candidate.hasDisplayableLyrics()
            if (previous != null && !previousSafe) {
                val repaired = previous.lyrics.reconcileWordBoundaries(candidate)
                val resolved = previous.copy(lyrics = repaired)
                best = resolved
                val repairedSafe = repaired.hasDisplayableLyrics()
                if (repaired != previous.lyrics && repairedSafe) onCandidate(resolved)
                if (repairedSafe) break
                if (candidateSafe) {
                    // A clean full transcript replaces an incomplete repair.
                    val replacement = ResolvedLyrics(candidate, provider)
                    best = replacement
                    onCandidate(replacement)
                    if (!preferWordTiming || candidate.hasVerifiedWordBoundaries()) break
                }
                continue
            }
            if (previous == null || preferWordTiming && candidateSafe &&
                candidate.syncType == "RICH_SYNCED" && previous.lyrics.syncType != "RICH_SYNCED") {
                val selected = ResolvedLyrics(candidate.reconcileWordBoundaries(previous?.lyrics), provider)
                best = selected
                if (selected.lyrics.hasDisplayableLyrics()) onCandidate(selected)
            }
            val selected = best ?: continue
            if (selected.lyrics.hasVerifiedWordBoundaries() || !preferWordTiming && selected.lyrics.hasDisplayableLyrics()) break
        }
    }
    best?.takeIf { it.lyrics.hasDisplayableLyrics() }
}

/** A legacy rich cache may contain spaced syllables; a fresh readable line can replace it. */
internal fun keepsFormattedRichCache(cached: Lyrics?, incoming: Lyrics?, preferWordTiming: Boolean = true): Boolean {
    return preferWordTiming && cached?.hasDisplayableLyrics() == true &&
        cached.hasVerifiedWordBoundaries() && incoming?.syncType != "RICH_SYNCED"
}
