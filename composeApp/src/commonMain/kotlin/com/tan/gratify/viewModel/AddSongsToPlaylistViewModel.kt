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
)

class AddSongsToPlaylistViewModel(
    private val searchRepository: SearchRepository,
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(AddSongsToPlaylistState())
    val uiState: StateFlow<AddSongsToPlaylistState> get() = _uiState.asStateFlow()

    private var searchJob: Job? = null

    companion object {
        const val MIN_TRACKS = 4
    }

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
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob?.cancel()
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

    fun canSave(): Boolean = _uiState.value.selectedTracks.size >= MIN_TRACKS

    fun savePlaylist(title: String) {
        if (!canSave()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val localPlaylistEntity = LocalPlaylistEntity(title = title)
            val createdId = localPlaylistRepository.insertLocalPlaylistAndGetId(localPlaylistEntity)

            if (createdId > 0) {
                val selectedTracks = _uiState.value.selectedTracks
                localPlaylistRepository.addTracksToLocalPlaylist(
                    id = createdId,
                    songs = selectedTracks.map { it.toSongEntity() },
                )
            }

            _uiState.update { it.copy(isSaving = false, createdPlaylistId = createdId) }
        }
    }
}
