package com.tan.gratify.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.entities.analytics.PlaybackEventEntity
import com.tan.domain.extension.now
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

data class WrappedRank(val title: String, val plays: Int, val id: String = "", val seconds: Long = 0,
    val subtitle: String = "", val thumbnail: String? = null)
data class WrappedSummary(
    val minutes: Long = 0, val plays: Int = 0, val uniqueTracks: Int = 0, val activeDays: Int = 0,
    val biggestDay: String? = null, val totalSeconds: Long = 0, val biggestDaySeconds: Long = 0,
    val firstDay: String? = null, val lastDay: String? = null, val longestStreak: Int = 0,
    val monthlySeconds: List<Long> = List(12) { 0L }, val clockBand: Int = 0,
)

internal fun wrappedEvents(events: List<PlaybackEventEntity>, year: Int, asOf: LocalDateTime): List<PlaybackEventEntity> {
    val seen = mutableSetOf<Long>()
    return events.filter { it.timestamp.year == year && it.timestamp <= asOf && it.videoId.isNotBlank() &&
        it.listenedSecond in 1..86_400 && (it.eventId <= 0 || seen.add(it.eventId)) }
}

fun summarizeWrapped(events: List<PlaybackEventEntity>, year: Int, asOf: LocalDateTime = now()): WrappedSummary {
    val selected = wrappedEvents(events, year, asOf)
    val days = selected.groupBy { it.timestamp.date }.mapValues { (_, rows) -> rows.sumOf { it.listenedSecond } }
    val biggest = days.entries.sortedWith(compareByDescending<Map.Entry<LocalDate, Long>> { it.value }.thenBy { it.key }).firstOrNull()
    val monthly = (1..12).map { month -> selected.filter { it.timestamp.monthNumber == month }.sumOf { it.listenedSecond } }
    val bands = (0..3).map { band -> selected.filter { it.timestamp.hour / 6 == band }.sumOf { it.listenedSecond } }
    var streak = 0; var longest = 0; var previous: LocalDate? = null
    days.keys.sorted().forEach { day ->
        streak = if (previous?.plus(DatePeriod(days = 1)) == day) streak + 1 else 1
        longest = maxOf(longest, streak); previous = day
    }
    val seconds = selected.sumOf { it.listenedSecond }
    return WrappedSummary(seconds / 60, selected.count { it.listenedSecond >= 30 }, selected.map { it.videoId }.distinct().size,
        days.size, biggest?.key?.toString(), seconds, biggest?.value ?: 0, days.keys.minOrNull()?.toString(),
        days.keys.maxOrNull()?.toString(), longest, monthly, bands.indices.maxByOrNull { bands[it] } ?: 0)
}

data class WrappedAccess(val ownerId: String? = null, val ready: Boolean = true, val loading: Boolean = false)
data class WrappedState(
    val year: Int = now().year, val years: List<Int> = listOf(now().year), val loading: Boolean = true,
    val error: Boolean = false, val summary: WrappedSummary = WrappedSummary(),
    val tracks: List<WrappedRank> = emptyList(), val artists: List<WrappedRank> = emptyList(),
    val provisional: Boolean = true, val trackingEnabled: Boolean = false, val metadataIncomplete: Boolean = false,
    val playlistAvailable: Boolean = false, val savingPlaylist: Boolean = false, val playlistId: Long? = null,
    val playlistError: Boolean = false,
)

class WrappedViewModel(
    private val analyticsRepository: AnalyticsRepository, private val songRepository: SongRepository,
    private val commonRepository: CommonRepository? = null, private val playlists: LocalPlaylistRepository? = null,
    tracking: Flow<Boolean> = flowOf(true), access: Flow<WrappedAccess> = flowOf(WrappedAccess()),
    private val clock: () -> LocalDateTime = ::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WrappedState(year = clock().year, years = listOf(clock().year)))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var account = WrappedAccess(ready = false, loading = true)
    private var topSongs = emptyList<SongEntity>()
    init {
        viewModelScope.launch { tracking.collect { value -> mutableState.update { it.copy(trackingEnabled = value) } } }
        viewModelScope.launch {
            access.distinctUntilChanged().collect { value ->
                account = value; job?.cancel(); generation++; topSongs = emptyList()
                mutableState.value = WrappedState(year = clock().year, years = listOf(clock().year), loading = value.loading,
                    error = !value.ready && !value.loading, trackingEnabled = mutableState.value.trackingEnabled)
                if (value.ready) load(clock().year)
            }
        }
    }

    private suspend fun owns(owner: WrappedAccess): Boolean = account == owner && owner.ready &&
        (commonRepository == null || commonRepository.notificationAccountOwner() == owner.ownerId)

    fun load(year: Int) {
        if (year !in 1970..clock().year || !account.ready || mutableState.value.savingPlaylist) return
        job?.cancel(); val request = ++generation; val owner = account; val today = clock()
        topSongs = emptyList()
        mutableState.value = WrappedState(year, mutableState.value.years, trackingEnabled = mutableState.value.trackingEnabled,
            provisional = year == today.year)
        job = viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    check(owns(owner))
                    val years = (analyticsRepository.getPlaybackYears().first().filter { it in 1970..today.year } + today.year)
                        .distinct().sortedDescending()
                    val events = wrappedEvents(analyticsRepository.getPlaybackEventsInRange(LocalDateTime(year, 1, 1, 0, 0),
                        LocalDateTime(year + 1, 1, 1, 0, 0)).first(), year, today)
                    val songs = mutableMapOf<String, SongEntity>(); var partial = false
                    events.map { it.videoId }.distinct().sorted().chunked(200).forEach { ids ->
                        try { songRepository.getSongsByListVideoId(ids).first().forEach { songs[it.videoId] = it } }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { partial = true }
                    }
                    val ranked = withContext(Dispatchers.Default) {
                    val trackRanks = events.groupBy { it.videoId }.mapNotNull { (id, rows) ->
                        val plays = rows.count { it.listenedSecond >= 30 }; if (plays == 0) return@mapNotNull null
                        val song = songs[id]
                        WrappedRank(song?.title.orEmpty(), plays, id, rows.sumOf { it.listenedSecond },
                            song?.artistName.orEmpty().joinToString(", "), song?.thumbnails)
                    }.sortedWith(compareByDescending<WrappedRank> { it.plays }.thenByDescending { it.seconds }.thenBy { it.id })
                    val artistRows = mutableMapOf<String, WrappedRank>()
                    events.groupBy { it.videoId }.forEach { (id, rows) ->
                        val song = songs[id] ?: return@forEach; val credited = mutableSetOf<String>()
                        song.artistName.orEmpty().forEachIndexed { index, raw ->
                            val name = raw.trim(); if (name.isBlank()) return@forEachIndexed
                            val key = song.artistId?.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "name:${name.lowercase()}"
                            if (!credited.add(key)) return@forEachIndexed
                            val previous = artistRows[key] ?: WrappedRank(name, 0, key)
                            artistRows[key] = previous.copy(plays = previous.plays + rows.count { it.listenedSecond >= 30 },
                                seconds = previous.seconds + rows.sumOf { it.listenedSecond })
                        }
                    }
                    Triple(trackRanks, artistRows.values.sortedWith(compareByDescending<WrappedRank> { it.seconds }
                        .thenByDescending { it.plays }.thenBy { it.id }).take(5), summarizeWrapped(events, year, today))
                    }
                    val trackRanks = ranked.first
                    check(owns(owner)); if (request != generation) return@withTimeout
                    topSongs = trackRanks.mapNotNull { songs[it.id] }.take(50)
                    mutableState.value = WrappedState(year, years, loading = false, summary = ranked.third,
                        tracks = trackRanks.take(5), artists = ranked.second, provisional = year == today.year,
                        trackingEnabled = mutableState.value.trackingEnabled,
                        metadataIncomplete = partial || events.any { it.videoId !in songs }, playlistAvailable = playlists != null && topSongs.isNotEmpty())
                }
            } catch (_: TimeoutCancellationException) { if (request == generation) mutableState.update { it.copy(loading = false, error = true) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == generation) mutableState.update { it.copy(loading = false, error = true) } }
        }
    }

    fun savePlaylist() {
        val repository = playlists ?: return
        val snapshot = state.value
        if (snapshot.savingPlaylist || snapshot.playlistId != null || !snapshot.playlistAvailable || snapshot.loading) return
        val songs = topSongs.toList(); val owner = account; val request = generation
        mutableState.update { it.copy(savingPlaylist = true, playlistError = false) }
        viewModelScope.launch {
            try {
                val id = withTimeout(20_000) {
                    check(owns(owner))
                    val title = "Gratify Wrapped ${snapshot.year}"
                    val existing = repository.getAllLocalPlaylists().first().firstOrNull { it.title == title && it.tracks == songs.map { song -> song.videoId } }
                    existing?.id ?: repository.createLocalPlaylistWithSongsForOwner(LocalPlaylistEntity(title = title,
                        thumbnail = songs.firstOrNull()?.thumbnails, downloadedAt = null), songs, owner.ownerId)
                }
                if (request == generation && owns(owner)) mutableState.update { it.copy(playlistId = id) }
            } catch (_: TimeoutCancellationException) { if (request == generation) mutableState.update { it.copy(playlistError = true) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == generation) mutableState.update { it.copy(playlistError = true) } }
            finally { if (request == generation) mutableState.update { it.copy(savingPlaylist = false) } }
        }
    }
}
