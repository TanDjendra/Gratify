package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import com.eygraber.uri.Uri
import com.tan.common.Config
import com.tan.common.QUALITY
import com.tan.common.SELECTED_LANGUAGE
import com.tan.common.VIDEO_QUALITY
import com.tan.domain.data.entities.DownloadState
import com.tan.domain.data.entities.GoogleAccountEntity
import com.tan.domain.extension.toNetScapeString
import com.tan.domain.manager.lyricsAnimationsEnabled
import com.tan.domain.manager.setLyricsAnimationsEnabled
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.DownloadHandler
import com.tan.domain.repository.AccountRepository
import com.tan.domain.repository.CacheRepository
import com.tan.domain.repository.CommonRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.utils.LocalResource
import com.tan.logger.LogLevel
import com.tan.logger.Logger
import com.tan.gratify.Platform
import com.tan.gratify.getPlatform
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.inject
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.backup_create_failed
import gratify.composeapp.generated.resources.backup_create_success
import gratify.composeapp.generated.resources.backup_in_progress
import gratify.composeapp.generated.resources.clear_canvas_cache
import gratify.composeapp.generated.resources.clear_downloaded_cache
import gratify.composeapp.generated.resources.clear_player_cache
import gratify.composeapp.generated.resources.clear_thumbnail_cache
import gratify.composeapp.generated.resources.restore_failed
import gratify.composeapp.generated.resources.restore_in_progress

/** Batas tunggu backup saat logout, supaya tombol Logout tidak terasa menggantung. */
private const val LOGOUT_SYNC_TIMEOUT_MS = 30_000L

class SettingsViewModel(
    private val dataStoreManager: DataStoreManager,
    private val commonRepository: CommonRepository,
    private val songRepository: SongRepository,
    private val accountRepository: AccountRepository,
    private val cacheRepository: CacheRepository,
) : BaseViewModel() {
    private val _settingsWriteFailed = MutableStateFlow(false)
    val settingsWriteFailed: StateFlow<Boolean> = _settingsWriteFailed
    private fun changePreference(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            _settingsWriteFailed.value = false
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _settingsWriteFailed.value = true }
        }
    }
    private val _settingsReadFailed = MutableStateFlow(false)
    val settingsReadFailed: StateFlow<Boolean> = _settingsReadFailed
    private val preferenceObservers = SettingsPreferenceObservers(viewModelScope) { _settingsReadFailed.value = true }
    private fun observePreference(key: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        viewModelScope.launch { preferenceObservers.observe(key, block) }
    }
    val lyricsAnimationsEnabled = dataStoreManager.lyricsAnimationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLyricsAnimationsEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setLyricsAnimationsEnabled(enabled)
        }
    }

    private val databasePath: String? = commonRepository.getDatabasePath()
    private val downloadUtils: DownloadHandler by inject()
    private val supabase: SupabaseClient by inject()

    private var _location: MutableStateFlow<String?> = MutableStateFlow(null)
    val location: StateFlow<String?> = _location
    private var _language: MutableStateFlow<String?> = MutableStateFlow(null)
    val language: StateFlow<String?> = _language
    private var _loggedIn: MutableStateFlow<String?> = MutableStateFlow(null)
    val loggedIn: StateFlow<String?> = _loggedIn
    private var _normalizeVolume: MutableStateFlow<String?> = MutableStateFlow(null)
    val normalizeVolume: StateFlow<String?> = _normalizeVolume
    private var _skipSilent: MutableStateFlow<String?> = MutableStateFlow(null)
    val skipSilent: StateFlow<String?> = _skipSilent
    private var _savedPlaybackState: MutableStateFlow<String?> = MutableStateFlow(null)
    val savedPlaybackState: StateFlow<String?> = _savedPlaybackState
    private var _saveRecentSongAndQueue: MutableStateFlow<String?> = MutableStateFlow(null)
    val saveRecentSongAndQueue: StateFlow<String?> = _saveRecentSongAndQueue
    private var _lastCheckForUpdate: MutableStateFlow<String?> = MutableStateFlow(null)
    val lastCheckForUpdate: StateFlow<String?> = _lastCheckForUpdate
    private var _sponsorBlockEnabled: MutableStateFlow<String?> = MutableStateFlow(null)
    val sponsorBlockEnabled: StateFlow<String?> = _sponsorBlockEnabled
    private var _sponsorBlockCategories: MutableStateFlow<ArrayList<String>?> =
        MutableStateFlow(null)
    val sponsorBlockCategories: StateFlow<ArrayList<String>?> = _sponsorBlockCategories
    private var _sendBackToGoogle: MutableStateFlow<String?> = MutableStateFlow(null)
    val sendBackToGoogle: StateFlow<String?> = _sendBackToGoogle
    private var _mainLyricsProvider: MutableStateFlow<String?> = MutableStateFlow(null)
    val mainLyricsProvider: StateFlow<String?> = _mainLyricsProvider

    private var _translationLanguage: MutableStateFlow<String?> = MutableStateFlow(null)
    val translationLanguage: StateFlow<String?> = _translationLanguage
    private var _useTranslation: MutableStateFlow<String?> = MutableStateFlow(null)
    val useTranslation: StateFlow<String?> = _useTranslation
    private var _playerCacheLimit: MutableStateFlow<Int?> = MutableStateFlow(null)
    val playerCacheLimit: StateFlow<Int?> = _playerCacheLimit
    private var _playVideoInsteadOfAudio: MutableStateFlow<String?> = MutableStateFlow(null)
    val playVideoInsteadOfAudio: StateFlow<String?> = _playVideoInsteadOfAudio
    private var _videoQuality: MutableStateFlow<String?> = MutableStateFlow(null)
    val videoQuality: StateFlow<String?> = _videoQuality
    private var _thumbCacheSize = MutableStateFlow<Long?>(null)
    val thumbCacheSize: StateFlow<Long?> = _thumbCacheSize
    private var _canvasCacheSize: MutableStateFlow<Long?> = MutableStateFlow(null)
    val canvasCacheSize: StateFlow<Long?> = _canvasCacheSize
    private var _translucentBottomBar: MutableStateFlow<String?> = MutableStateFlow(null)
    val translucentBottomBar: StateFlow<String?> = _translucentBottomBar
    private var _usingProxy = MutableStateFlow(false)
    val usingProxy: StateFlow<Boolean> = _usingProxy
    private var _proxyType = MutableStateFlow(DataStoreManager.ProxyType.PROXY_TYPE_HTTP)
    val proxyType: StateFlow<DataStoreManager.ProxyType> = _proxyType
    private var _proxyHost = MutableStateFlow("")
    val proxyHost: StateFlow<String> = _proxyHost
    private var _proxyPort = MutableStateFlow(8000)
    val proxyPort: StateFlow<Int> = _proxyPort
    private var _proxyUsername = MutableStateFlow("")
    val proxyUsername: StateFlow<String> = _proxyUsername
    private var _proxyPassword = MutableStateFlow("")
    val proxyPassword: StateFlow<String> = _proxyPassword
    private var _autoCheckUpdate = MutableStateFlow(false)
    val autoCheckUpdate: StateFlow<Boolean> = _autoCheckUpdate
    private var _updateChannel: MutableStateFlow<String> = MutableStateFlow(DataStoreManager.GITHUB)
    val updateChannel: StateFlow<String> = _updateChannel
    private var _blurFullscreenLyrics = MutableStateFlow(false)
    val blurFullscreenLyrics: StateFlow<Boolean> = _blurFullscreenLyrics
    private var _blurPlayerBackground = MutableStateFlow(false)
    val blurPlayerBackground: StateFlow<Boolean> = _blurPlayerBackground
    private val _aiProvider = MutableStateFlow<String>(DataStoreManager.AI_PROVIDER_OPENAI)
    val aiProvider: StateFlow<String> = _aiProvider
    private val _isHasApiKey = MutableStateFlow<Boolean>(false)
    val isHasApiKey: StateFlow<Boolean> = _isHasApiKey
    private val _useAITranslation = MutableStateFlow<Boolean>(false)
    val useAITranslation: StateFlow<Boolean> = _useAITranslation
    private val _customModelId = MutableStateFlow<String>("")
    val customModelId: StateFlow<String> = _customModelId
    private val _customOpenAIBaseUrl = MutableStateFlow<String>("")
    val customOpenAIBaseUrl: StateFlow<String> = _customOpenAIBaseUrl
    private val _customOpenAIHeaders = MutableStateFlow<String>("")
    val customOpenAIHeaders: StateFlow<String> = _customOpenAIHeaders
    private val _crossfadeEnabled = MutableStateFlow<Boolean>(false)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled
    private val _crossfadeDuration = MutableStateFlow<Int>(5000)
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration
    private val _crossfadeDjMode = MutableStateFlow<Boolean>(true)
    val crossfadeDjMode: StateFlow<Boolean> = _crossfadeDjMode
    private val _youtubeSubtitleLanguage = MutableStateFlow<String>("")
    val youtubeSubtitleLanguage: StateFlow<String> = _youtubeSubtitleLanguage

    private var _helpBuildLyricsDatabase: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val helpBuildLyricsDatabase: StateFlow<Boolean> = _helpBuildLyricsDatabase
    private var _contributor: MutableStateFlow<Pair<String, String>> = MutableStateFlow(Pair("", ""))
    val contributor: StateFlow<Pair<String, String>> = _contributor

    private var _backupDownloaded: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val backupDownloaded: StateFlow<Boolean> = _backupDownloaded

    private var _enableLiquidGlass: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val enableLiquidGlass: StateFlow<Boolean> = _enableLiquidGlass

    private val _explicitContentEnabled = MutableStateFlow(false)
    val explicitContentEnabled: StateFlow<Boolean> = _explicitContentEnabled

    private val _discordLoggedIn = MutableStateFlow(false)
    val discordLoggedIn: StateFlow<Boolean> = _discordLoggedIn

    private val _richPresenceEnabled = MutableStateFlow(false)
    val richPresenceEnabled: StateFlow<Boolean> = _richPresenceEnabled

    private val _keepServiceAlive = MutableStateFlow<Boolean>(false)
    val keepServiceAlive: StateFlow<Boolean> = _keepServiceAlive

    private val _keepYouTubePlaylistOffline = MutableStateFlow<Boolean>(false)
    val keepYouTubePlaylistOffline: StateFlow<Boolean> = _keepYouTubePlaylistOffline

    private val _combineLocalAndYouTubeLiked = MutableStateFlow<Boolean>(false)
    val combineLocalAndYouTubeLiked: StateFlow<Boolean> = _combineLocalAndYouTubeLiked

    private val _downloadQuality = MutableStateFlow<String?>(null)
    val downloadQuality: StateFlow<String?> = _downloadQuality

    private val _videoDownloadQuality = MutableStateFlow<String?>(null)
    val videoDownloadQuality: StateFlow<String?> = _videoDownloadQuality

    private val _localTrackingEnabled = MutableStateFlow<Boolean>(false)
    val localTrackingEnabled: StateFlow<Boolean> = _localTrackingEnabled

    // Auto Backup
    private val _autoBackupEnabled = MutableStateFlow<Boolean>(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled

    private val _autoBackupFrequency = MutableStateFlow<String>(DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY)
    val autoBackupFrequency: StateFlow<String> = _autoBackupFrequency

    private val _autoBackupMaxFiles = MutableStateFlow<Int>(5)
    val autoBackupMaxFiles: StateFlow<Int> = _autoBackupMaxFiles

    private val _autoBackupLastTime = MutableStateFlow<Long>(0L)
    val autoBackupLastTime: StateFlow<Long> = _autoBackupLastTime

    private var _alertData: MutableStateFlow<SettingAlertState?> = MutableStateFlow(null)
    val alertData: StateFlow<SettingAlertState?> = _alertData

    private var _basicAlertData: MutableStateFlow<SettingBasicAlertState?> = MutableStateFlow(null)
    val basicAlertData: StateFlow<SettingBasicAlertState?> = _basicAlertData

    // Fraction of storage
    private var _fraction: MutableStateFlow<SettingsStorageSectionFraction> =
        MutableStateFlow(
            SettingsStorageSectionFraction(),
        )
    val fraction: StateFlow<SettingsStorageSectionFraction> = _fraction

    // Biến để lưu trữ và hiển thị trạng thái killServiceOnExit
    private var _killServiceOnExit: MutableStateFlow<String?> = MutableStateFlow(null)
    val killServiceOnExit: StateFlow<String?> = _killServiceOnExit

    init {
        getYoutubeSubtitleLanguage()
        getHelpBuildLyricsDatabase()
        viewModelScope.launch {
            enableLiquidGlass.collect {
                if (getPlatform() != Platform.Android && it) {
                    setEnableLiquidGlass(false)
                }
            }
        }
    }

    fun getAudioSessionId() = mediaPlayerHandler.player.audioSessionId

    fun getData() {
        _settingsReadFailed.value = false
        getLocation()
        getLanguage()
        getQuality()
        getPlayerCacheSize()
        getDownloadedCacheSize()
        getPlayerCacheLimit()
        getLoggedIn()
        getNormalizeVolume()
        getSkipSilent()
        getSavedPlaybackState()
        getSendBackToGoogle()
        getSaveRecentSongAndQueue()
        getLastCheckForUpdate()
        getSponsorBlockEnabled()
        getSponsorBlockCategories()
        getTranslationLanguage()
        getYoutubeSubtitleLanguage()
        getLyricsProvider()
        getUseTranslation()
        getPlayVideoInsteadOfAudio()
        getVideoQuality()
        getSpotifyLogIn()
        getSpotifyLyrics()
        getSpotifyCanvas()
        getUsingProxy()
        getCanvasCache()
        getTranslucentBottomBar()
        getAutoCheckUpdate()
        getBlurFullscreenLyrics()
        getBlurPlayerBackground()
        getAIProvider()
        getAIApiKey()
        getAITranslation()
        getCustomModelId()
        getCustomOpenAIBaseUrl()
        getCustomOpenAIHeaders()
        getKillServiceOnExit()
        getCrossfadeEnabled()
        getCrossfadeDuration()
        getCrossfadeDjMode()
        getContributorNameAndEmail()
        getBackupDownloaded()
        getUpdateChannel()
        getEnableLiquidGlass()
        getExplicitContentEnabled()
        getDiscordLoggedIn()
        getDiscordRichPresenceEnabled()
        getKeepServiceAlive()
        getKeepYouTubePlaylistOffline()
        getCombineLocalAndYouTubeLiked()
        getDownloadQuality()
        getVideoDownloadQuality()
        getLocalTrackingEnabled()
        getAutoBackupEnabled()
        getAutoBackupFrequency()
        getAutoBackupMaxFiles()
        getAutoBackupLastTime()
    }

    private fun getLocalTrackingEnabled() {
        observePreference("getLocalTrackingEnabled") {
            dataStoreManager.localTrackingEnabled.collect { enabled ->
                _localTrackingEnabled.value = enabled == DataStoreManager.TRUE
            }
        }
    }

    fun setLocalTrackingEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setLocalTrackingEnabled(enabled)
            getLocalTrackingEnabled()
        }
    }

    private fun getDownloadQuality() {
        observePreference("getDownloadQuality") {
            dataStoreManager.downloadQuality.collect { quality ->
                if (QUALITY.items.any { it.toString() == quality }) {
                    _downloadQuality.emit(quality)
                } else {
                    _downloadQuality.emit(QUALITY.items[0].toString())
                }
            }
        }
    }

    fun setDownloadQuality(quality: String) {
        changePreference {
            dataStoreManager.setDownloadQuality(quality)
            getDownloadQuality()
        }
    }

    private fun getVideoDownloadQuality() {
        observePreference("getVideoDownloadQuality") {
            dataStoreManager.videoDownloadQuality.collect { videoQuality ->
                when (videoQuality) {
                    VIDEO_QUALITY.items[0].toString() -> _videoDownloadQuality.emit(VIDEO_QUALITY.items[0].toString())
                    VIDEO_QUALITY.items[1].toString() -> _videoDownloadQuality.emit(VIDEO_QUALITY.items[1].toString())
                    VIDEO_QUALITY.items[2].toString() -> _videoDownloadQuality.emit(VIDEO_QUALITY.items[2].toString())
                }
            }
        }
    }

    fun setVideoDownloadQuality(quality: String) {
        changePreference {
            if (VIDEO_QUALITY.items.contains(quality)) {
                dataStoreManager.setVideoDownloadQuality(quality)
            }
            getVideoDownloadQuality()
        }
    }

    private fun getKeepYouTubePlaylistOffline() {
        observePreference("getKeepYouTubePlaylistOffline") {
            dataStoreManager.keepYouTubePlaylistOffline.collect { keep ->
                _keepYouTubePlaylistOffline.value = keep == DataStoreManager.TRUE
            }
        }
    }

    fun setKeepYouTubePlaylistOffline(keep: Boolean) {
        changePreference {
            dataStoreManager.setKeepYouTubePlaylistOffline(keep)
            getKeepYouTubePlaylistOffline()
        }
    }

    private fun getCombineLocalAndYouTubeLiked() {
        observePreference("getCombineLocalAndYouTubeLiked") {
            dataStoreManager.combineLocalAndYouTubeLiked.collect { combine ->
                _combineLocalAndYouTubeLiked.value = combine == DataStoreManager.TRUE
            }
        }
    }

    fun setCombineLocalAndYouTubeLiked(combine: Boolean) {
        changePreference {
            dataStoreManager.setCombineLocalAndYouTubeLiked(combine)
            getCombineLocalAndYouTubeLiked()
        }
    }

    private fun getKeepServiceAlive() {
        observePreference("getKeepServiceAlive") {
            dataStoreManager.keepServiceAlive.collect { keepServiceAlive ->
                _keepServiceAlive.value = keepServiceAlive == DataStoreManager.TRUE
            }
        }
    }

    fun setKeepServiceAlive(keepServiceAlive: Boolean) {
        changePreference {
            if (keepServiceAlive) dataStoreManager.setKillServiceOnExit(false)
            dataStoreManager.setKeepServiceAlive(keepServiceAlive)
            getKeepServiceAlive()
        }
    }

    private fun getCrossfadeEnabled() {
        observePreference("getCrossfadeEnabled") {
            dataStoreManager.crossfadeEnabled.collect { enabled ->
                _crossfadeEnabled.value = enabled == DataStoreManager.TRUE
            }
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setCrossfadeEnabled(enabled)
            getCrossfadeEnabled()
        }
    }

    private fun getCrossfadeDuration() {
        observePreference("getCrossfadeDuration") {
            dataStoreManager.crossfadeDuration.collect { duration ->
                _crossfadeDuration.value = duration
            }
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        changePreference {
            dataStoreManager.setCrossfadeDuration(duration)
            getCrossfadeDuration()
        }
    }

    private fun getCrossfadeDjMode() {
        observePreference("getCrossfadeDjMode") {
            dataStoreManager.crossfadeDjMode.collect { enabled ->
                _crossfadeDjMode.value = enabled == DataStoreManager.TRUE
            }
        }
    }

    fun setCrossfadeDjMode(enabled: Boolean) {
        changePreference {
            dataStoreManager.setCrossfadeDjMode(enabled)
            getCrossfadeDjMode()
        }
    }

    private fun getDiscordLoggedIn() {
        observePreference("getDiscordLoggedIn") {
            dataStoreManager.discordToken.collect { loggedIn ->
                _discordLoggedIn.value = loggedIn.isNotEmpty()
            }
        }
    }

    fun linkGoogleAccount() {
        viewModelScope.launch {
            try {
                supabase.auth.linkIdentity(io.github.jan.supabase.auth.providers.Google)
                makeToast("Permintaan penautan akun Google berhasil dikirim.")
            } catch (e: Throwable) {
                e.printStackTrace()
                makeToast("Gagal menautkan akun: ${e.message}")
            }
        }
    }

    fun logOutDiscord() {
        viewModelScope.launch {
            dataStoreManager.setDiscordToken("")
            delay(100)
            getDiscordLoggedIn()
        }
    }

    private fun getDiscordRichPresenceEnabled() {
        observePreference("getDiscordRichPresenceEnabled") {
            dataStoreManager.richPresenceEnabled.collect { enabled ->
                _richPresenceEnabled.value = enabled == DataStoreManager.TRUE
            }
        }
    }

    fun setDiscordRichPresenceEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setRichPresenceEnabled(enabled)
            delay(100)
            getDiscordRichPresenceEnabled()
        }
    }

    private fun getExplicitContentEnabled() {
        observePreference("getExplicitContentEnabled") {
            dataStoreManager.explicitContentEnabled.collect { enabled ->
                _explicitContentEnabled.value = enabled == DataStoreManager.TRUE
            }
        }
    }

    fun setExplicitContentEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setExplicitContentEnabled(enabled)
            getExplicitContentEnabled()
        }
    }

    private fun getEnableLiquidGlass() {
        observePreference("getEnableLiquidGlass") {
            dataStoreManager.enableLiquidGlass.collect { enableLiquidGlass ->
                _enableLiquidGlass.value = enableLiquidGlass == DataStoreManager.TRUE
            }
        }
    }

    fun setEnableLiquidGlass(enableLiquidGlass: Boolean) {
        changePreference {
            dataStoreManager.setEnableLiquidGlass(enableLiquidGlass)
            getEnableLiquidGlass()
        }
    }

    private fun getUpdateChannel() {
        observePreference("getUpdateChannel") {
            dataStoreManager.updateChannel.collect { channel ->
                _updateChannel.value = channel
            }
        }
    }

    fun setUpdateChannel(channel: String) {
        changePreference {
            dataStoreManager.setUpdateChannel(channel)
            getUpdateChannel()
        }
    }

    private fun getBackupDownloaded() {
        observePreference("getBackupDownloaded") {
            dataStoreManager.backupDownloaded.collect { backupDownloaded ->
                _backupDownloaded.value = backupDownloaded == DataStoreManager.TRUE
            }
        }
    }

    fun setBackupDownloaded(backupDownloaded: Boolean) {
        changePreference {
            dataStoreManager.setBackupDownloaded(backupDownloaded)
            getBackupDownloaded()
        }
    }

    // Auto Backup functions
    private fun getAutoBackupEnabled() {
        observePreference("getAutoBackupEnabled") {
            dataStoreManager.autoBackupEnabled.collect { enabled ->
                _autoBackupEnabled.value = enabled == DataStoreManager.TRUE
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setAutoBackupEnabled(enabled)
            getAutoBackupEnabled()
        }
    }

    private fun getAutoBackupFrequency() {
        observePreference("getAutoBackupFrequency") {
            dataStoreManager.autoBackupFrequency.collect { frequency ->
                _autoBackupFrequency.value = frequency
            }
        }
    }

    fun setAutoBackupFrequency(frequency: String) {
        changePreference {
            dataStoreManager.setAutoBackupFrequency(frequency)
            getAutoBackupFrequency()
        }
    }

    private fun getAutoBackupMaxFiles() {
        observePreference("getAutoBackupMaxFiles") {
            dataStoreManager.autoBackupMaxFiles.collect { max ->
                _autoBackupMaxFiles.value = max
            }
        }
    }

    fun setAutoBackupMaxFiles(max: Int) {
        changePreference {
            dataStoreManager.setAutoBackupMaxFiles(max)
            getAutoBackupMaxFiles()
        }
    }

    private fun getAutoBackupLastTime() {
        observePreference("getAutoBackupLastTime") {
            dataStoreManager.autoBackupLastTime.collect { time ->
                _autoBackupLastTime.value = time
            }
        }
    }

    private fun getContributorNameAndEmail() {
        observePreference("getContributorNameAndEmail") {
            combine(dataStoreManager.contributorName, dataStoreManager.contributorEmail) { name, email ->
                name to email
            }.collect { contributor ->
                _contributor.value = contributor
            }
        }
    }

    fun setContributorName(name: String) {
        changePreference {
            dataStoreManager.setContributorLyricsDatabase(name to contributor.value.second)
            getContributorNameAndEmail()
        }
    }

    fun setContributorEmail(email: String) {
        changePreference {
            dataStoreManager.setContributorLyricsDatabase(contributor.value.first to email)
            getContributorNameAndEmail()
        }
    }

    private fun getCustomModelId() {
        observePreference("getCustomModelId") {
            dataStoreManager.customModelId.collect { customModelId ->
                _customModelId.value = customModelId
            }
        }
    }

    fun setCustomModelId(modelId: String) {
        changePreference {
            dataStoreManager.setCustomModelId(modelId)
            getCustomModelId()
        }
    }

    private fun getCustomOpenAIBaseUrl() {
        observePreference("getCustomOpenAIBaseUrl") {
            dataStoreManager.customOpenAIBaseUrl.collect { baseUrl ->
                _customOpenAIBaseUrl.value = baseUrl
            }
        }
    }

    fun setCustomOpenAIBaseUrl(baseUrl: String) {
        changePreference {
            dataStoreManager.setCustomOpenAIBaseUrl(baseUrl.trim())
            getCustomOpenAIBaseUrl()
        }
    }

    private fun getCustomOpenAIHeaders() {
        observePreference("getCustomOpenAIHeaders") {
            dataStoreManager.customOpenAIHeaders.collect { headers ->
                _customOpenAIHeaders.value = headers
            }
        }
    }

    fun setCustomOpenAIHeaders(headers: String) {
        changePreference {
            dataStoreManager.setCustomOpenAIHeaders(headers.trim())
            getCustomOpenAIHeaders()
        }
    }

    private fun getAIProvider() {
        observePreference("getAIProvider") {
            dataStoreManager.aiProvider.collect { aiProvider ->
                _aiProvider.value = aiProvider
            }
        }
    }

    fun setAIProvider(provider: String) {
        changePreference {
            dataStoreManager.setAIProvider(provider)
            getAIProvider()
        }
    }

    private fun getAITranslation() {
        observePreference("getAITranslation") {
            combine(dataStoreManager.useAITranslation, dataStoreManager.aiApiKey) { enabled, key ->
                enabled == DataStoreManager.TRUE && key.isNotBlank()
            }.collect { enabled ->
                _useAITranslation.value = enabled
            }
        }
    }

    fun setAITranslation(useAITranslation: Boolean) {
        changePreference {
            dataStoreManager.setUseAITranslation(useAITranslation)
            if (useAITranslation) dataStoreManager.setEnableTranslateLyric(true)
            getAITranslation()
        }
    }

    private fun getAIApiKey() {
        observePreference("getAIApiKey") {
            dataStoreManager.aiApiKey.collect { aiApiKey ->
                if (aiApiKey.isNotEmpty()) {
                    _isHasApiKey.value = true

                } else {
                    _isHasApiKey.value = false
                }
            }
        }
    }

    fun setAIApiKey(apiKey: String) {
        changePreference {
            dataStoreManager.setAIApiKey(apiKey.trim())
            if (apiKey.isBlank()) dataStoreManager.setUseAITranslation(false)
            getAIApiKey()
        }
    }

    private fun getBlurFullscreenLyrics() {
        observePreference("getBlurFullscreenLyrics") {
            dataStoreManager.blurFullscreenLyrics.collect { blurFullscreenLyrics ->
                _blurFullscreenLyrics.value = blurFullscreenLyrics == DataStoreManager.TRUE
            }
        }
    }

    fun setBlurFullscreenLyrics(blurFullscreenLyrics: Boolean) {
        changePreference {
            dataStoreManager.setBlurFullscreenLyrics(blurFullscreenLyrics)
            getBlurFullscreenLyrics()
        }
    }

    private fun getBlurPlayerBackground() {
        observePreference("getBlurPlayerBackground") {
            dataStoreManager.blurPlayerBackground.collect { blurPlayerBackground ->
                _blurPlayerBackground.value = blurPlayerBackground == DataStoreManager.TRUE
            }
        }
    }

    fun setBlurPlayerBackground(blurPlayerBackground: Boolean) {
        changePreference {
            dataStoreManager.setBlurPlayerBackground(blurPlayerBackground)
            getBlurPlayerBackground()
        }
    }

    private fun getAutoCheckUpdate() {
        observePreference("getAutoCheckUpdate") {
            dataStoreManager.autoCheckForUpdates.collect { autoCheckUpdate ->
                _autoCheckUpdate.value = autoCheckUpdate == DataStoreManager.TRUE
            }
        }
    }

    fun setAutoCheckUpdate(autoCheckUpdate: Boolean) {
        changePreference {
            dataStoreManager.setAutoCheckForUpdates(autoCheckUpdate)
            getAutoCheckUpdate()
        }
    }

    private fun getCanvasCache() {
        observePreference("getCanvasCache") {
            _canvasCacheSize.value = cacheRepository.getCacheSize(Config.CANVAS_CACHE)
        }
    }

    fun setAlertData(alertData: SettingAlertState?) {
        _alertData.value = alertData
    }

    fun setBasicAlertData(alertData: SettingBasicAlertState?) {
        _basicAlertData.value = alertData
    }

    private fun getUsingProxy() {
        observePreference("getUsingProxy") {
            dataStoreManager.usingProxy.collectLatest { usingProxy ->
                if (usingProxy == DataStoreManager.TRUE) {
                    getProxy()
                }
                _usingProxy.value = usingProxy == DataStoreManager.TRUE
            }
        }
    }

    fun setUsingProxy(usingProxy: Boolean) {
        changePreference {
            dataStoreManager.setUsingProxy(usingProxy)
            getUsingProxy()
            getProxy()
        }
    }

    private fun getProxy() {
        observePreference("getProxy") {
            val host =
                launch {
                    dataStoreManager.proxyHost.collect {
                        _proxyHost.value = it
                    }
                }
            val port =
                launch {
                    dataStoreManager.proxyPort.collect {
                        _proxyPort.value = it
                    }
                }
            val type =
                launch {
                    dataStoreManager.proxyType.collect {
                        _proxyType.value = it
                        log("getProxy: $it")
                    }
                }
            val username =
                launch {
                    dataStoreManager.proxyUsername.collect {
                        _proxyUsername.value = it
                    }
                }
            val password =
                launch {
                    dataStoreManager.proxyPassword.collect {
                        _proxyPassword.value = it
                    }
                }
            host.join()
            port.join()
            type.join()
            username.join()
            password.join()
        }
    }

    fun setProxy(
        proxyType: DataStoreManager.ProxyType,
        host: String,
        port: Int,
    ) {
        log("setProxy: $proxyType, $host, $port")
        viewModelScope.launch {
            dataStoreManager.setProxyType(proxyType)
            dataStoreManager.setProxyHost(host)
            dataStoreManager.setProxyPort(port)
        }
    }

    fun setProxyCredentials(
        username: String,
        password: String,
    ) {

        viewModelScope.launch {
            dataStoreManager.setProxyUsername(username)
            dataStoreManager.setProxyPassword(password)
        }
    }

    fun getTranslucentBottomBar() {
        observePreference("getTranslucentBottomBar") {
            dataStoreManager.translucentBottomBar.collect { translucentBottomBar ->
                _translucentBottomBar.emit(translucentBottomBar)
            }
        }
    }

    fun setTranslucentBottomBar(translucentBottomBar: Boolean) {
        changePreference {
            dataStoreManager.setTranslucentBottomBar(translucentBottomBar)
            getTranslucentBottomBar()
        }
    }

    fun getThumbCacheSize(context: PlatformContext) {
        observePreference("getThumbCacheSize") {
            val diskCache = SingletonImageLoader.get(context).diskCache
            _thumbCacheSize.emit(diskCache?.size)
        }
    }

    fun getVideoQuality() {
        observePreference("getVideoQuality") {
            dataStoreManager.videoQuality.collect { videoQuality ->
                when (videoQuality) {
                    VIDEO_QUALITY.items[0].toString() -> _videoQuality.emit(VIDEO_QUALITY.items[0].toString())
                    VIDEO_QUALITY.items[1].toString() -> _videoQuality.emit(VIDEO_QUALITY.items[1].toString())
                    VIDEO_QUALITY.items[2].toString() -> _videoQuality.emit(VIDEO_QUALITY.items[2].toString())
                }
            }
        }
    }

    fun getTranslationLanguage() {
        observePreference("getTranslationLanguage") {
            dataStoreManager.translationLanguage.collect { translationLanguage ->
                _translationLanguage.emit(translationLanguage)
            }
        }
    }

    fun setTranslationLanguage(language: String) {
        changePreference {
            dataStoreManager.setTranslationLanguage(language)
            getTranslationLanguage()
        }
    }

    fun getUseTranslation() {
        observePreference("getUseTranslation") {
            dataStoreManager.enableTranslateLyric.collect { useTranslation ->
                _useTranslation.emit(useTranslation)
            }
        }
    }

    fun setUseTranslation(useTranslation: Boolean) {
        changePreference {
            dataStoreManager.setEnableTranslateLyric(useTranslation)
            if (!useTranslation) {
                dataStoreManager.setUseAITranslation(false)
                getAITranslation()
            }
            getUseTranslation()
        }
    }

    fun getLyricsProvider() {
        observePreference("getLyricsProvider") {
            dataStoreManager.lyricsProvider.collect { mainLyricsProvider ->
                _mainLyricsProvider.emit(mainLyricsProvider)
            }
        }
    }

    fun setLyricsProvider(provider: String) {
        changePreference {
            dataStoreManager.setLyricsProvider(provider)
            getLyricsProvider()
        }
    }

    fun getLocation() {
        observePreference("getLocation") {
            dataStoreManager.location.collect { location ->
                _location.emit(location)
            }
        }
    }

    fun getLoggedIn() {
        observePreference("getLoggedIn") {
            dataStoreManager.loggedIn.collect { loggedIn ->
                _loggedIn.emit(loggedIn)
            }
        }
    }

    fun changeLocation(location: String) {
        changePreference {
            dataStoreManager.setLocation(location)
            getLocation()
        }
    }

    fun getSaveRecentSongAndQueue() {
        observePreference("getSaveRecentSongAndQueue") {
            dataStoreManager.saveRecentSongAndQueue.collect { saved ->
                _saveRecentSongAndQueue.emit(saved)
            }
        }
    }

    fun getLastCheckForUpdate() {
        observePreference("getLastCheckForUpdate") {
            dataStoreManager.getString("CheckForUpdateAt").collect { lastCheckForUpdate ->
                _lastCheckForUpdate.emit(lastCheckForUpdate)
            }
        }
    }

    fun getSponsorBlockEnabled() {
        observePreference("getSponsorBlockEnabled") {
            dataStoreManager.sponsorBlockEnabled.first().let { enabled ->
                _sponsorBlockEnabled.emit(enabled)
            }
        }
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        changePreference {
            dataStoreManager.setSponsorBlockEnabled(enabled)
            getSponsorBlockEnabled()
        }
    }

    fun getPlayVideoInsteadOfAudio() {
        observePreference("getPlayVideoInsteadOfAudio") {
            dataStoreManager.watchVideoInsteadOfPlayingAudio.collect { playVideoInsteadOfAudio ->
                _playVideoInsteadOfAudio.emit(playVideoInsteadOfAudio)
            }
        }
    }

    fun setPlayVideoInsteadOfAudio(playVideoInsteadOfAudio: Boolean) {
        changePreference {
            dataStoreManager.setWatchVideoInsteadOfPlayingAudio(playVideoInsteadOfAudio)
            getPlayVideoInsteadOfAudio()
        }
    }

    fun getSponsorBlockCategories() {
        observePreference("getSponsorBlockCategories") {
            dataStoreManager.getSponsorBlockCategories().let {
                log("getSponsorBlockCategories: $it", LogLevel.WARN)
                _sponsorBlockCategories.emit(it)
            }
        }
    }

    fun setSponsorBlockCategories(list: ArrayList<String>) {
        log("setSponsorBlockCategories: $list", LogLevel.WARN)
        changePreference {
            runBlocking(Dispatchers.IO) {
                dataStoreManager.setSponsorBlockCategories(list)
            }
            getSponsorBlockCategories()
        }
    }

    private var _quality: MutableStateFlow<String?> = MutableStateFlow(null)
    val quality: StateFlow<String?> = _quality

    fun getQuality() {
        observePreference("getQuality") {
            dataStoreManager.quality.collect { quality ->
                if (QUALITY.items.any { it.toString() == quality }) {
                    _quality.emit(quality)
                } else {
                    _quality.emit(QUALITY.items[0].toString())
                }
            }
        }
    }

    fun changeVideoQuality(item: String) {
        changePreference {
            if (VIDEO_QUALITY.items.contains(item)) {
                dataStoreManager.setVideoQuality(item)
            }
            getVideoQuality()
        }
    }

    fun changeQuality(qualityItem: String?) {
        changePreference {
            log("changeQuality: $qualityItem")
            dataStoreManager.setQuality(qualityItem ?: QUALITY.items.first().toString())
            getQuality()
        }
    }

    private val _cacheSize: MutableStateFlow<Long?> = MutableStateFlow(null)
    var cacheSize: StateFlow<Long?> = _cacheSize

    fun getPlayerCacheSize() {
        observePreference("getPlayerCacheSize") {
            _cacheSize.value = cacheRepository.getCacheSize(Config.PLAYER_CACHE)
        }
    }

    fun clearPlayerCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cacheRepository.clearCache(Config.PLAYER_CACHE) }
            makeToast(getString(Res.string.clear_player_cache))
            getPlayerCacheSize()
        }
    }

    private val _downloadedCacheSize: MutableStateFlow<Long?> = MutableStateFlow(null)
    var downloadedCacheSize: StateFlow<Long?> = _downloadedCacheSize

    fun getDownloadedCacheSize() {
        observePreference("getDownloadedCacheSize") {
            _downloadedCacheSize.value = cacheRepository.getCacheSize(Config.DOWNLOAD_CACHE)
        }
    }

    fun clearDownloadedCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cacheRepository.clearCache(Config.DOWNLOAD_CACHE) }
            songRepository.getDownloadedSongs().firstOrNull()?.let { songs ->
                songs.forEach { song ->
                    songRepository.updateDownloadState(song.videoId, DownloadState.STATE_NOT_DOWNLOADED)
                }
            }
            makeToast(getString(Res.string.clear_downloaded_cache))
            getDownloadedCacheSize()
            downloadUtils.removeAllDownloads()
        }
    }

    fun clearCanvasCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cacheRepository.clearCache(Config.CANVAS_CACHE) }
            makeToast(getString(Res.string.clear_canvas_cache))
            getCanvasCache()
        }
    }

    fun backup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                makeToast(getString(Res.string.backup_in_progress))
                withContext(Dispatchers.IO) {
                    backupNative(commonRepository, uri, backupDownloaded.value)
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    makeToast(getString(Res.string.backup_create_success))
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    it.printStackTrace()
                    makeToast(getString(Res.string.backup_create_failed))
                }
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            makeToast(getString(Res.string.restore_in_progress))
            withContext(Dispatchers.IO) {
                runCatching {
                    restoreNative(commonRepository, uri) {
                        getData()
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        it.printStackTrace()
                        makeToast(getString(Res.string.restore_failed))
                    }
                }
            }
        }
    }

    fun getLanguage() {
        observePreference("getLanguage") {
            dataStoreManager.getString(SELECTED_LANGUAGE).collect { language ->
                _language.emit(language)
            }
        }
    }

    fun changeLanguage(code: String) {
        changePreference {
            dataStoreManager.putString(SELECTED_LANGUAGE, code)
            Logger.w("SettingsViewModel", "changeLanguage: $code")
            getLanguage()
            changeLanguageNative(code)
        }
    }

    fun getNormalizeVolume() {
        observePreference("getNormalizeVolume") {
            dataStoreManager.normalizeVolume.collect { normalizeVolume ->
                _normalizeVolume.emit(normalizeVolume)
            }
        }
    }

    fun setNormalizeVolume(normalizeVolume: Boolean) {
        changePreference {
            dataStoreManager.setNormalizeVolume(normalizeVolume)
            getNormalizeVolume()
        }
    }

    fun getSendBackToGoogle() {
        observePreference("getSendBackToGoogle") {
            dataStoreManager.sendBackToGoogle.collect { sendBackToGoogle ->
                _sendBackToGoogle.emit(sendBackToGoogle)
            }
        }
    }

    fun setSendBackToGoogle(sendBackToGoogle: Boolean) {
        changePreference {
            dataStoreManager.setSendBackToGoogle(sendBackToGoogle)
            getSendBackToGoogle()
        }
    }

    fun getSkipSilent() {
        observePreference("getSkipSilent") {
            dataStoreManager.skipSilent.collect { skipSilent ->
                _skipSilent.emit(skipSilent)
            }
        }
    }

    fun setSkipSilent(skip: Boolean) {
        changePreference {
            dataStoreManager.setSkipSilent(skip)
            getSkipSilent()
        }
    }

    fun getSavedPlaybackState() {
        observePreference("getSavedPlaybackState") {
            dataStoreManager.saveStateOfPlayback.collect { savedPlaybackState ->
                _savedPlaybackState.emit(savedPlaybackState)
            }
        }
    }

    fun setSavedPlaybackState(savedPlaybackState: Boolean) {
        changePreference {
            dataStoreManager.setSaveStateOfPlayback(savedPlaybackState)
            getSavedPlaybackState()
        }
    }

    fun setSaveLastPlayed(b: Boolean) {
        changePreference {
            dataStoreManager.setSaveRecentSongAndQueue(b)
            getSaveRecentSongAndQueue()
        }
    }

    fun getPlayerCacheLimit() {
        observePreference("getPlayerCacheLimit") {
            dataStoreManager.maxSongCacheSize.collect {
                _playerCacheLimit.emit(it)
            }
        }
    }

    fun setPlayerCacheLimit(size: Int) {
        changePreference {
            dataStoreManager.setMaxSongCacheSize(size)
            getPlayerCacheLimit()
        }
    }

    private var _googleAccounts: MutableStateFlow<LocalResource<List<GoogleAccountEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val googleAccounts: StateFlow<LocalResource<List<GoogleAccountEntity>>> = _googleAccounts

    fun getAllGoogleAccount() {
        Logger.w("getAllGoogleAccount", "getAllGoogleAccount: Go to function")
        observePreference("getAllGoogleAccount") {
            _googleAccounts.emit(LocalResource.Loading())
            accountRepository.getGoogleAccounts().collectLatest { accounts ->
                Logger.w("getAllGoogleAccount", "getAllGoogleAccount: $accounts")
                if (!accounts.isNullOrEmpty()) {
                    _googleAccounts.emit(LocalResource.Success(accounts))
                } else {
                    if (dataStoreManager.cookie.first().isNotBlank()) {
                        accountRepository
                            .getAccountInfo(
                                dataStoreManager.cookie.first(),
                            ).collect {
                                Logger.w("getAllGoogleAccount", "getAllGoogleAccount: $it")
                                if (it.isNotEmpty()) {
                                    dataStoreManager.putString("YouTubeAccountName", it.first().name)
                                    dataStoreManager.putString(
                                        "YouTubeAccountThumbUrl",
                                        it
                                            .first()
                                            .thumbnails
                                            .lastOrNull()
                                            ?.url ?: "",
                                    )
                                    accountRepository
                                        .insertGoogleAccount(
                                            GoogleAccountEntity(
                                                email = it.first().email,
                                                name = it.first().name,
                                                thumbnailUrl =
                                                    it
                                                        .first()
                                                        .thumbnails
                                                        .lastOrNull()
                                                        ?.url ?: "",
                                                cache = accountRepository.getYouTubeCookie(),
                                                pageId = it.first().pageId,
                                                isUsed = true,
                                            ),
                                        ).singleOrNull()
                                        ?.let { account ->
                                            Logger.w("getAllGoogleAccount", "inserted: $account")
                                        }
                                    getAllGoogleAccount()
                                } else {
                                    _googleAccounts.emit(LocalResource.Success(emptyList()))
                                }
                            }
                    } else {
                        _googleAccounts.emit(LocalResource.Success(emptyList()))
                    }
                }
            }
        }
    }

    suspend fun addAccount(
        cookie: String,
        netscapeCookie: String? = null,
    ): Boolean {
        val currentCookie = dataStoreManager.cookie.first()
        val currentPageId = dataStoreManager.pageId.first()
        try {
            runBlocking {
                dataStoreManager.setCookie(cookie, "")
            }
            return accountRepository
                .getAccountInfo(
                    cookie,
                ).lastOrNull()
                ?.takeIf {
                    it.isNotEmpty()
                }?.let { accountInfoList ->
                    Logger.d("getAllGoogleAccount", "addAccount: $accountInfoList")
                    accountRepository.getGoogleAccounts().lastOrNull()?.forEach {
                        Logger.d("getAllGoogleAccount", "set used: $it start")
                        accountRepository
                            .updateGoogleAccountUsed(it.email, false)
                            .singleOrNull()
                            ?.let {
                                Logger.w("getAllGoogleAccount", "set used: $it")
                            }
                    }
                    dataStoreManager.putString("YouTubeAccountName", accountInfoList.first().name)
                    dataStoreManager.putString(
                        "YouTubeAccountThumbUrl",
                        accountInfoList
                            .first()
                            .thumbnails
                            .lastOrNull()
                            ?.url ?: "",
                    )
                    val cookieItem =
                        netscapeCookie ?: commonRepository
                            .getCookiesFromInternalDatabase(Config.YOUTUBE_MUSIC_MAIN_URL, getPackageName())
                            .toNetScapeString()
                    commonRepository.writeTextToFile(cookieItem, (getFileDir() + "/ytdlp-cookie.txt")).let {
                        Logger.d("getAllGoogleAccount", "addAccount: write cookie file: $it")
                    }
                    accountInfoList.forEachIndexed { index, account ->
                        accountRepository
                            .insertGoogleAccount(
                                GoogleAccountEntity(
                                    email = account.email,
                                    name = account.name,
                                    thumbnailUrl =
                                        account
                                            .thumbnails
                                            .lastOrNull()
                                            ?.url ?: "",
                                    cache = cookie,
                                    isUsed = index == 0,
                                    netscapeCookie = cookieItem,
                                    pageId = account.pageId,
                                ),
                            ).firstOrNull()
                            ?.let {
                                log("addAccount: $it", LogLevel.WARN)
                            }
                    }
                    dataStoreManager.setCookie(cookie, accountInfoList.first().pageId)
                    getAllGoogleAccount()
                    getLoggedIn()
                    true
                } ?: run {
                Logger.w("getAllGoogleAccount", "addAccount: Account info is null")
                runBlocking {
                    dataStoreManager.setCookie(currentCookie, currentPageId)
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.e("getAllGoogleAccount", "addAccount: ${e.message}")
            runBlocking {
                dataStoreManager.setCookie(currentCookie, currentPageId)
            }
            return false
        }
    }

    fun setUsedAccount(acc: GoogleAccountEntity?) {
        viewModelScope.launch {
            if (acc != null) {
                googleAccounts.value.data?.forEach {
                    accountRepository
                        .updateGoogleAccountUsed(it.email, false)
                        .singleOrNull()
                        ?.let {
                            Logger.w("getAllGoogleAccount", "set used: $it")
                        }
                }
                dataStoreManager.putString("YouTubeAccountName", acc.name)
                dataStoreManager.putString("YouTubeAccountThumbUrl", acc.thumbnailUrl)
                accountRepository
                    .updateGoogleAccountUsed(acc.email, true)
                    .singleOrNull()
                    ?.let {
                        Logger.w("getAllGoogleAccount", "set used: $it")
                    }
                acc.netscapeCookie?.let { commonRepository.writeTextToFile(it, (getFileDir() + "/ytdlp-cookie.txt")) }.let {
                    Logger.d("getAllGoogleAccount", "addAccount: write cookie file: $it")
                }
                dataStoreManager.setCookie(acc.cache ?: "", acc.pageId)
                delay(500)
                getAllGoogleAccount()
                getLoggedIn()
            } else {
                googleAccounts.value.data?.forEach {
                    accountRepository
                        .updateGoogleAccountUsed(it.email, false)
                        .singleOrNull()
                        ?.let {
                            Logger.w("getAllGoogleAccount", "set used: $it")
                        }
                }
                dataStoreManager.putString("YouTubeAccountName", "")
                dataStoreManager.putString("YouTubeAccountThumbUrl", "")
                dataStoreManager.setCookie("", null)
                delay(500)
                getAllGoogleAccount()
                getLoggedIn()
            }
        }
    }

    private val _deletingAccount = MutableStateFlow(false)
    val deletingAccount: StateFlow<Boolean> = _deletingAccount
    fun deleteGratifyAccount() {
        if (_deletingAccount.value) return
        _deletingAccount.value = true
        viewModelScope.launch {
            try {
                userDataSyncManager.deleteAccount()
                makeToast("Akun dan data cloud telah dihapus.")
                getLoggedIn()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { makeToast("Penghapusan belum selesai. Periksa koneksi dan coba lagi.") }
            finally { _deletingAccount.value = false }
        }
    }

    fun logOutAllYouTube() = setUsedAccount(null)

    fun logOutGratify() {
        viewModelScope.launch {
            // Cloud backup is best effort: the private local archive is the durable fallback.
            try {
                withTimeoutOrNull(LOGOUT_SYNC_TIMEOUT_MS) { userDataSyncManager.performSyncUp(force = true) }
                userDataSyncManager.performLogout()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                makeToast("Keluar gagal. Data akun tetap tersimpan; silakan coba lagi.")
                return@launch
            }
            dataStoreManager.clearPerUserData()
            dataStoreManager.setLoggedIn(false)
            dataStoreManager.setCookie("", null)
            delay(500)
            getAllGoogleAccount()
            getLoggedIn()
        }
    }

    @ExperimentalCoilApi
    fun clearThumbnailCache(platformContext: PlatformContext) {
        viewModelScope.launch {
            SingletonImageLoader.get(platformContext).diskCache?.clear()
            makeToast(getString(Res.string.clear_thumbnail_cache))
            getThumbCacheSize(platformContext)
        }
    }

    private var _spotifyLogIn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val spotifyLogIn: StateFlow<Boolean> = _spotifyLogIn

    fun getSpotifyLogIn() {
        observePreference("getSpotifyLogIn") {
            dataStoreManager.spdc.collect { loggedIn ->
                if (loggedIn.isNotEmpty()) {
                    _spotifyLogIn.emit(true)
                } else {
                    _spotifyLogIn.emit(false)
                }
            }
        }
    }

    fun setSpotifyLogIn(loggedIn: Boolean) {
        changePreference {
            _spotifyLogIn.emit(loggedIn)
            if (!loggedIn) {
                dataStoreManager.setSpdc("")
                delay(500)
            }
            getSpotifyLogIn()
            getLoggedIn()
        }
    }

    private var _spotifyLyrics: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val spotifyLyrics: StateFlow<Boolean> = _spotifyLyrics

    private var _spotifyCanvas: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val spotifyCanvas: StateFlow<Boolean> = _spotifyCanvas

    fun getSpotifyLyrics() {
        observePreference("getSpotifyLyrics") {
            dataStoreManager.spotifyLyrics.collect {
                if (it == DataStoreManager.TRUE) {
                    _spotifyLyrics.emit(true)
                } else {
                    _spotifyLyrics.emit(false)
                }
            }
        }
    }

    fun setSpotifyLyrics(loggedIn: Boolean) {
        changePreference {
            dataStoreManager.setSpotifyLyrics(loggedIn)
            getSpotifyLyrics()
        }
    }

    fun getSpotifyCanvas() {
        observePreference("getSpotifyCanvas") {
            dataStoreManager.spotifyCanvas.collect {
                if (it == DataStoreManager.TRUE) {
                    _spotifyCanvas.emit(true)
                } else {
                    _spotifyCanvas.emit(false)
                }
            }
        }
    }

    fun setSpotifyCanvas(loggedIn: Boolean) {
        changePreference {
            dataStoreManager.setSpotifyCanvas(loggedIn)
            getSpotifyCanvas()
        }
    }

    // Lấy giá trị của killServiceOnExit từ DataStore
    fun getKillServiceOnExit() {
        observePreference("getKillServiceOnExit") {
            dataStoreManager.killServiceOnExit.collect { killServiceOnExit ->
                _killServiceOnExit.emit(killServiceOnExit)
            }
        }
    }

    // Lưu giá trị killServiceOnExit vào DataStore
    fun setKillServiceOnExit(kill: Boolean) {
        changePreference {
            if (kill) dataStoreManager.setKeepServiceAlive(false)
            dataStoreManager.setKillServiceOnExit(kill)
            getKillServiceOnExit()
        }
    }

    fun getYoutubeSubtitleLanguage() {
        observePreference("getYoutubeSubtitleLanguage") {
            dataStoreManager.youtubeSubtitleLanguage.collect { language ->
                _youtubeSubtitleLanguage.emit(language)
            }
        }
    }

    fun setYoutubeSubtitleLanguage(language: String) {
        changePreference {
            dataStoreManager.setYoutubeSubtitleLanguage(language)
            getYoutubeSubtitleLanguage()
        }
    }

    fun getHelpBuildLyricsDatabase() {
        observePreference("getHelpBuildLyricsDatabase") {
            dataStoreManager.helpBuildLyricsDatabase.collect { helpBuildLyricsDatabase ->
                _helpBuildLyricsDatabase.emit(helpBuildLyricsDatabase == DataStoreManager.TRUE)
            }
        }
    }

    fun setHelpBuildLyricsDatabase(help: Boolean) {
        changePreference {
            dataStoreManager.setHelpBuildLyricsDatabase(help)
            getHelpBuildLyricsDatabase()
        }
    }

    // Cloud Sync

    private val userDataSyncManager: com.tan.data.sync.UserDataSyncManager by inject()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState

    fun syncUpNow() {
        if (_syncState.value is SyncUiState.InProgress) return
        _syncState.value = SyncUiState.InProgress
        viewModelScope.launch(Dispatchers.IO) {
            try {
                check(userDataSyncManager.performSyncUp(force = true)) { "Backup incomplete" }
                _syncState.value = SyncUiState.Success
            } catch (e: CancellationException) {
                _syncState.value = SyncUiState.Idle
                throw e
            } catch (e: Exception) {
                _syncState.value = SyncUiState.Error("Cloud backup incomplete; retry when online")
            }
        }
    }

    fun syncDownNow() {
        if (_syncState.value is SyncUiState.InProgress) return
        _syncState.value = SyncUiState.InProgress
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userDataSyncManager.performSyncDown()
                _syncState.value = SyncUiState.Success
            } catch (e: CancellationException) {
                _syncState.value = SyncUiState.Idle
                throw e
            } catch (e: Exception) {
                _syncState.value = SyncUiState.Error("Cloud restore incomplete; recovered data retained for retry")
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncUiState.Idle
    }
}

sealed class SyncUiState {
    data object Idle : SyncUiState()
    data object InProgress : SyncUiState()
    data object Success : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

data class SettingsStorageSectionFraction(
    val otherApp: Float = 0f,
    val downloadCache: Float = 0f,
    val playerCache: Float = 0f,
    val canvasCache: Float = 0f,
    val thumbCache: Float = 0f,
    val appDatabase: Float = 0f,
    val freeSpace: Float = 0f,
) {
    fun combine(): Float = otherApp + downloadCache + playerCache + canvasCache + thumbCache + appDatabase + freeSpace
}

data class SettingAlertState(
    val title: String,
    val message: String? = null,
    val textField: TextFieldData? = null,
    val selectOne: SelectData? = null,
    val multipleSelect: SelectData? = null,
    val confirm: Pair<String, (SettingAlertState) -> Unit>,
    val dismiss: String,
) {
    data class TextFieldData(
        val label: String,
        val value: String = "",
        val sensitive: Boolean = false,
        // User typing string -> (true or false, If false, show error message)
        val verifyCodeBlock: ((String) -> Pair<Boolean, String?>)? = null,
    )

    data class SelectData(
        // Selected / Data
        val listSelect: List<Pair<Boolean, String>>,
    ) {
        fun getSelected(): String = listSelect.firstOrNull { it.first }?.second ?: ""

        fun getListSelected(): List<String> = listSelect.filter { it.first }.map { it.second }
    }
}

data class SettingBasicAlertState(
    val title: String,
    val message: String? = null,
    val confirm: Pair<String, () -> Unit>,
    val dismiss: String,
)

expect suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction?

expect suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit = {},
)

expect suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
)

expect fun getPackageName(): String

expect fun getFileDir(): String

expect fun changeLanguageNative(code: String)
