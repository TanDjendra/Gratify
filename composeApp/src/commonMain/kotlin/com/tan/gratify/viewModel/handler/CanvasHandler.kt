package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.canvas.CanvasResult
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.manager.DataStoreManager.Values.TRUE
import com.tan.domain.repository.LyricsCanvasRepository
import com.tan.domain.utils.Resource
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.logger.LogLevel
import com.tan.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CanvasHandler(
    private val scope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val nowPlayingScreenData: MutableStateFlow<NowPlayingScreenData>,
    private val nowPlayingMediaId: () -> String?,
    private val nowPlayingCanvasUrl: () -> String?,
    private val log: (String, LogLevel) -> Unit,
) {
    private val _canvas = MutableStateFlow<CanvasResult?>(null)
    val canvas: StateFlow<CanvasResult?> = _canvas

    private var canvasJob: Job? = null

    fun cancelCanvas() {
        canvasJob?.cancel()
    }

    fun clearCanvas() {
        _canvas.value = null
    }

    fun getCanvas(videoId: String, duration: Int) {
        Logger.w("CanvasHandler", "Start getCanvas: $videoId $duration")
        canvasJob?.cancel()
        canvasJob = scope.launch {
            if (dataStoreManager.spotifyCanvas.first() == TRUE) {
                lyricsCanvasRepository.getCanvas(dataStoreManager, videoId, duration).cancellable().collect { response ->
                    val data = response.data
                    when (response) {
                        is Resource.Success if (data != null && nowPlayingMediaId() == videoId) -> {
                            _canvas.value = data
                            nowPlayingScreenData.update {
                                it.copy(
                                    canvasData = NowPlayingScreenData.CanvasData(
                                        isVideo = data.isVideo,
                                        url = data.canvasUrl,
                                    ),
                                )
                            }
                            if (data.isVideo) lyricsCanvasRepository.updateCanvasUrl(videoId, data.canvasUrl)
                            data.canvasThumbUrl?.let { lyricsCanvasRepository.updateCanvasThumbUrl(videoId, it) }
                        }

                        else -> {
                            log("Get canvas error: ${response.message}", LogLevel.WARN)
                            nowPlayingCanvasUrl()?.let { url ->
                                nowPlayingScreenData.update {
                                    it.copy(
                                        canvasData = NowPlayingScreenData.CanvasData(
                                            isVideo = url.contains(".mp4"),
                                            url = url,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
