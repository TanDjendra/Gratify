/*
 * Adapted from maxrave-dev/core SimpMusicLyricsClient.kt (GPL-3.0),
 * revision d90b53fbad07ab73cecbfe89b33b533fb9ca78ed. Fetching only.
 */
package com.tan.gratify.lyrics

import com.tan.gratify.lyrics.models.response.BaseResponse
import com.tan.gratify.lyrics.models.response.BetterLyricsResponse
import com.tan.gratify.lyrics.models.response.LrclibObject
import com.tan.gratify.lyrics.models.response.LyricsResponse
import com.tan.gratify.lyrics.models.response.TranslatedLyricsResponse
import com.tan.gratify.lyrics.parser.parseSyncedLyrics
import com.tan.gratify.lyrics.parser.parseUnsyncedLyrics
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException

class SimpMusicLyricsClient(private val lyricsService: SimpMusicLyrics = SimpMusicLyrics()) {
    suspend fun getLyrics(videoId: String): Result<List<LyricsResponse>> = fetching {
        lyricsService.findLyricsByVideoId(videoId).bodyOrThrow()
    }

    suspend fun getTranslatedLyrics(videoId: String, language: String): Result<TranslatedLyricsResponse> = fetching {
        require(language.length == 2) { "Language code must be a 2-letter code" }
        lyricsService.findTranslatedLyrics(videoId, language).bodyOrThrow()
    }

    suspend fun searchLrclibLyrics(q_track: String, q_artist: String, duration: Int?) = fetching {
        val track = lyricsSearchTitle(q_track)
        val response = lyricsService.searchLrclibLyrics(track, q_artist)
        check(response.status.value in 200..299) { "LRCLIB HTTP ${response.status.value}" }
        val match = selectLrclibResult(response.body<List<LrclibObject>>(), duration, track, q_artist)
        when {
            !match?.syncedLyrics.isNullOrBlank() -> parseSyncedLyrics(match!!.syncedLyrics!!, match.plainLyrics)
            !match?.plainLyrics.isNullOrBlank() -> parseUnsyncedLyrics(match!!.plainLyrics!!)
            else -> null
        }
    }

    suspend fun searchBetterLyrics(q_track: String, q_artist: String, durationSeconds: Int?) = fetching {
        val response = lyricsService.searchBetterLyrics(q_track, q_artist, durationSeconds)
        check(response.status.value in 200..299) { "Better Lyrics HTTP ${response.status.value}" }
        response.body<BetterLyricsResponse>().ttml
    }

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        val envelope = body<BaseResponse<T>>()
        envelope.error?.let { error("${it.reason} (code: ${it.code})") }
        check(status.value in 200..299 && envelope.success) { "SimpMusic Lyrics HTTP ${status.value}" }
        return envelope.data ?: error("Response data is null")
    }

    private suspend inline fun <T> fetching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    fun close() = lyricsService.close()
}
