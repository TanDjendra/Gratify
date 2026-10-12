package com.tan.media3

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.ContentMetadataMutations
import com.tan.common.Config
import com.tan.domain.data.player.GenericMediaItem
import com.tan.domain.data.player.GenericMediaMetadata
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.player.MediaPlayerInterface
import com.tan.media3.service.SimpleMediaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/** Cache/network regression fixture using the production adapter, session, and service. Debug only. */
@UnstableApi
class MediaPlaybackAuditActivity : Activity(), KoinComponent {
    private val player by inject<MediaPlayerInterface>()
    private val settings by inject<DataStoreManager>()
    private val scope by inject<CoroutineScope>(named(Config.SERVICE_SCOPE))
    private val cache by inject<SimpleCache>(named(Config.DOWNLOAD_CACHE))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Gratify native media playback audit"; textSize = 22f })
        scope.launch {
            when (intent.getStringExtra("audit_action")) {
                "finish" -> finishAndRemoveTask()
                "stop_service" -> { com.tan.media3.di.stopService(this@MediaPlaybackAuditActivity); finish() }
                "stop_on_exit" -> { settings.setKillServiceOnExit(true); finishAndRemoveTask() }
                "seek_to_fade" -> player.seekTo(56500)
                "invalidate_preload" -> {
                    // Simulate an asynchronous preload failure using the real adapter.
                    val adapter = player as com.tan.media3.exoplayer.CrossfadeExoPlayerAdapter
                    val field = adapter.javaClass.getDeclaredField("precachedPlayers").apply { isAccessible = true }
                    val entries = field.get(adapter) as Map<*, *>
                    entries.values.forEach { entry ->
                        val getter = entry!!.javaClass.getDeclaredMethod("getPlayer").apply { isAccessible = true }
                        (getter.invoke(entry) as androidx.media3.exoplayer.ExoPlayer).stop()
                    }
                }
                "source_error" -> {
                    val adapter = player as com.tan.media3.exoplayer.CrossfadeExoPlayerAdapter
                    val field = adapter.javaClass.getDeclaredField("activePlayerListener").apply { isAccessible = true }
                    (field.get(adapter) as? androidx.media3.common.Player.Listener)?.onPlayerError(
                        androidx.media3.common.PlaybackException("Audit expired source", null,
                            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
                }
                "transient_focus" -> {
                    val manager = getSystemService(android.media.AudioManager::class.java)
                    val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                        .setOnAudioFocusChangeListener { }.build()
                    manager.requestAudioFocus(request)
                    kotlinx.coroutines.delay(3000)
                    manager.abandonAudioFocusRequest(request)
                }
                "resume" -> { startService(Intent(this@MediaPlaybackAuditActivity, SimpleMediaService::class.java)); player.play() }
                else -> {
                    settings.setKillServiceOnExit(false)
                    settings.setCrossfadeEnabled(intent.getBooleanExtra("crossfade", false))
                    settings.setCrossfadeDuration(3000)
                    val auditSpeed = intent.getFloatExtra("speed", 1f).coerceIn(1f, 4f)
                    // Persist too: the service observes this preference after a fresh process
                    // launch and would otherwise overwrite the fixture's initial speed.
                    settings.setPlaybackSpeed(auditSpeed)
                    val networkAudit = intent.getBooleanExtra("network_audit", false)
                    if (networkAudit) {
                        // Only this explicit debug fixture bypasses URL resolution/cache. Use a
                        // loopback server via adb reverse to exercise real delayed HTTP loading.
                        val adapter = player as com.tan.media3.exoplayer.CrossfadeExoPlayerAdapter
                        val sourceField = adapter.javaClass.getDeclaredField("mediaSourceFactory").apply { isAccessible = true }
                        val source = sourceField.get(adapter)!!
                        val factoryField = source.javaClass.getDeclaredField("defaultMediaSourceFactory").apply { isAccessible = true }
                        (factoryField.get(source) as androidx.media3.exoplayer.source.DefaultMediaSourceFactory)
                            .setDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this@MediaPlaybackAuditActivity,
                                androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                    .setConnectTimeoutMs(30000).setReadTimeoutMs(30000)))
                        adapter.forwardingPlayer.addListener(object : androidx.media3.common.Player.Listener {
                            override fun onEvents(sessionPlayer: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                                android.util.Log.i("MediaHandoffAudit", "item=${sessionPlayer.mediaMetadata.title} " +
                                    "state=${sessionPlayer.playbackState} intent=${sessionPlayer.playWhenReady} playing=${sessionPlayer.isPlaying}")
                            }
                        })
                    }
                    val audio = assets.open("media-audit.m4a").use { it.readBytes() }
                    val artwork = java.io.File(cacheDir, "media-audit-art.png")
                    val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(45, 110, 185)) }
                    artwork.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    val items = (1..intent.getIntExtra("track_count", 3).coerceIn(3, 20)).map { index ->
                        val id = "gratify-media-audit-m4a-$index"
                        if (!networkAudit && !cache.isCached(id, 0, audio.size.toLong())) {
                            val hole = checkNotNull(cache.startReadWriteNonBlocking(id, 0, audio.size.toLong()))
                            val sink = CacheDataSink.Factory().setCache(cache).createDataSink()
                            try {
                                sink.open(DataSpec.Builder().setUri(id).setKey(id).setLength(audio.size.toLong()).build())
                                try { sink.write(audio, 0, audio.size) } finally { sink.close() }
                                cache.applyContentMetadataMutations(id, ContentMetadataMutations().apply {
                                    ContentMetadataMutations.setContentLength(this, audio.size.toLong())
                                })
                            } finally { cache.releaseHoleSpan(hole) }
                        }
                        val uri = if (networkAudit) "http://127.0.0.1:18089/track-$index.m4a" else id
                        GenericMediaItem(id, uri, GenericMediaMetadata("Audit Track $index", "Gratify Test Artist",
                            "Native Playback Audit", android.net.Uri.fromFile(artwork).toString()), id)
                    }
                    startService(Intent(this@MediaPlaybackAuditActivity, SimpleMediaService::class.java))
                    player.shuffleModeEnabled = false
                    player.repeatMode = 0
                    player.playbackParameters = com.tan.domain.data.player.GenericPlaybackParameters(
                        auditSpeed, 1f)
                    player.playWhenReady = true
                    player.setMediaItem(items.first())
                    // setMediaItem queues its mutation on the application's main scope.
                    kotlinx.coroutines.yield()
                    items.drop(1).forEach(player::addMediaItem)
                }
            }
        }
    }
}
