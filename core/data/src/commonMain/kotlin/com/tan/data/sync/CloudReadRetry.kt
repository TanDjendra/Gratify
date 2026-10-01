package com.tan.data.sync

import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Retry transport failures for reads only. Mutations and server rejections are never replayed. */
internal suspend fun <T> retryCloudRead(
    attempts: Int = 3,
    retryDelayMillis: Long = 1_000,
    read: suspend () -> T,
): T {
    require(attempts > 0 && retryDelayMillis >= 0)
    repeat(attempts - 1) {
        try { return read() }
        catch (_: HttpRequestException) { delay(retryDelayMillis) }
    }
    return read()
}

internal suspend fun restoreLibraryAndPlaylists(library: suspend () -> Unit, playlists: suspend () -> Unit) {
    val libraryError = try { library(); null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e }
    val playlistError = try { playlists(); null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e }
    (libraryError ?: playlistError)?.let { throw it }
}
