package com.tan.data.cache

import com.tan.data.io.cacheDir
import com.tan.data.io.fileSystem
import com.tan.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.Path
import okio.Path.Companion.toPath

/** Small, disposable metadata cache. Account-sensitive callers must include their owner in the key. */
class JsonContentCache(
    private val directory: () -> Path = { "${cacheDir()}/content_cache_v1".toPath() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxEntries: Int = 64,
    private val maxBytes: Long = 8L * 1024 * 1024,
) {
    @Serializable private data class Entry(val savedAt: Long, val value: String)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val fs get() = fileSystem()
    private fun path(key: String) = directory() / (key.encodeUtf8().sha256().hex() + ".json")

    suspend fun <T> read(key: String, maxAgeMs: Long, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val p = path(key)
                val size = fs.metadataOrNull(p)?.size ?: return@withLock null
                if (size > 512 * 1024) return@withLock null
                val entry = fs.read(p) { json.decodeFromString<Entry>(readUtf8()) }
                val age = now() - entry.savedAt
                if (age !in 0..maxAgeMs) return@withLock null
                json.decodeFromString(serializer, entry.value)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }

    suspend fun <T> write(key: String, value: T, serializer: KSerializer<T>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val text = json.encodeToString(Entry.serializer(), Entry(now(), json.encodeToString(serializer, value)))
                if (text.encodeToByteArray().size > 512 * 1024) return@withLock
                fs.createDirectories(directory())
                val p = path(key)
                val temporary = (p.toString() + ".tmp").toPath()
                fs.write(temporary) { writeUtf8(text) }
                fs.atomicMove(temporary, p)
                val files = fs.list(directory()).filter { it.name.endsWith(".json") }
                    .map { it to fs.metadata(it) }.sortedByDescending { it.second.lastModifiedAtMillis ?: 0 }
                var used = 0L
                files.forEachIndexed { index, (file, metadata) ->
                    used += metadata.size ?: 0
                    if (index >= maxEntries || used > maxBytes) fs.delete(file)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Cache failure must not prevent displaying fresh content. */ }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { fs.listOrNull(directory()).orEmpty().forEach { fs.delete(it) } }
    }
}

/** Fresh entries skip the network; an expired entry is an offline fallback after a refresh attempt. */
fun <T> cachedContent(
    cache: JsonContentCache?, key: suspend () -> String, freshMs: Long,
    serializer: KSerializer<T>, fetch: () -> Flow<Resource<T>>,
): Flow<Resource<T>> = flow<Resource<T>> {
    if (cache == null) { fetch().collect { emit(it) }; return@flow }
    val identity = key()
    suspend fun current() { if (key() != identity) throw CancellationException("Content owner changed") }
    val fresh = cache.read(identity, freshMs, serializer)
    current()
    if (fresh != null) { emit(Resource.Success(fresh)); return@flow }
    val stale = cache.read(identity, 7L * 24 * 60 * 60 * 1000, serializer)
    current()
    try {
        fetch().collect { result ->
            current()
            if (result is Resource.Success && result.data != null) {
                cache.write(identity, result.data!!, serializer)
                current(); emit(result)
            } else if (stale == null) emit(result) else emit(Resource.Success(stale))
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { if (stale == null) emit(Resource.Error("Content could not load")) else emit(Resource.Success(stale)) }
}.flowOn(Dispatchers.IO)
