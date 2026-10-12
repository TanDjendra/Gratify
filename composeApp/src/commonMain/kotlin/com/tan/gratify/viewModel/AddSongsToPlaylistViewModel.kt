package com.tan.gratify.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tan.data.sync.AccountAccess
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.SearchRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.utils.Resource
import com.tan.domain.utils.LocalResource
import com.tan.domain.utils.toSongEntity
import com.tan.domain.utils.toTrack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AddSongsToPlaylistState(
    val searchQuery: String = "",
    val searchResults: List<SongsResult> = emptyList(),
    val recentSongs: List<SongEntity> = emptyList(),
    val selectedTracks: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val createdPlaylistId: Long? = null,
    val saveError: String? = null,
    val title: String = "",
    val titleInvalid: Boolean = false,
    val searchError: Boolean = false,
    val recentLoading: Boolean = false,
    val recentError: Boolean = false,
    val accountBlocked: Boolean = false,
    val accountUnavailable: Boolean = false,
    val ownerChanged: Boolean = false,
    val targetPlaylistId: Long? = null,
    val existingTrackIds: Set<String> = emptySet(),
    val targetLoading: Boolean = false,
    val targetUnavailable: Boolean = false,
    val likedSongs: List<SongEntity> = emptyList(),
    val likedLoading: Boolean = false,
    val likedError: Boolean = false,
    val recommendations: List<Track> = emptyList(),
    val recommendationsLoading: Boolean = false,
    val recommendationsError: Boolean = false,
    val pendingTrackIds: Set<String> = emptySet(),
    val failedTrackIds: Set<String> = emptySet(),
    val addedCount: Int = 0,
    val sessionAddedTrackIds: Set<String> = emptySet(),
    val membershipChangeCount: Int = 0,
)

class AddSongsToPlaylistViewModel(
    private val searchRepository: SearchRepository,
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val accountAccess: StateFlow<AccountAccess>? = null,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(AddSongsToPlaylistState(
        title = savedStateHandle["playlist_title"] ?: "",
        searchQuery = savedStateHandle["playlist_query"] ?: "",
        selectedTracks = savedStateHandle.get<String>("playlist_tracks")?.let {
            runCatching { Json.decodeFromString<List<Track>>(it) }.getOrNull()
        }.orEmpty().filter { it.videoId.isNotBlank() }.distinctBy(Track::videoId),
        createdPlaylistId = savedStateHandle["playlist_created_id"],
        accountBlocked = accountAccess != null,
        ownerChanged = savedStateHandle["playlist_owner_changed"] ?: false,
        targetPlaylistId = savedStateHandle["targetPlaylistId"] ?: savedStateHandle["playlist_target_id"],
        targetLoading = savedStateHandle.get<Long>("targetPlaylistId") != null || savedStateHandle.get<Long>("playlist_target_id") != null,
    ))
    val uiState: StateFlow<AddSongsToPlaylistState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var recentJob: Job? = null
    private var saveJob: Job? = null
    private var targetJob: Job? = null
    private var likedJob: Job? = null
    private var recommendationsJob: Job? = null
    private val appendJobs = mutableMapOf<String, Job>()
    private val appendMutex = Mutex()
    private var recommendationSeed: String? = null
    private var initialTargetTrackIds: Set<String>? = null
    private var searchGeneration = 0L
    private fun cancelDiscovery() {
        likedJob?.cancel(); recommendationsJob?.cancel()
        appendJobs.values.toList().forEach { it.cancel() }; appendJobs.clear()
    }
    private fun ownerKey(access: AccountAccess?): String? = when(access) {
        is AccountAccess.Ready -> access.owner
        AccountAccess.SignedOut -> "guest"
        null -> "unscoped-test"
        else -> null
    }
    private fun matchesOwner(key: String?) = key != null && ownerKey(accountAccess?.value) == key &&
        !_uiState.value.ownerChanged && (accountAccess == null || savedStateHandle.get<String>("playlist_draft_owner") == key)
    private fun editable() = !_uiState.value.isSaving && _uiState.value.createdPlaylistId == null &&
        !_uiState.value.targetLoading && !_uiState.value.targetUnavailable &&
        matchesOwner(ownerKey(accountAccess?.value))

    init {
        if (accountAccess == null) resumeReads() else viewModelScope.launch {
            accountAccess.collect { access ->
                val key = ownerKey(access)
                if (key == null) {
                    searchJob?.cancel(); recentJob?.cancel(); targetJob?.cancel(); cancelDiscovery()
                    _uiState.update { it.copy(accountBlocked = true, accountUnavailable = access == AccountAccess.RecoveryRequired,
                        searchResults = emptyList(), recentSongs = emptyList(), likedSongs = emptyList(), recommendations = emptyList(),
                        pendingTrackIds = emptySet(), failedTrackIds = emptySet(), isSearching = false, recentLoading = false, likedLoading = false, recommendationsLoading = false) }
                } else {
                    val previous = savedStateHandle.get<String>("playlist_draft_owner")
                    if (previous != null && previous != key) {
                        searchGeneration++; searchJob?.cancel(); recentJob?.cancel(); saveJob?.cancel(); targetJob?.cancel(); cancelDiscovery()
                        listOf("playlist_tracks", "playlist_query", "playlist_title", "playlist_initial_title", "playlist_created_id").forEach { savedStateHandle.remove<Any?>(it) }
                        savedStateHandle["playlist_owner_changed"] = true
                        _uiState.value = AddSongsToPlaylistState(ownerChanged = true)
                    }
                    savedStateHandle["playlist_draft_owner"] = key
                    _uiState.update { it.copy(accountBlocked = false, accountUnavailable = false) }
                    if (!_uiState.value.ownerChanged) resumeReads()
                }
            }
        }
    }

    fun bindTitle(initial: String) {
        if (_uiState.value.ownerChanged) return
        if (!savedStateHandle.contains("playlist_initial_title")) savedStateHandle["playlist_initial_title"] = initial.trim().take(100)
        if (!savedStateHandle.contains("playlist_title")) {
            savedStateHandle["playlist_title"] = initial.trim().take(100)
            _uiState.update { it.copy(title = initial.trim().take(100)) }
        }
    }
    fun bindTargetPlaylist(id: Long?) {
        if (id == null || _uiState.value.ownerChanged) return
        check(_uiState.value.targetPlaylistId == null || _uiState.value.targetPlaylistId == id)
        savedStateHandle["playlist_target_id"] = id
        _uiState.update { it.copy(targetPlaylistId = id) }
        loadTargetPlaylist()
    }
    fun loadTargetPlaylist() {
        val id = _uiState.value.targetPlaylistId ?: return
        val owner = ownerKey(accountAccess?.value)
        if (!matchesOwner(owner) || _uiState.value.createdPlaylistId != null) return
        targetJob?.cancel()
        _uiState.update { it.copy(targetLoading = true, targetUnavailable = false) }
        targetJob = viewModelScope.launch {
            try {
                val result = withTimeout(10_000) { localPlaylistRepository.getLocalPlaylist(id).first { it !is LocalResource.Loading } }
                val playlist = result.data
                check(result is LocalResource.Success && playlist != null && playlist.sourceSharedPlaylistId == null)
                if (matchesOwner(owner)) {
                    val existing = playlist.tracks.orEmpty().toSet()
                    val initial = initialTargetTrackIds ?: existing.also { initialTargetTrackIds = it }
                    _uiState.update { it.copy(title = playlist.title, targetLoading = false, existingTrackIds = existing,
                        sessionAddedTrackIds = existing - initial, addedCount = (existing - initial).size,
                        selectedTracks = it.selectedTracks.filter { track -> track.videoId !in existing }) }
                    persistTracks()
                    recommendationSeed = playlist.tracks?.firstOrNull()
                    loadLikedSongs()
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (matchesOwner(owner)) _uiState.update { it.copy(targetLoading = false, targetUnavailable = true, existingTrackIds = emptySet()) }
            }
        }
    }
    fun updateTitle(value: String) {
        if (!editable() || _uiState.value.targetPlaylistId != null || value.length > 100) return
        savedStateHandle["playlist_title"] = value
        _uiState.update { it.copy(title = value, titleInvalid = false, saveError = null) }
    }
    fun hasUnsavedChanges() = _uiState.value.selectedTracks.isNotEmpty() ||
        (_uiState.value.targetPlaylistId == null && _uiState.value.title.isNotBlank())
    private fun resumeReads() {
        if (_uiState.value.createdPlaylistId != null) return
        loadRecentSongs()
        loadTargetPlaylist()
        if (_uiState.value.searchQuery.isNotBlank()) searchSongs(_uiState.value.searchQuery)
    }
    fun loadRecentSongs() {
        recentJob?.cancel()
        val owner = ownerKey(accountAccess?.value)
        if (!matchesOwner(owner)) return
        _uiState.update { it.copy(recentLoading = true, recentError = false) }
        recentJob = viewModelScope.launch {
            try {
                val songs = withTimeout(10_000) { songRepository.getRecentSong(limit = 20, offset = 0) }
                    .filter { it.videoId.isNotBlank() }.distinctBy(SongEntity::videoId)
                if (matchesOwner(owner)) {
                    _uiState.update { it.copy(recentSongs = songs, recentLoading = false) }
                    updateRecommendationFallback()
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (matchesOwner(owner)) _uiState.update { it.copy(recentLoading = false, recentError = true) }
            }
        }
    }
    fun searchSongs(query: String) {
        if (!editable()) return
        savedStateHandle["playlist_query"] = query
        val generation = ++searchGeneration
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, searchResults = emptyList(), searchError = false, isSearching = query.isNotBlank()) }
        if (query.isBlank()) return
        val owner = ownerKey(accountAccess?.value)
        searchJob = viewModelScope.launch {
            try {
                delay(300)
                val result = withTimeout(15_000) { searchRepository.getSearchDataSong(query.trim()).first() }
                if (generation == searchGeneration && matchesOwner(owner)) _uiState.update { it.copy(
                    searchResults = if (result is Resource.Success) result.data.orEmpty().filter { song -> song.videoId.isNotBlank() }.distinctBy(SongsResult::videoId) else emptyList(),
                    searchError = result is Resource.Error, isSearching = false) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (generation == searchGeneration && matchesOwner(owner)) _uiState.update { it.copy(isSearching = false, searchError = true, searchResults = emptyList()) }
            }
        }
    }
    /** Empty creation is a separate step: old restored selections must not be committed silently. */
    fun createEmptyPlaylist() {
        if (!editable() || _uiState.value.targetPlaylistId != null) return
        _uiState.update { it.copy(selectedTracks = emptyList()) }
        persistTracks()
        savePlaylist()
    }
    fun loadLikedSongs() {
        val owner = ownerKey(accountAccess?.value)
        if (!matchesOwner(owner) || _uiState.value.targetPlaylistId == null) return
        likedJob?.cancel()
        _uiState.update { it.copy(likedLoading = true, likedError = false) }
        likedJob = viewModelScope.launch {
            try {
                val songs = withTimeout(10_000) { songRepository.getLikedSongs().first() }
                    .filter { it.videoId.isNotBlank() }.distinctBy(SongEntity::videoId)
                if (matchesOwner(owner)) _uiState.update { it.copy(likedSongs = songs, likedLoading = false) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (matchesOwner(owner)) _uiState.update { it.copy(likedLoading = false, likedError = true) }
            }
            if (matchesOwner(owner)) { updateRecommendationFallback(); loadRecommendations() }
        }
    }
    private fun updateRecommendationFallback() {
        _uiState.update { state ->
            if (state.recommendations.isNotEmpty()) state else state.copy(recommendations =
                (state.recentSongs + state.likedSongs).distinctBy(SongEntity::videoId).map { it.toTrack() })
        }
    }
    fun loadRecommendations() {
        val owner = ownerKey(accountAccess?.value)
        if (!matchesOwner(owner) || _uiState.value.targetPlaylistId == null) return
        val seed = recommendationSeed ?: _uiState.value.recentSongs.firstOrNull()?.videoId
            ?: _uiState.value.likedSongs.firstOrNull()?.videoId ?: return
        recommendationsJob?.cancel()
        _uiState.update { it.copy(recommendationsLoading = true, recommendationsError = false) }
        recommendationsJob = viewModelScope.launch {
            try {
                val result = withTimeout(15_000) { songRepository.getRelatedData(seed).first() }
                if (matchesOwner(owner)) _uiState.update { state -> state.copy(
                    recommendations = ((if (result is Resource.Success) result.data?.first.orEmpty() else emptyList()) +
                        state.recentSongs.map { it.toTrack() } + state.likedSongs.map { it.toTrack() })
                        .filter { it.videoId.isNotBlank() }.distinctBy(Track::videoId),
                    recommendationsLoading = false, recommendationsError = result is Resource.Error) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (matchesOwner(owner)) _uiState.update { it.copy(recommendationsLoading = false, recommendationsError = true) }
            }
        }
    }
    /** Each change persists immediately, in tap order, using owner-checked public-sync transactions. */
    fun appendTrack(track: Track) {
        changeTrackMembership(track, removing = false)
    }
    fun toggleTrack(track: Track) {
        changeTrackMembership(track, removing = track.videoId in _uiState.value.existingTrackIds)
    }
    private fun changeTrackMembership(track: Track, removing: Boolean) {
        val id = _uiState.value.targetPlaylistId ?: return
        if (!editable() || track.videoId.isBlank() || (!removing && track.videoId in _uiState.value.existingTrackIds) ||
            track.videoId in _uiState.value.pendingTrackIds) return
        val owner = ownerKey(accountAccess?.value)
        val accountOwner = (accountAccess?.value as? AccountAccess.Ready)?.owner
        _uiState.update { it.copy(pendingTrackIds = it.pendingTrackIds + track.videoId,
            failedTrackIds = it.failedTrackIds - track.videoId) }
        val appendJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                appendMutex.withLock {
                    if (!matchesOwner(owner)) return@withLock
                    withTimeout(15_000) {
                        if (removing) localPlaylistRepository.removeTrackFromLocalPlaylistForOwner(id, track.toSongEntity(), accountOwner)
                        else localPlaylistRepository.addTracksToLocalPlaylistForOwner(id, listOf(track.toSongEntity()), accountOwner)
                    }
                    // Idempotent repository results still confirm the desired membership.
                    if (matchesOwner(owner)) _uiState.update {
                        val existing = if (removing) it.existingTrackIds - track.videoId else it.existingTrackIds + track.videoId
                        val added = existing - initialTargetTrackIds.orEmpty()
                        it.copy(existingTrackIds = existing, sessionAddedTrackIds = added, addedCount = added.size,
                            membershipChangeCount = it.membershipChangeCount + 1)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (matchesOwner(owner)) _uiState.update { it.copy(failedTrackIds = it.failedTrackIds + track.videoId) }
            } finally {
                if (matchesOwner(owner)) _uiState.update { it.copy(pendingTrackIds = it.pendingTrackIds - track.videoId) }
                if (appendJobs[track.videoId] === currentCoroutineContext()[Job]) appendJobs.remove(track.videoId)
            }
        }
        appendJobs[track.videoId] = appendJob
        appendJob.start()
    }
    fun clearSearch() = searchSongs("")
    fun addTrack(track: Track) {
        if (!editable() || track.videoId.isBlank() || track.videoId in _uiState.value.existingTrackIds) return
        _uiState.update { if (it.selectedTracks.any { selected -> selected.videoId == track.videoId }) it
            else it.copy(selectedTracks = it.selectedTracks + track, saveError = null) }
        persistTracks()
    }
    fun removeTrack(videoId: String) {
        if (!editable()) return
        _uiState.update { it.copy(selectedTracks = it.selectedTracks.filter { track -> track.videoId != videoId }, saveError = null) }
        persistTracks()
    }
    fun moveTrack(videoId: String, offset: Int) {
        if (!editable() || (offset != -1 && offset != 1)) return
        val tracks = _uiState.value.selectedTracks.toMutableList()
        val index = tracks.indexOfFirst { it.videoId == videoId }
        val target = index + offset
        if (index < 0 || target !in tracks.indices) return
        tracks.add(target, tracks.removeAt(index))
        _uiState.update { it.copy(selectedTracks = tracks, saveError = null) }
        persistTracks()
    }
    private fun persistTracks() { savedStateHandle["playlist_tracks"] = Json.encodeToString(_uiState.value.selectedTracks) }
    fun isTrackSelected(videoId: String) = _uiState.value.selectedTracks.any { it.videoId == videoId }
    fun canSave() = editable() && (_uiState.value.targetPlaylistId == null || _uiState.value.selectedTracks.isNotEmpty())
    fun savePlaylist(title: String = _uiState.value.title) {
        if (!canSave()) return
        val cleaned = title.trim()
        val target = _uiState.value.targetPlaylistId
        if (target == null && (cleaned.isBlank() || cleaned.length > 100)) { _uiState.update { it.copy(titleInvalid = true) }; return }
        val owner = ownerKey(accountAccess?.value)
        val accountOwner = (accountAccess?.value as? AccountAccess.Ready)?.owner
        val songs = _uiState.value.selectedTracks.map { it.toSongEntity() }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        saveJob = viewModelScope.launch {
            try {
                val playlist = LocalPlaylistEntity(title = cleaned, thumbnail = songs.firstOrNull()?.thumbnails?.takeIf(String::isNotBlank))
                val created = if (target != null) {
                    localPlaylistRepository.addTracksToLocalPlaylistForOwner(target, songs, accountOwner)
                    target
                } else if (accountAccess == null) localPlaylistRepository.createLocalPlaylistWithSongs(playlist, songs)
                    else localPlaylistRepository.createLocalPlaylistWithSongsForOwner(playlist, songs, accountOwner)
                check(created > 0) { "Playlist could not be created" }
                if (matchesOwner(owner) || ownerKey(accountAccess?.value) == null &&
                    savedStateHandle.get<String>("playlist_draft_owner") == owner && !_uiState.value.ownerChanged) {
                    savedStateHandle["playlist_created_id"] = created
                    _uiState.update { it.copy(createdPlaylistId = created) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (matchesOwner(owner)) _uiState.update { it.copy(saveError = "Playlist belum tersimpan. Coba lagi.") }
            } finally { if (savedStateHandle.get<String>("playlist_draft_owner") == owner || accountAccess == null)
                _uiState.update { it.copy(isSaving = false) } }
        }
    }
}
