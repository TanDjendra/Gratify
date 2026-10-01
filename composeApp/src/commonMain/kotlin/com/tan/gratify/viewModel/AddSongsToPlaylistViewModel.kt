package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.SearchRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.utils.Resource
import com.tan.domain.utils.toSongEntity
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddSongsToPlaylistState(
    val searchQuery: String = "",
    val searchResults: List<SongsResult> = emptyList(),
    val recentSongs: List<SongEntity> = emptyList(),
    val selectedTracks: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val createdPlaylistId: Long? = null,
    val saveError: String? = null,
)

class AddSongsToPlaylistViewModel(
    private val searchRepository: SearchRepository,
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(AddSongsToPlaylistState())
    val uiState: StateFlow<AddSongsToPlaylistState> get() = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecentSongs()
    }

    fun loadRecentSongs() {
        viewModelScope.launch {
            val songs = songRepository.getRecentSong(limit = 20, offset = 0)
            _uiState.update { it.copy(recentSongs = songs) }
        }
    }

    fun searchSongs(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearching = true) }
            searchRepository.getSearchDataSong(query).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                searchResults = result.data ?: emptyList(),
                                isSearching = false,
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
        searchJob?.cancel()
    }

    fun addTrack(track: Track) {
        _uiState.update { state ->
            if (state.selectedTracks.any { it.videoId == track.videoId }) {
                state
            } else {
                state.copy(selectedTracks = state.selectedTracks + track)
            }
        }
    }

    fun removeTrack(videoId: String) {
        _uiState.update { state ->
            state.copy(selectedTracks = state.selectedTracks.filter { it.videoId != videoId })
        }
    }

    fun isTrackSelected(videoId: String): Boolean =
        _uiState.value.selectedTracks.any { it.videoId == videoId }

    fun canSave(): Boolean = !_uiState.value.isSaving

    fun savePlaylist(title: String) {
        if (!canSave() || title.isBlank()) return
        val selectedTracks = _uiState.value.selectedTracks.toList()
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val createdId = localPlaylistRepository.createLocalPlaylistWithSongs(
                    LocalPlaylistEntity(title = title.trim()), selectedTracks.map { it.toSongEntity() })
                check(createdId > 0) { "Playlist could not be created" }
                _uiState.update { it.copy(createdPlaylistId = createdId) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(saveError = "Playlist belum tersimpan. Coba lagi.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
