package com.tan.gratify.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.filter
import androidx.paging.insertFooterItem
import com.tan.common.ASC
import com.tan.common.CUSTOM_ORDER
import com.tan.common.Config
import com.tan.common.DESC
import com.tan.common.LOCAL_PLAYLIST_ID
import com.tan.common.TITLE
import com.tan.data.db.Converters
import com.tan.domain.data.entities.DownloadState
import com.tan.domain.data.entities.DownloadState.STATE_DOWNLOADED
import com.tan.domain.data.entities.DownloadState.STATE_DOWNLOADING
import com.tan.domain.data.entities.DownloadState.STATE_NOT_DOWNLOADED
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.PairSongLocalPlaylist
import com.tan.domain.data.entities.SetVideoIdEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.DownloadHandler
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.repository.SharedPlaylistRepository
import com.tan.domain.repository.AccountRepository
import com.tan.domain.data.entities.SharedPlaylistTrack
import kotlinx.coroutines.flow.firstOrNull
import com.tan.domain.utils.FilterState
import com.tan.domain.utils.collectLatestResource
import com.tan.domain.utils.collectResource
import com.tan.domain.utils.toArrayListTrack
import com.tan.domain.utils.toSongEntity
import com.tan.domain.utils.toTrack
import com.tan.logger.Logger
import com.tan.gratify.pagination.PagingActions
import com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail
import com.tan.gratify.ui.theme.md_theme_dark_background
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.inject
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.add_to_queue
import gratify.composeapp.generated.resources.added_to_playlist
import gratify.composeapp.generated.resources.added_to_queue
import gratify.composeapp.generated.resources.added_to_youtube_playlist
import gratify.composeapp.generated.resources.can_t_add_to_youtube_playlist
import gratify.composeapp.generated.resources.can_t_delete_from_youtube_playlist
import gratify.composeapp.generated.resources.delete
import gratify.composeapp.generated.resources.delete_song_from_playlist
import gratify.composeapp.generated.resources.error
import gratify.composeapp.generated.resources.playlist
import gratify.composeapp.generated.resources.playlist_is_empty
import gratify.composeapp.generated.resources.removed_from_YouTube_playlist
import gratify.composeapp.generated.resources.suggest
import gratify.composeapp.generated.resources.synced
import gratify.composeapp.generated.resources.syncing
import gratify.composeapp.generated.resources.unsynced
import gratify.composeapp.generated.resources.unsyncing
import gratify.composeapp.generated.resources.updated
import gratify.composeapp.generated.resources.updated_to_youtube_playlist
import gratify.composeapp.generated.resources.updating

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.repository.SocialRepository

class LocalPlaylistViewModel(
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val sharedPlaylistRepository: SharedPlaylistRepository,
    private val accountRepository: AccountRepository,
    private val socialRepository: SocialRepository,
) : BaseViewModel() {
    private val supabase: SupabaseClient by inject()
    private val converter = Converters()
    private val downloadUtils: DownloadHandler by inject<DownloadHandler>()

    private var _offset: MutableStateFlow<Int> = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset

    fun setOffset(offset: Int) {
        _offset.value = offset
    }

    var loading: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val _uiState: MutableStateFlow<LocalPlaylistState> = MutableStateFlow(LocalPlaylistState.initial())
    val uiState: StateFlow<LocalPlaylistState> get() = _uiState

    private fun setFilter(filterState: FilterState) {
        _uiState.update {
            it.copy(
                filterState = filterState,
            )
        }
    }

    fun setBrush(brush: List<Color>) {
        _uiState.update {
            it.copy(
                colors = brush,
            )
        }
    }

    private var newUpdateJob: Job? = null

    init {
        viewModelScope.launch {
            setFilter(
                when (dataStoreManager.localPlaylistFilter.first()) {
                    DataStoreManager.LOCAL_PLAYLIST_FILTER_OLDER_FIRST -> FilterState.OlderFirst
                    DataStoreManager.LOCAL_PLAYLIST_FILTER_NEWER_FIRST -> FilterState.NewerFirst
                    DataStoreManager.LOCAL_PLAYLIST_FILTER_TITLE -> FilterState.Title
                    DataStoreManager.LOCAL_PLAYLIST_FILTER_CUSTOM_ORDER -> FilterState.CustomOrder
                    else -> FilterState.OlderFirst
                },
            )
            val listTrackStringJob =
                launch {
                    uiState
                        .map { it.downloadState }
                        .distinctUntilChanged()
                        .collectLatest { downloadState ->
                            if (downloadState == STATE_DOWNLOADED || downloadState == STATE_DOWNLOADING) {
                                newUpdateJob?.cancel()
                                newUpdateJob =
                                    launch(Dispatchers.IO) {
                                        localPlaylistRepository
                                            .listTrackFlow(uiState.value.id)
                                            .distinctUntilChanged()
                                            .collectLatest { list ->
                                                delay(500)
                                                val currentList = uiState.value.trackCount
                                                val newList = list.size
                                                log("newList: $list")
                                                log("currentList: $currentList, newList: $newList")
                                                if (newList > currentList) {
                                                    updatePlaylistState(uiState.value.id, refresh = true)
                                                }
                                                delay(500)
                                                val fullTracks = localPlaylistRepository.getFullPlaylistTracks(id = uiState.value.id)
                                                val notDownloadedList =
                                                    fullTracks.filter { it.downloadState != STATE_DOWNLOADED }.map { it.videoId }
                                                if (fullTracks.isEmpty()) {
                                                    updatePlaylistDownloadState(uiState.value.id, STATE_NOT_DOWNLOADED)
                                                } else if (fullTracks.all { it.downloadState == STATE_DOWNLOADED } &&
                                                    downloadState != STATE_DOWNLOADED
                                                ) {
                                                    updatePlaylistDownloadState(uiState.value.id, STATE_DOWNLOADED)
                                                } else if (
                                                    fullTracks.any { it.downloadState == STATE_DOWNLOADING } &&
                                                    notDownloadedList.isNotEmpty() &&
                                                    downloadState != STATE_DOWNLOADING
                                                ) {
                                                    updatePlaylistDownloadState(uiState.value.id, STATE_DOWNLOADING)
                                                } else if (notDownloadedList.isNotEmpty()) {
                                                    updatePlaylistDownloadState(uiState.value.id, STATE_DOWNLOADING)
                                                    downloadTracks(notDownloadedList)
                                                }
                                            }
                                    }
                            }
                        }
                }
            val resetSuggestions =
                launch {
                    uiState
                        .map {
                            it.id
                        }.distinctUntilChanged()
                        .collectLatest {
                            _uiState.update {
                                it.copy(
                                    suggestions = null,
                                )
                            }
                        }
                }
            listTrackStringJob.join()
            resetSuggestions.join()
        }
    }

    private val _tracksPagingState: MutableStateFlow<PagingData<Pair<SongEntity, PairSongLocalPlaylist>>> =
        MutableStateFlow(
            PagingData.empty(),
        )
    val tracksPagingState: StateFlow<PagingData<Pair<SongEntity, PairSongLocalPlaylist>>> get() = _tracksPagingState
    private val lazyTrackPagingItems: MutableStateFlow<LazyPagingItems<Pair<SongEntity, PairSongLocalPlaylist>>?> = MutableStateFlow(null)

    fun setLazyTrackPagingItems(lazyPagingItems: LazyPagingItems<Pair<SongEntity, PairSongLocalPlaylist>>) {
        lazyTrackPagingItems.value = lazyPagingItems
        Logger.d(tag, "setLazyTrackPagingItems: ${lazyTrackPagingItems.value?.itemCount}")
    }

    private val modifications = MutableStateFlow<List<PagingActions<Pair<SongEntity, PairSongLocalPlaylist>>>>(emptyList())

    private fun getTracksPagingState(
        id: Long,
        filterState: FilterState,
    ) {
        viewModelScope.launch {
            Logger.w("LocalPlaylistViewModel", "getTracksPagingState: ${uiState.value}")
            modifications.value = listOf()
            localPlaylistRepository
                .getTracksPaging(
                    id,
                    filterState,
                ).distinctUntilChanged()
                .cachedIn(viewModelScope)
                .combine(modifications) { pagingData, modifications ->
                    modifications.fold(pagingData) { data, actions ->
                        applyActions(data, actions)
                    }
                }.collect {
                    _tracksPagingState.value = it
                }
        }
    }

    private fun applyActions(
        pagingData: PagingData<Pair<SongEntity, PairSongLocalPlaylist>>,
        actions: PagingActions<Pair<SongEntity, PairSongLocalPlaylist>>,
    ): PagingData<Pair<SongEntity, PairSongLocalPlaylist>> =
        when (actions) {
            is PagingActions.Insert -> {
                val loadState = lazyTrackPagingItems.value?.loadState
                val list =
                    lazyTrackPagingItems.value
                        ?.itemSnapshotList
                        ?.toList()
                        ?.filterNotNull()
                        ?.map { it.first.videoId }
                        ?: emptyList()
                Logger.w(tag, "applyActions: $loadState")
                if (loadState?.refresh is LoadState.NotLoading &&
                    loadState.append is LoadState.NotLoading &&
                    !list.contains(actions.item.first.videoId)
                ) {
                    pagingData
                        .insertFooterItem(item = actions.item)
                } else {
                    pagingData
                }
            }

            is PagingActions.Remove -> {
                pagingData.filter {
                    actions.item.first.videoId != it.first.videoId
                }
            }
        }

    private fun onApplyActions(actions: PagingActions<Pair<SongEntity, PairSongLocalPlaylist>>) {
        modifications.value += actions
    }

    fun getSuggestions(playlistId: Long) {
        loading.value = true
        viewModelScope.launch {
            localPlaylistRepository.getSuggestionsTrackForPlaylist(playlistId).collectLatestResource(
                onSuccess = { res ->
                    val reloadParams = res?.first
                    val songs = res?.second
                    if (reloadParams != null && songs != null) {
                        _uiState.update {
                            it.copy(
                                suggestions =
                                    LocalPlaylistState.SuggestionSongs(
                                        reloadParams = reloadParams,
                                        songs = songs,
                                    ),
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                suggestions = null,
                            )
                        }
                    }
                    loading.value = false
                },
                onError = { e ->
                    makeToast(e)
                    loading.value = false
                    _uiState.update {
                        it.copy(
                            suggestions = null,
                        )
                    }
                },
            )
        }
    }

    fun reloadSuggestion() {
        loading.value = true
        viewModelScope.launch {
            val param = uiState.value.suggestions?.reloadParams
            if (param != null) {
                localPlaylistRepository.reloadSuggestionPlaylist(param).collectLatestResource(
                    onSuccess = { res ->
                        val reloadParams = res?.first
                        val songs = res?.second
                        if (reloadParams != null && songs != null) {
                            _uiState.update {
                                it.copy(
                                    suggestions =
                                        LocalPlaylistState.SuggestionSongs(
                                            reloadParams = reloadParams,
                                            songs = songs,
                                        ),
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    suggestions = null,
                                )
                            }
                        }
                        loading.value = false
                    },
                    onError = {
                        _uiState.update { it.copy(suggestions = null) }
                        makeToast(getString(Res.string.error))
                        loading.value = false
                    },
                )
            }
        }
    }

    fun updatePlaylistDownloadState(
        id: Long,
        state: Int,
    ) {
        viewModelScope.launch {
            localPlaylistRepository
                .updateDownloadState(
                    id,
                    state,
                    getString(Res.string.updated),
                ).collectLatestResource(
                    onSuccess = { mess ->
                        Logger.d(tag, "updatePlaylistDownloadState: $mess")
                        _uiState.update {
                            it.copy(
                                downloadState = state,
                            )
                        }
                    },
                )
        }
    }

    val listJob: MutableStateFlow<ArrayList<SongEntity>> = MutableStateFlow(arrayListOf())

//        var downloadState: StateFlow<List<Download?>>
//        viewModelScope.launch {
//            downloadState = downloadUtils.getAllDownloads().stateIn(viewModelScope)
//            downloadState.collectLatest { down ->
//                if (down.isNotEmpty()){
//                    var count = 0
//                    down.forEach { downloadItem ->
//                        if (downloadItem?.state == Download.STATE_COMPLETED) {
//                            count++
//                        }
//                        else if (downloadItem?.state == Download.STATE_FAILED) {
//                            updatePlaylistDownloadState(id, DownloadState.STATE_DOWNLOADING)
//                        }
//                    }
//                    if (count == down.size) {
//                        mainRepository.getLocalPlaylist(id).collect{ playlist ->
//                            mainRepository.getSongsByListVideoId(playlist.tracks!!).collect{ tracks ->
//                                tracks.forEach { track ->
//                                    if (track.downloadState != DownloadState.STATE_DOWNLOADED) {
//                                        mainRepository.updateDownloadState(track.videoId, DownloadState.STATE_NOT_DOWNLOADED)
//                                        Toast.makeText(getApplication(), "Download Failed", Toast.LENGTH_SHORT).show()
//                                    }
//                                }
//                            }
//                        }
//                        Logger.d("Check Downloaded", "Downloaded")
//                        updatePlaylistDownloadState(id, DownloadState.STATE_DOWNLOADED)
//                        Toast.makeText(getApplication(), "Download Completed", Toast.LENGTH_SHORT).show()
//                    }
//                    else {
//                        updatePlaylistDownloadState(id, DownloadState.STATE_DOWNLOADING)
//                    }
//                }
//                else {
//                    updatePlaylistDownloadState(id, DownloadState.STATE_NOT_DOWNLOADED)
//                }
//            }
//        }
//    }

    fun updatePlaylistTitle(
        title: String,
        id: Long,
    ) {
        viewModelScope.launch {
            showLoadingDialog(message = getString(Res.string.updating))
            localPlaylistRepository
                .updateTitleLocalPlaylist(
                    id,
                    title,
                    getString(Res.string.updated),
                    getString(Res.string.updated_to_youtube_playlist),
                    getString(Res.string.error),
                ).collectResource(
                    onSuccess = {
                        makeToast(it)
                        updatePlaylistState(id)
                        hideLoadingDialog()
                    },
                    onError = {
                        makeToast(it)
                        hideLoadingDialog()
                    },
                )
        }
    }

    fun deletePlaylist(id: Long, onDeleted: () -> Unit = {}) {
        val initialTitle = uiState.value.title
        viewModelScope.launch(Dispatchers.IO) {
            val dbEntity = localPlaylistRepository.getLocalPlaylist(id).firstOrNull()?.data
            val currentTitle = dbEntity?.title ?: initialTitle
            val sourceSharedId = dbEntity?.sourceSharedPlaylistId

            // Delete cloud and shared playlists FIRST before local delete
            try {
                val user = supabase.auth.currentUserOrNull()
                val userId = user?.id?.toString()
                if (userId != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        if (sourceSharedId != null) {
                            // Playlist ini hasil menambahkan playlist publik orang lain:
                            // turunkan hitungan "ditambahkan X kali" (unik per user).
                            sharedPlaylistRepository.removePlaylistSave(sourceSharedId, userId)
                        } else {
                            socialRepository.deleteCloudPlaylist(id, userId, currentTitle).collect {}
                            if (currentTitle.isNotBlank()) {
                                sharedPlaylistRepository.deleteSharedPlaylistByLocalId(
                                    userId = userId,
                                    localPlaylistId = id,
                                    fallbackTitle = currentTitle
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            localPlaylistRepository
                .deleteLocalPlaylist(id, getString(Res.string.delete))
                .collectLatestResource(
                    onSuccess = {
                        viewModelScope.launch(Dispatchers.Main) {
                            makeToast(it)
                            onDeleted()
                        }
                    },
                    onError = {
                        viewModelScope.launch(Dispatchers.Main) {
                            makeToast(it)
                        }
                    },
                )
        }
    }

    fun updatePlaylistThumbnail(
        uri: String,
        id: Long,
    ) {
        showLoadingDialog(message = getString(Res.string.updating))
        viewModelScope.launch {
            localPlaylistRepository
                .updateThumbnailLocalPlaylist(id, uri, getString(Res.string.updated))
                .collectResource(
                    onSuccess = {
                        makeToast(it)
                        updatePlaylistState(id)
                        hideLoadingDialog()
                        viewModelScope.launch {
                            try {
                                val user = supabase.auth.currentUserOrNull()
                                val userId = user?.id?.toString()
                                if (userId != null) {
                                    socialRepository.syncUpPlaylist(id, userId, isPublic = null).collect {}
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onError = {
                        makeToast(it)
                        hideLoadingDialog()
                    },
                )
        }
    }

    fun deleteItem(
        id: Long,
        song: SongEntity,
    ) {
        viewModelScope.launch {
            val pair = localPlaylistRepository.getPlaylistPairOfSong(id, song.videoId).lastOrNull() ?: return@launch
            localPlaylistRepository
                .removeTrackFromLocalPlaylist(
                    id,
                    song,
                    getString(Res.string.delete_song_from_playlist),
                    getString(Res.string.removed_from_YouTube_playlist),
                    getString(Res.string.can_t_delete_from_youtube_playlist),
                ).collectLatestResource(
                    onSuccess = {
                        makeToast(it)
                        onApplyActions(PagingActions.Remove(song to pair))
                        updatePlaylistState(id)
                        syncToCloudIfNeeded(id)
                    },
                    onError = {
                        makeToast(it)
                    },
                )
        }
    }

    fun downloadFullPlaylistState(
        id: Long,
        listJob: List<String>,
    ) {
        viewModelScope.launch {
            downloadUtils.downloadTask.collect { download ->
                _uiState.update { ui ->
                    ui.copy(
                        downloadState =
                            if (listJob.all { download[it] == STATE_DOWNLOADED }) {
                                localPlaylistRepository.updateLocalPlaylistDownloadState(
                                    STATE_DOWNLOADED,
                                    id,
                                )
                                STATE_DOWNLOADED
                            } else if (listJob.any { download[it] == STATE_DOWNLOADING }) {
                                localPlaylistRepository.updateLocalPlaylistDownloadState(
                                    STATE_DOWNLOADING,
                                    id,
                                )
                                STATE_DOWNLOADING
                            } else {
                                localPlaylistRepository.updateLocalPlaylistDownloadState(
                                    STATE_NOT_DOWNLOADED,
                                    id,
                                )
                                STATE_NOT_DOWNLOADED
                            },
                    )
                }
            }
        }
    }

    private var _listSetVideoId: MutableStateFlow<List<SetVideoIdEntity>?> =
        MutableStateFlow(null)
    val listSetVideoId: StateFlow<List<SetVideoIdEntity>?> = _listSetVideoId

    fun getSetVideoId(youtubePlaylistId: String) {
        viewModelScope.launch {
            localPlaylistRepository.getYouTubeSetVideoId(youtubePlaylistId).collect {
                _listSetVideoId.value = it
            }
        }
    }

    fun syncPlaylistWithYouTubePlaylist(id: Long) {
        makeToast(getString(Res.string.syncing))
        showLoadingDialog(message = getString(Res.string.syncing))
        viewModelScope.launch {
            localPlaylistRepository
                .syncLocalPlaylistToYouTubePlaylist(id, getString(Res.string.synced), getString(Res.string.error))
                .collectLatestResource(
                    onSuccess = { ytId ->
                        _uiState.update {
                            it.copy(
                                syncState = LocalPlaylistEntity.YouTubeSyncState.Synced,
                                ytPlaylistId = ytId,
                            )
                        }
                        makeToast(getString(Res.string.synced))
                        hideLoadingDialog()
                    },
                    onError = {
                        makeToast(it)
                        updateLocalPlaylistSyncState(id, LocalPlaylistEntity.YouTubeSyncState.NotSynced)
                        hideLoadingDialog()
                    },
                )
//            mainRepository.createYouTubePlaylist(playlist).collect {
//                if (it != null) {
//                    val ytId = "VL$it"
//                    mainRepository.updateLocalPlaylistYouTubePlaylistId(playlist.id, ytId)
//                    mainRepository.updateLocalPlaylistYouTubePlaylistSynced(playlist.id, 1)
//                    mainRepository.getLocalPlaylistByYoutubePlaylistId(ytId).collect { yt ->
//                        if (yt != null) {
//                            mainRepository.updateLocalPlaylistYouTubePlaylistSyncState(
//                                yt.id,
//                                LocalPlaylistEntity.YouTubeSyncState.Synced,
//                            )
//                            mainRepository.getLocalPlaylist(playlist.id).collect { last ->
//                                _localPlaylist.emit(last)
//                                Toast
//                                    .makeText(
//                                        application,
//                                        application.getString(Res.string.synced),
//                                        Toast.LENGTH_SHORT,
//                                    ).show()
//                            }
//                        }
//                    }
//                } else {
//                    Toast
//                        .makeText(
//                            application,
//                            application.getString(Res.string.error),
//                            Toast.LENGTH_SHORT,
//                        ).show()
//                }
//            }
        }
    }
    
    fun sharePlaylistToPublic() {
        showLoadingDialog(message = "Membagikan Playlist...") // Assuming Indonesian string, can be Res.string
        viewModelScope.launch {
            try {
                // Get current user id
                val user = supabase.auth.currentUserOrNull()
                val userId = user?.id?.toString()
                if (userId == null) {
                    makeToast("Anda harus login untuk membagikan playlist.")
                    hideLoadingDialog()
                    return@launch
                }
                
                // Get creator name
                val creatorName = dataStoreManager.getString("AppProfileName").first() 
                    ?: dataStoreManager.getString("AccountName").first() 
                    ?: "Anonim"
                
                // Get all tracks from local playlist
                val fullTracks = localPlaylistRepository.getFullPlaylistTracks(id = uiState.value.id)
                val sharedTracks = fullTracks.map { track ->
                    SharedPlaylistTrack(
                        playlistId = "", // Assigned in repository
                        videoId = track.videoId,
                        title = track.title,
                        artists = track.artistName?.joinToString(", ") ?: "Unknown Artist",
                        durationSeconds = track.durationSeconds
                    )
                }
                
                val result = sharedPlaylistRepository.sharePlaylist(
                    userId = userId,
                    localPlaylistId = uiState.value.id,
                    title = uiState.value.title,
                    creatorName = creatorName,
                    thumbnailUrl = if (isAutoAssignedOrNullThumbnail(uiState.value.thumbnail, uiState.value.ytPlaylistId)) {
                        uiState.value.top4Tracks.firstOrNull()?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
                    } else {
                        uiState.value.thumbnail
                    },
                    tracks = sharedTracks
                )
                
                // Sekalian sync ke cloud_playlists untuk backup cloud & profil mutualan
                socialRepository.syncUpPlaylist(uiState.value.id, userId, isPublic = true).collect { syncResult ->
                    Logger.i(tag, "Backup ke cloud_playlists: ${syncResult.isSuccess}")
                }
                
                if (result.isSuccess) {
                    _uiState.update { it.copy(isPublic = true) }
                    makeToast("Berhasil dibagikan ke Publik!")
                } else {
                    makeToast("Gagal membagikan playlist: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                makeToast("Terjadi kesalahan")
            } finally {
                hideLoadingDialog()
            }
        }
    }

    fun unsharePlaylistFromPublic() {
        showLoadingDialog(message = "Menyembunyikan dari Publik...")
        viewModelScope.launch {
            try {
                val user = supabase.auth.currentUserOrNull()
                val userId = user?.id?.toString()
                if (userId == null) {
                    makeToast("Anda harus login.")
                    hideLoadingDialog()
                    return@launch
                }

                socialRepository.unshareOrHideCloudPlaylist(uiState.value.id, userId).collect {}

                try {
                    sharedPlaylistRepository.deleteSharedPlaylistByLocalId(
                        userId = userId,
                        localPlaylistId = uiState.value.id,
                        fallbackTitle = uiState.value.title
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                _uiState.update { it.copy(isPublic = false) }
                makeToast("Playlist berhasil disembunyikan dari publik.")
            } catch (e: Exception) {
                e.printStackTrace()
                makeToast("Terjadi kesalahan")
            } finally {
                hideLoadingDialog()
            }
        }
    }

    fun backupPlaylistToCloud(isPublic: Boolean = true) {
        showLoadingDialog(message = "Menyimpan ke Cloud...")
        viewModelScope.launch {
            try {
                val user = supabase.auth.currentUserOrNull()
                val userId = user?.id?.toString()
                if (userId == null) {
                    makeToast("Anda harus login untuk mem-backup playlist.")
                    hideLoadingDialog()
                    return@launch
                }
                socialRepository.syncUpPlaylist(uiState.value.id, userId, isPublic)
                    .collect { result ->
                        if (result.isSuccess) {
                            makeToast("Berhasil di-backup ke Cloud!")
                        } else {
                            makeToast("Gagal backup ke Cloud: ${result.exceptionOrNull()?.message}")
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                makeToast("Terjadi kesalahan")
            } finally {
                hideLoadingDialog()
            }
        }
    }

    private fun syncToCloudIfNeeded(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val cloudLists = supabase.postgrest["cloud_playlists"]
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("local_playlist_id", playlistId)
                        }
                    }.decodeList<CloudPlaylistDto>()
                if (cloudLists.isNotEmpty()) {
                    socialRepository.syncUpPlaylist(playlistId, userId, isPublic = null).collect {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateLocalPlaylistSyncState(
        id: Long,
        syncState: Int,
        ytId: String? = null,
    ) {
        showLoadingDialog()
        viewModelScope.launch {
            localPlaylistRepository
                .updateSyncState(id, syncState, getString(Res.string.synced))
                .collectLatestResource(
                    onSuccess = { mess ->
                        makeToast(mess)
                        _uiState.update {
                            it.copy(
                                syncState = syncState,
                            )
                        }
                        hideLoadingDialog()
                    },
                    onError = {
                        makeToast(it)
                        hideLoadingDialog()
                    },
                )
            if (ytId != null) {
                localPlaylistRepository
                    .updateYouTubePlaylistId(id, ytId, getString(Res.string.updated))
                    .collectLatestResource(
                        onSuccess = { mess ->
                            Logger.d(tag, "updateLocalPlaylistSyncState: $mess")
                            _uiState.update {
                                it.copy(
                                    ytPlaylistId = ytId,
                                )
                            }
                        },
                    )
            }
        }
    }

    fun unsyncPlaylistWithYouTubePlaylist(id: Long) {
        makeToast(getString(Res.string.unsyncing))
        showLoadingDialog(message = getString(Res.string.unsyncing))
        viewModelScope.launch {
            localPlaylistRepository
                .unsyncLocalPlaylist(id, getString(Res.string.unsynced))
                .collectLatestResource(
                    onSuccess = { mess ->
                        makeToast(mess)
                        _uiState.update {
                            it.copy(
                                syncState = LocalPlaylistEntity.YouTubeSyncState.NotSynced,
                                ytPlaylistId = null,
                            )
                        }
                        hideLoadingDialog()
                    },
                    onError = {
                        makeToast(it)
                        hideLoadingDialog()
                    },
                )
        }
    }

    fun updateListTrackSynced(id: Long) {
        makeToast(getString(Res.string.syncing))
        showLoadingDialog(message = getString(Res.string.syncing))
        viewModelScope.launch {
            localPlaylistRepository.updateListTrackSynced(id).collectLatest { done ->
                if (done) {
                    makeToast(getString(Res.string.updated))
                    updatePlaylistState(id, refresh = true)
                }
                hideLoadingDialog()
            }
        }
    }

    fun removeListSuggestion() {
        _uiState.update { it.copy(suggestions = null) }
    }

    fun addSuggestTrackToListTrack(track: Track) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    suggestions =
                        state.suggestions?.copy(
                            songs = state.suggestions.songs.filter { it.videoId != track.videoId },
                        ),
                )
            }
            _uiState.value.id.let { id ->
                localPlaylistRepository
                    .addTrackToLocalPlaylist(
                        id,
                        track.toSongEntity(),
                        getString(Res.string.added_to_playlist),
                        getString(Res.string.added_to_youtube_playlist),
                        getString(Res.string.can_t_add_to_youtube_playlist),
                    ).collectLatestResource(
                        onSuccess = {
                            makeToast(it)
                            viewModelScope.launch {
                                localPlaylistRepository.getPlaylistPairOfSong(id, track.videoId).lastOrNull()?.let { pair ->
                                    onApplyActions(PagingActions.Insert(track.toSongEntity() to pair))
                                }
                            }
                            syncToCloudIfNeeded(id)
                        },
                        onError = {
                            makeToast(it)
                        },
                    )
            }
        }
    }

    fun onUIEvent(ev: LocalPlaylistUIEvent) {
        when (ev) {
            is LocalPlaylistUIEvent.ChangeFilter -> {
                viewModelScope.launch {
                    dataStoreManager.setLocalPlaylistFilter(
                        when (ev.filterState) {
                            FilterState.OlderFirst -> DataStoreManager.LOCAL_PLAYLIST_FILTER_OLDER_FIRST
                            FilterState.NewerFirst -> DataStoreManager.LOCAL_PLAYLIST_FILTER_NEWER_FIRST
                            FilterState.Title -> DataStoreManager.LOCAL_PLAYLIST_FILTER_TITLE
                            FilterState.CustomOrder -> DataStoreManager.LOCAL_PLAYLIST_FILTER_CUSTOM_ORDER
                        },
                    )
                }
                setFilter(ev.filterState)
                Logger.w("PlaylistScreen", "new filterState: ${ev.filterState}")
                getTracksPagingState(uiState.value.id, ev.filterState)
            }

            is LocalPlaylistUIEvent.ItemClick -> {
                val loadedList = lazyTrackPagingItems.value?.itemSnapshotList?.toList() ?: return
                val clickedSong = loadedList.find { it?.first?.videoId == ev.videoId }?.first ?: return
                val index = loadedList.indexOfFirst { it?.first?.videoId == ev.videoId }

                setQueueData(
                    QueueData.Data(
                        listTracks = loadedList.mapNotNull { it?.first }.toArrayListTrack(),
                        firstPlayedTrack = clickedSong.toTrack(),
                        playlistId = LOCAL_PLAYLIST_ID + uiState.value.id,
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${uiState.value.title}\"",
                        playlistType = PlaylistType.LOCAL_PLAYLIST,
                        continuation =
                            if (offset.value > 0) {
                                when (uiState.value.filterState) {
                                    FilterState.OlderFirst -> {
                                        ASC + converter.dateToTimestamp(loadedList.lastOrNull()?.second?.inPlaylist)
                                    }

                                    FilterState.NewerFirst -> {
                                        DESC + converter.dateToTimestamp(loadedList.lastOrNull()?.second?.inPlaylist)
                                    }

                                    FilterState.CustomOrder -> {
                                        CUSTOM_ORDER + offset.value.toString()
                                    }

                                    FilterState.Title -> {
                                        TITLE + offset.value.toString()
                                    }
                                }
                            } else {
                                null
                            },
                    ),
                )
                loadMediaItem(
                    clickedSong,
                    Config.PLAYLIST_CLICK,
                    index,
                )
            }

            is LocalPlaylistUIEvent.SuggestionsItemClick -> {
                val suggestionsList = uiState.value.suggestions?.songs ?: return
                val clickedSong = suggestionsList.find { it.videoId == ev.videoId } ?: return

                setQueueData(
                    QueueData.Data(
                        listTracks = suggestionsList.toCollection(ArrayList()),
                        firstPlayedTrack = clickedSong,
                        playlistId = "RDAMVM${clickedSong.videoId}",
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${uiState.value.title}\" ${
                            getString(Res.string.suggest)
                        }",
                        playlistType = PlaylistType.RADIO,
                        continuation = null,
                    ),
                )
                loadMediaItem(
                    clickedSong,
                    Config.SONG_CLICK,
                )
            }

            is LocalPlaylistUIEvent.PlayClick -> {
                val loadedList =
                    lazyTrackPagingItems.value?.itemSnapshotList?.toList() ?: run {
                        makeToast(getString(Res.string.playlist_is_empty))
                        return
                    }

                val firstPlayTrack = loadedList.firstOrNull()?.first?.toTrack()
                setQueueData(
                    QueueData.Data(
                        listTracks = loadedList.mapNotNull { it?.first }.toArrayListTrack(),
                        firstPlayedTrack = firstPlayTrack,
                        playlistId = LOCAL_PLAYLIST_ID + uiState.value.id,
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${uiState.value.title}\"",
                        playlistType = PlaylistType.LOCAL_PLAYLIST,
                        continuation =
                            if (offset.value > 0) {
                                if (offset.value > 0) {
                                    when (uiState.value.filterState) {
                                        FilterState.OlderFirst -> {
                                            ASC + converter.dateToTimestamp(loadedList.lastOrNull()?.second?.inPlaylist)
                                        }

                                        FilterState.NewerFirst -> {
                                            DESC + converter.dateToTimestamp(loadedList.lastOrNull()?.second?.inPlaylist)
                                        }

                                        FilterState.CustomOrder -> {
                                            CUSTOM_ORDER + offset.value.toString()
                                        }

                                        FilterState.Title -> {
                                            TITLE + offset.value.toString()
                                        }
                                    }
                                } else {
                                    null
                                }
                            } else {
                                null
                            },
                    ),
                )
                loadMediaItem(
                    firstPlayTrack,
                    Config.PLAYLIST_CLICK,
                    0,
                )
            }

            is LocalPlaylistUIEvent.ShuffleClick -> {
                viewModelScope.launch {
                    val listVideoId = localPlaylistRepository.getListTrackVideoId(uiState.value.id)
                    log("ShuffleClick: uiState id ${uiState.value.id}")
                    log("ShuffleClick: $listVideoId")
                    if (listVideoId.isEmpty()) {
                        makeToast(getString(Res.string.playlist_is_empty))
                        return@launch
                    }
                    val random = listVideoId.random()
                    val randomIndex = listVideoId.indexOf(random)
                    val firstPlayedTrack = songRepository.getSongById(random).singleOrNull()?.toTrack() ?: return@launch
                    setQueueData(
                        QueueData.Data(
                            listTracks = arrayListOf(firstPlayedTrack),
                            firstPlayedTrack = firstPlayedTrack,
                            playlistId = LOCAL_PLAYLIST_ID + uiState.value.id,
                            playlistName = "${
                                getString(
                                    Res.string.playlist,
                                )
                            } \"${uiState.value.title}\"",
                            playlistType = PlaylistType.LOCAL_PLAYLIST,
                            continuation = "",
                        ),
                    )
                    shufflePlaylist(randomIndex)
                }
            }
        }
    }

    fun updatePlaylistState(
        id: Long,
        refresh: Boolean = false,
    ) {
        viewModelScope.launch {
            localPlaylistRepository.getLocalPlaylist(id).collectLatestResource(
                onSuccess = { pl ->
                    if (pl != null) {
                        _uiState.update {
                            it.copy(
                                id = pl.id,
                                title = pl.title,
                                thumbnail = pl.thumbnail,
                                inLibrary = pl.inLibrary,
                                downloadState = pl.downloadState,
                                syncState = pl.syncState,
                                ytPlaylistId = pl.youtubePlaylistId,
                                trackCount = pl.tracks?.size ?: 0,
                                top4Tracks = pl.tracks?.take(4) ?: emptyList(),
                                creatorName = pl.creatorName,
                            )
                        }

                        val readOnly = pl.sourceSharedPlaylistId != null
                        val isUnavailable = pl.sourceAvailability == com.tan.domain.data.entities.LocalPlaylistEntity.SourceAvailability.UNAVAILABLE
                        _uiState.update { it.copy(isReadOnly = readOnly, isUnavailable = isUnavailable) }

                        if (readOnly && !isUnavailable && pl.sourceSharedPlaylistId != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val result = supabase.postgrest["shared_playlists"]
                                        .select { filter { eq("id", pl.sourceSharedPlaylistId!!) } }
                                        .decodeList<com.tan.domain.data.entities.SharedPlaylist>()
                                    if (result.isEmpty()) {
                                        localPlaylistRepository.updateSourceAvailability(
                                            pl.sourceSharedPlaylistId!!,
                                            com.tan.domain.data.entities.LocalPlaylistEntity.SourceAvailability.UNAVAILABLE
                                        )
                                        _uiState.update { it.copy(isUnavailable = true) }
                                    } else {
                                        // Perbarui hitungan "ditambahkan X kali" dari playlist sumber.
                                        val source = result.first()
                                        _uiState.update {
                                            it.copy(
                                                addCount = source.addCount,
                                                creatorName = it.creatorName ?: source.creatorName,
                                            )
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        if (refresh) {
                            getTracksPagingState(id, _uiState.value.filterState)
                        }
                        val currentUserId = supabase.auth.currentUserOrNull()?.id
                        if (currentUserId != null && pl.id > 0) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val cloudLists = supabase.postgrest["cloud_playlists"]
                                        .select {
                                            filter {
                                                eq("user_id", currentUserId)
                                                eq("local_playlist_id", pl.id)
                                            }
                                        }.decodeList<CloudPlaylistDto>()
                                    val isPub = if (cloudLists.isEmpty()) false else cloudLists.any { it.isPublic }
                                    _uiState.update { it.copy(isPublic = isPub) }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    private fun downloadTracks(listJob: List<String>) {
        viewModelScope.launch {
            listJob.forEach { videoId ->
                songRepository.getSongById(videoId).singleOrNull()?.let { song ->
                    if (song.downloadState != STATE_DOWNLOADED) {
                        downloadUtils.downloadTrack(videoId, song.title, song.thumbnails ?: "")
                    }
                }
            }
        }
    }

    fun downloadFullPlaylist() {
        viewModelScope.launch {
            val fullTracks = localPlaylistRepository.getFullPlaylistTracks(id = uiState.value.id)
            val listJob = fullTracks.filter { it.downloadState != STATE_DOWNLOADED }.map { it.videoId }
            if (listJob.isNotEmpty()) {
                downloadTracks(listJob)
                downloadFullPlaylistState(uiState.value.id, listJob)
            } else if (fullTracks.isNotEmpty() && fullTracks.all { it.downloadState == STATE_DOWNLOADED }) {
                updatePlaylistDownloadState(uiState.value.id, STATE_DOWNLOADED)
            } else {
                makeToast(getString(Res.string.playlist_is_empty))
            }
        }
    }

    fun addAllToQueue() {
        viewModelScope.launch {
            showLoadingDialog(getString(Res.string.add_to_queue))
            val fullTracks = localPlaylistRepository.getFullPlaylistTracks(id = uiState.value.id)
            if (fullTracks.isNotEmpty()) {
                mediaPlayerHandler.loadMoreCatalog(fullTracks.toArrayListTrack(), true)
                makeToast(getString(Res.string.added_to_queue))
                hideLoadingDialog()
            } else {
                makeToast(getString(Res.string.playlist_is_empty))
                hideLoadingDialog()
            }
        }
    }

    suspend fun changeLocalPlaylistItemPosition(
        from: Int,
        to: Int,
    ) {
        val isSynced =
            uiState.value.syncState == LocalPlaylistEntity.YouTubeSyncState.Synced
        if (isSynced) {
            // Synced playlist: show loading dialog to block user interaction during API call
            showLoadingDialog(message = getString(Res.string.updating))
            localPlaylistRepository
                .moveItemInSyncedPlaylist(
                    playlistId = uiState.value.id,
                    fromIndex = from,
                    toIndex = to,
                ).collectResource(
                    onLoading = {
                        log("changeLocalPlaylistItemPosition (synced): loading")
                    },
                    onSuccess = {
                        log("changeLocalPlaylistItemPosition (synced): success $it")
                        hideLoadingDialog()
                    },
                    onError = { message ->
                        log("changeLocalPlaylistItemPosition (synced): error $message")
                        makeToast(message ?: getString(Res.string.error))
                        hideLoadingDialog()
                    },
                )
        } else {
            // Unsynced playlist: local only, no loading dialog needed
            val loadedList =
                lazyTrackPagingItems.value?.itemSnapshotList?.toList() ?: return
            val fromItem = loadedList.getOrNull(from)?.first ?: return
            val toItem = loadedList.getOrNull(to)?.first ?: return
            val fromPosition = loadedList.getOrNull(from)?.second?.position ?: from
            val toPosition = loadedList.getOrNull(to)?.second?.position ?: to
            val playlistId = uiState.value.id

            localPlaylistRepository
                .changePositionOfSongInPlaylist(playlistId, fromItem.videoId, toPosition)
                .lastOrNull()
                ?.let { log("changeLocalPlaylistItemPosition: from $it") }
            localPlaylistRepository
                .changePositionOfSongInPlaylist(playlistId, toItem.videoId, fromPosition)
                .lastOrNull()
                ?.let { log("changeLocalPlaylistItemPosition: to $it") }
            syncToCloudIfNeeded(playlistId)
        }
    }
}

sealed class LocalPlaylistUIEvent {
    data class ChangeFilter(
        val filterState: FilterState,
    ) : LocalPlaylistUIEvent()

    data class ItemClick(
        val videoId: String,
    ) : LocalPlaylistUIEvent()

    data class SuggestionsItemClick(
        val videoId: String,
    ) : LocalPlaylistUIEvent()

    data object PlayClick : LocalPlaylistUIEvent()

    data object ShuffleClick : LocalPlaylistUIEvent()
}

data class LocalPlaylistState(
    val id: Long,
    val title: String,
    val thumbnail: String? = null,
    val colors: List<Color> =
        listOf(
            Color.Black,
            md_theme_dark_background,
        ),
    val inLibrary: LocalDateTime? = null,
    val downloadState: Int = DownloadState.STATE_NOT_DOWNLOADED,
    val syncState: Int = LocalPlaylistEntity.YouTubeSyncState.NotSynced,
    val ytPlaylistId: String? = null,
    val trackCount: Int = 0,
    val top4Tracks: List<String> = emptyList(),
    val page: Int = 0,
    val isLoadedFull: Boolean = false,
    val loadState: PlaylistLoadState = PlaylistLoadState.Loading,
    val filterState: FilterState = FilterState.OlderFirst,
    val suggestions: SuggestionSongs? = null,
    val isPublic: Boolean = false,
    val isReadOnly: Boolean = false,
    val isUnavailable: Boolean = false,
    // Watermark: nama pembuat asli bila playlist ini ditambahkan dari playlist publik orang lain.
    val creatorName: String? = null,
    // Jumlah "ditambahkan X kali" dari playlist sumber (0 = sembunyikan).
    val addCount: Int = 0,
) {
    sealed class SuggestionState {
        data object Loading : SuggestionState()

        data object Error : SuggestionState()

        data class Success(
            val suggestSongs: SuggestionSongs,
        ) : SuggestionState()
    }

    sealed class PlaylistLoadState {
        data object Loading : PlaylistLoadState()

        data object Error : PlaylistLoadState()

        data object Success : PlaylistLoadState()
    }

    data class SuggestionSongs(
        val reloadParams: String,
        val songs: List<Track>,
    )

    companion object {
        fun initial(): LocalPlaylistState =
            LocalPlaylistState(
                id = 0,
                title = "",
                thumbnail = null,
                inLibrary = null,
                downloadState = 0,
                syncState = 0,
                trackCount = 0,
            )
    }
}