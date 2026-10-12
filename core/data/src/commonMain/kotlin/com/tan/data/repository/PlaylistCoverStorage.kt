package com.tan.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

internal fun storedPlaylistCoverUrl(url: String?): String? = url?.let {
    if ("/storage/v1/object/sign/playlist-covers/" in it || "/storage/v1/object/sign/workspace-covers/" in it)
        it.replace("/storage/v1/object/sign/", "/storage/v1/object/public/").substringBefore('?') else it
}
private fun workspaceCoverPath(url:String?):String? = storedPlaylistCoverUrl(url)
    ?.substringAfter("/storage/v1/object/public/workspace-covers/","")?.substringBefore('?')
    ?.takeIf { Regex("[a-f0-9-]{36}/[a-f0-9-]{36}/[a-f0-9-]{36}[.]jpg").matches(it) }

internal fun playlistCoverPath(url: String?): String? = storedPlaylistCoverUrl(url)
    ?.substringAfter("/storage/v1/object/public/playlist-covers/", "")
    ?.substringBefore('?')?.takeIf { it.isNotBlank() && it.split('/').size == 3 && ".." !in it }

/** The stored URL is an object identity, not a public read grant. */
internal class PlaylistCoverStorage(private val supabase: SupabaseClient) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Pair<Long, String>>()
    private val requests = Semaphore(4)

    suspend fun <T> map(items: List<T>, url: (T)->String?, copy: (T,String?)->T): List<T> = coroutineScope {
        items.map { item -> async { copy(item,displayUrl(url(item))) } }.awaitAll()
    }

    suspend fun cachedDisplayUrl(url: String?): String? {
        val path = workspaceCoverPath(url) ?: playlistCoverPath(url) ?: return url
        val bucket=if(workspaceCoverPath(url)!=null)"workspace-covers" else "playlist-covers"
        if (supabase.auth.sessionStatus.value is io.github.jan.supabase.auth.status.SessionStatus.RefreshFailure) return null
        val key = "${supabase.auth.currentUserOrNull()?.id}:$bucket:$path"
        val now = Clock.System.now().toEpochMilliseconds()
        return mutex.withLock { cache[key]?.takeIf { now < it.first }?.second }
    }

    suspend fun displayUrl(url: String?): String? {
        val path = workspaceCoverPath(url) ?: playlistCoverPath(url) ?: return url
        val bucket=if(workspaceCoverPath(url)!=null)"workspace-covers" else "playlist-covers"
        if (supabase.auth.sessionStatus.value is io.github.jan.supabase.auth.status.SessionStatus.RefreshFailure) return null
        val key = "${supabase.auth.currentUserOrNull()?.id}:$bucket:$path"
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock { cache[key]?.takeIf { now < it.first }?.let { return it.second } }
        return requests.withPermit {
            try {
                val signed = withTimeoutOrNull(2_000) {
                    supabase.storage.from(bucket).createSignedUrl(path, 5.minutes)
                } ?: return@withPermit null
                mutex.withLock {
                    cache.entries.removeAll { now >= it.value.first }
                    while(cache.size>=128)cache.remove(cache.keys.first())
                    cache[key] = now + 60_000 to signed
                }
                signed
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
}
