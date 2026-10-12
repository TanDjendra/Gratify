package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.metadata.Lyrics
import com.tan.gratify.viewModel.LyricsProvider
import com.tan.domain.utils.reconcileWordBoundaries
import com.tan.domain.utils.hasDisplayableLyrics
import com.tan.domain.utils.assessTextQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Story previews own their lookup; they never replace the main player's lyrics or queue. */
internal fun storyLyricsFlow(
    preferred: String,
    spotifyAvailable: Boolean,
    readCache: suspend () -> Lyrics?,
    fetch: suspend (LyricsProvider) -> Lyrics?,
): Flow<Lyrics> = channelFlow {
    var cached = try { withTimeoutOrNull(1500) { readCache() }?.assessTextQuality()?.takeIf { it.hasReadableLines() } }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
    if (cached?.hasDisplayableLyrics() == true) send(cached)
    var displayed = cached?.takeIf { it.hasDisplayableLyrics() }
    resolveLyricsFallback(lyricsProviderOrder(preferred, spotifyAvailable), preferWordTiming = true,
        referenceLyrics = cached,
        onCandidate = { candidate ->
            cached = cached?.reconcileWordBoundaries(candidate.lyrics)
            val result = if (keepsFormattedRichCache(cached, candidate.lyrics)) cached!! else candidate.lyrics
            if (result != displayed) {
                trySend(result)
                displayed = result
            }
        }, fetch = fetch)
}
