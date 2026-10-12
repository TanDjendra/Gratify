package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.common.Config
import com.tan.common.LibraryChipType
import com.tan.domain.data.entities.AlbumEntity
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.PlaylistEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.type.ChartItem
import com.tan.domain.data.type.PlaylistType
import com.tan.domain.data.type.RecentlyType
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.YouTubeLibraryRepository
import com.tan.domain.repository.YouTubeConnectionStatus
import com.tan.domain.repository.AlbumRepository
import com.tan.domain.repository.ArtistRepository
import com.tan.domain.repository.CommonRepository
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.PlaylistRepository
import com.tan.domain.repository.PodcastRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.utils.LocalResource
import com.tan.domain.utils.Resource
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.added_local_playlist
import gratify.composeapp.generated.resources.youtube_liked_music

class LibraryViewModel(
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val commonRepository: CommonRepository,
    private val playlistRepository: PlaylistRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val podcastRepository: PodcastRepository,
    private val artistRepository: ArtistRepository,
    val officialYouTube: YouTubeLibraryRepository,
) : BaseViewModel() {
    private val _currentScreen: MutableStateFlow<LibraryChipType> = MutableStateFlow(LibraryChipType.YOUR_LIBRARY)
    val currentScreen: StateFlow<LibraryChipType> get() = _currentScreen.asStateFlow()
    private val _recentlyAdded: MutableStateFlow<LocalResource<List<RecentlyType>>> =
        MutableStateFlow(LocalResource.Loading())
    val recentlyAdded: StateFlow<LocalResource<List<RecentlyType>>> get() = _recentlyAdded.asStateFlow()

    private val _followedArtists: MutableStateFlow<LocalResource<List<com.tan.domain.data.entities.ArtistEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val followedArtists: StateFlow<LocalResource<List<com.tan.domain.data.entities.ArtistEntity>>> get() = _followedArtists.asStateFlow()

    private val _yourLocalPlaylist: MutableStateFlow<LocalResource<List<LocalPlaylistEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val yourLocalPlaylist: StateFlow<LocalResource<List<LocalPlaylistEntity>>> get() = _yourLocalPlaylist.asStateFlow()

    private val _youTubePlaylist: MutableStateFlow<LocalResource<List<PlaylistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubePlaylist: StateFlow<LocalResource<List<PlaylistsResult>>> get() = _youTubePlaylist.asStateFlow()

    private val _youTubeMixForYou: MutableStateFlow<LocalResource<List<PlaylistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubeMixForYou: StateFlow<LocalResource<List<PlaylistsResult>>> get() = _youTubeMixForYou.asStateFlow()

    private val _favoritePlaylist: MutableStateFlow<LocalResource<List<PlaylistType>>> =
        MutableStateFlow(LocalResource.Loading())
    val favoritePlaylist: StateFlow<LocalResource<List<PlaylistType>>> get() = _favoritePlaylist.asStateFlow()

    private val _favoritePodcasts: MutableStateFlow<LocalResource<List<PlaylistType>>> =
        MutableStateFlow(LocalResource.Loading())
    val favoritePodcasts: StateFlow<LocalResource<List<PlaylistType>>> get() = _favoritePodcasts.asStateFlow()

    private val _downloadedPlaylist: MutableStateFlow<LocalResource<List<PlaylistType>>> =
        MutableStateFlow(LocalResource.Loading())
    val downloadedPlaylist: StateFlow<LocalResource<List<PlaylistType>>> get() = _downloadedPlaylist.asStateFlow()

    private val _chartPlaylists: MutableStateFlow<LocalResource<List<ChartItem>>> =
        MutableStateFlow(LocalResource.Loading())
    val chartPlaylists: StateFlow<LocalResource<List<ChartItem>>> get() = _chartPlaylists.asStateFlow()

    private val _listCanvasSong: MutableStateFlow<LocalResource<List<SongEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val listCanvasSong: StateFlow<LocalResource<List<SongEntity>>> get() = _listCanvasSong.asStateFlow()

    private val _accountThumbnail: MutableStateFlow<String?> = MutableStateFlow(null)
    val accountThumbnail: StateFlow<String?> get() = _accountThumbnail.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val youtubeLoggedIn = officialYouTube.connection.mapLatest { it.status == com.tan.domain.repository.YouTubeConnectionStatus.CONNECTED }

    val youtubeCollections = YouTubeCollectionsState(officialYouTube, viewModelScope)

    init {
        viewModelScope.launch {
            val currentScreenJob =
                launch {
                    dataStoreManager.getString("library_current_screen").first()?.let { chipType ->
                        LibraryChipType.fromStringValue(chipType)?.let {
                            _currentScreen.value = it
                        }
                    }
                }
            val cookieJob =
                launch {
                    dataStoreManager.cookie.distinctUntilChanged().collect {
                        _accountThumbnail.value = dataStoreManager.getString("AppProfileImage").first().takeIf { !it.isNullOrEmpty() }
                    }
                }
            currentScreenJob.join()
            cookieJob.join()
        }
    }

    fun setCurrentScreen(chipType: LibraryChipType) {
        _currentScreen.value = chipType
        viewModelScope.launch {
            dataStoreManager.putString("library_current_screen", chipType.toStringValue())
        }
    }

    fun getRecentlyAdded() {
        viewModelScope.launch {
            commonRepository.getAllRecentData().collectLatest { data ->
                val temp: MutableList<RecentlyType> = mutableListOf()
                temp.addAll(data)
                temp
                    .find {
                        it is PlaylistEntity && (it.id.contains("RDEM") || it.id.contains("RDAMVM"))
                    }.let {
                        temp.remove(it)
                    }
                temp.removeIf { it is SongEntity && it.inLibrary == Config.REMOVED_SONG_DATE_TIME }
                temp.removeIf { it is PlaylistEntity && it.id == "LM" }
                temp.reverse()
                _recentlyAdded.value = LocalResource.Success(temp.toImmutableList())
            }
        }
    }

    private var followedArtistsJob: kotlinx.coroutines.Job? = null

    fun getFollowedArtists() {
        if (followedArtistsJob?.isActive == true) return
        _followedArtists.value = LocalResource.Loading()
        followedArtistsJob = viewModelScope.launch {
            artistRepository.getFollowedArtists().catch {
                _followedArtists.value = LocalResource.Error(it.message ?: "Could not load followed artists")
            }.collect { values ->
                _followedArtists.value = LocalResource.Success(values)
            }
        }
    }

    fun getYouTubePlaylist() {
        _youTubePlaylist.value = LocalResource.Loading()
        viewModelScope.launch {
            playlistRepository.getLibraryPlaylist().collect { data ->
                _youTubePlaylist.value = LocalResource.Success(data ?: emptyList())
            }
        }
    }

    fun getYouTubeMixedForYou() {
        _youTubeMixForYou.value = LocalResource.Loading()
        viewModelScope.launch {
            playlistRepository.getMixedForYou().collect { data ->
                _youTubeMixForYou.value = LocalResource.Success(data ?: emptyList())
            }
        }
    }

    fun getYouTubeLoggedIn(): Boolean = officialYouTube.connection.value.status == com.tan.domain.repository.YouTubeConnectionStatus.CONNECTED

    fun getPlaylistFavorite() {
        viewModelScope.launch {
            albumRepository.getLikedAlbums().collect { album ->
                val temp: MutableList<PlaylistType> = mutableListOf()
                temp.addAll(album)
                playlistRepository.getLikedPlaylists().collect { playlist ->
                    temp.addAll(playlist)
                    val sortedList =
                        temp.sortedWith<PlaylistType>(
                            Comparator { p0, p1 ->
                                val timeP0: LocalDateTime? =
                                    when (p0) {
                                        is AlbumEntity -> p0.favoriteAt ?: p0.inLibrary
                                        is PlaylistEntity -> p0.favoriteAt ?: p0.inLibrary
                                        else -> null
                                    }
                                val timeP1: LocalDateTime? =
                                    when (p1) {
                                        is AlbumEntity -> p1.favoriteAt ?: p1.inLibrary
                                        is PlaylistEntity -> p1.favoriteAt ?: p1.inLibrary
                                        else -> null
                                    }
                                if (timeP0 == null || timeP1 == null) {
                                    return@Comparator if (timeP0 == null && timeP1 == null) {
                                        0
                                    } else if (timeP0 == null) {
                                        -1
                                    } else {
                                        1
                                    }
                                }
                                timeP0.compareTo(timeP1) // Sort in descending order by inLibrary time
                            },
                        )
                    _favoritePlaylist.value = LocalResource.Success(sortedList)
                }
            }
        }
    }

    fun getFavoritePodcasts() {
        viewModelScope.launch {
            podcastRepository.getFavoritePodcasts().collectLatest { podcasts ->
                val sortedList = podcasts.sortedByDescending { it.favoriteTime }
                _favoritePodcasts.value = LocalResource.Success(sortedList)
            }
        }
    }

    fun getCanvasSong() {
        _listCanvasSong.value = LocalResource.Loading()
        viewModelScope.launch {
            songRepository.getCanvasSong(max = 5).collect { data ->
                _listCanvasSong.value = LocalResource.Success(data)
            }
        }
    }

    private var localPlaylistJob: kotlinx.coroutines.Job? = null

    fun getLocalPlaylist() {
        if (localPlaylistJob?.isActive == true) return
        _yourLocalPlaylist.value = LocalResource.Loading()
        localPlaylistJob = viewModelScope.launch {
            localPlaylistRepository.getAllLocalPlaylists().catch {
                _yourLocalPlaylist.value = LocalResource.Error(it.message ?: "Could not load playlists")
            }.collect { values ->
                _yourLocalPlaylist.value = LocalResource.Success(values.reversed())
            }
        }
    }

    fun getDownloadedPlaylist() {
        viewModelScope.launch {
            playlistRepository.getAllDownloadedPlaylist().collect { values ->
                _downloadedPlaylist.value = LocalResource.Success(values)
            }
        }
    }

    fun getChartPlaylists() {
        _chartPlaylists.value = LocalResource.Loading()
        viewModelScope.launch {
            playlistRepository.getChartPlaylist().collectLatest {
                when (it) {
                    is Resource.Success -> _chartPlaylists.value = LocalResource.Success(it.data ?: emptyList())
                    is Resource.Error -> _chartPlaylists.value = LocalResource.Error(it.message ?: "Unknown error")
                }
            }
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            val localPlaylistEntity = LocalPlaylistEntity(title = title)
            localPlaylistRepository
                .insertLocalPlaylist(
                    localPlaylistEntity,
                    getString(Res.string.added_local_playlist),
                ).lastOrNull()
                ?.let {
                    log("Created playlist with id: $it")
                }
            getLocalPlaylist()
        }
    }

    fun deleteSong(videoId: String) {
        _recentlyAdded.value = LocalResource.Loading()
        viewModelScope.launch {
            songRepository.setInLibrary(videoId, Config.REMOVED_SONG_DATE_TIME)
            songRepository.resetTotalPlayTime(videoId)
            delay(500) // Wait for the database to update
            getRecentlyAdded()
        }
    }

    fun deleteLocalPlaylist(id: Long) {
        viewModelScope.launch {
            localPlaylistRepository
                .deleteLocalPlaylist(id, "Playlist dihapus")
                .collectLatest { result ->
                    when (result) {
                        is LocalResource.Success -> { makeToast(result.data ?: "Playlist dihapus"); getLocalPlaylist() }
                        is LocalResource.Error -> makeToast(result.message ?: "Gagal menghapus playlist")
                        else -> Unit
                    }
                }
        }
    }

}
