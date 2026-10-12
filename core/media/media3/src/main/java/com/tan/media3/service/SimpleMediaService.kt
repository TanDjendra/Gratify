package com.tan.media3.service

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.tan.common.MEDIA_NOTIFICATION
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.player.MediaPlayerInterface
import com.tan.logger.Logger
import com.tan.media3.exoplayer.CrossfadeExoPlayerAdapter
import com.tan.media3.R
import com.tan.media3.extension.toCommandButton
import com.tan.media3.utils.CoilBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@UnstableApi
internal class SimpleMediaService :
    MediaLibraryService(),
    KoinComponent {
    companion object {
        // Never retain a Service context after its lifecycle. App exit also needs
        // to disconnect system controllers, which Context.stopService alone cannot do.
        private var runningService = java.lang.ref.WeakReference<SimpleMediaService>(null)

        @androidx.annotation.MainThread
        fun stopRunningPlayback() {
            runningService.get()?.stopPlaybackAndService()
        }
    }
    // Only service subscriptions belong to this scope. The player/handler scope is
    // application-scoped in Koin and must survive service and activity recreation.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mediaPlayerAdapter: MediaPlayerInterface by inject<MediaPlayerInterface>()
    private val player: Player by lazy {
        (mediaPlayerAdapter as CrossfadeExoPlayerAdapter).forwardingPlayer
    }
    private val coilBitmapLoader: CoilBitmapLoader by inject<CoilBitmapLoader>()

    private var mediaSession: MediaLibrarySession? = null

    private val simpleMediaSessionCallback: MediaLibrarySession.Callback by inject<MediaLibrarySession.Callback>()

    private val simpleMediaServiceHandler: MediaPlayerHandler by inject<MediaPlayerHandler>()

    private val binder = MusicBinder()


    inner class MusicBinder : Binder() {
        val service: SimpleMediaService
            get() = this@SimpleMediaService

        fun setActivitySession(
            context: Context,
            activity: Class<out Activity>,
        ) {
            mediaSession?.setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.w("Service", "Simple Media Service Bound")
        return super.onBind(intent) ?: binder
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        runningService = java.lang.ref.WeakReference(this)
        Logger.w("Service", "Simple Media Service Created")

        setListener(ForegroundPlaybackFailureListener {
            Logger.w("Service", "Foreground playback rejected; pausing until a user retries")
            mediaPlayerAdapter.pause()
        })

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { MEDIA_NOTIFICATION.NOTIFICATION_ID },
                MEDIA_NOTIFICATION.NOTIFICATION_CHANNEL_ID,
                R.string.notification_channel_name,
            ).apply {
                setSmallIcon(R.drawable.monochrome)
            },
        )

        if (mediaSession == null) {
            mediaSession =
                provideMediaLibrarySession(
                    this,
                    player,
                    simpleMediaSessionCallback,
                )
            // The app UI uses the adapter directly and binds with MusicBinder,
            // so no external MediaController connection registers this session.
            // Register explicitly to let Media3 own notifications and foreground state.
            addSession(checkNotNull(mediaSession))
        }

        Logger.d("PLAYER_DEBUG", "Player instance (hashCode): ${player.hashCode()}")
        Logger.d("PLAYER_DEBUG", "MediaSession instance (hashCode): ${mediaSession?.hashCode()}")

        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }

    }

    @UnstableApi
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.w("Service", "Simple Media Service Received Action: ${intent?.action}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    @UnstableApi
    override fun onDestroy() {
        if (runningService.get() === this) runningService.clear()
        Logger.w("Service", "Simple Media Service Destroyed")
        simpleMediaServiceHandler.mayBeSaveRecentSong()
        simpleMediaServiceHandler.mayBeSavePlaybackState()
        simpleMediaServiceHandler.onUpdateNotification = {}
        mediaSession?.release()
        mediaSession = null
        // Stop both crossfade players and abandon focus, retaining the reusable
        // application-owned adapter and handler for the next service instance.
        simpleMediaServiceHandler.stopPlaybackForAppExit()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        Logger.w("Service", "Simple Media Service Trim Memory Level: $level")
        simpleMediaServiceHandler.mayBeSaveRecentSong()
    }

    @UnstableApi
    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.w("Service", "Simple Media Service Task Removed")
        stopPlaybackAndService()
    }

    private fun stopPlaybackAndService() {
        simpleMediaServiceHandler.mayBeSaveRecentSong()
        simpleMediaServiceHandler.mayBeSavePlaybackState()
        pauseAllPlayersAndStopSelf()
        // Disconnect System UI controllers as well as stopping the foreground service.
        // Home and screen-off do not remove the task, so their playback continues.
        mediaSession?.release()
        mediaSession = null
        simpleMediaServiceHandler.stopPlaybackForAppExit()
    }

    // Can't inject by Koin because it depend on service
    @UnstableApi
    private fun provideMediaLibrarySession(
        service: MediaLibraryService,
        player: Player,
        callback: MediaLibrarySession.Callback,
    ): MediaLibrarySession {
        val builder = MediaLibrarySession.Builder(service, player, callback)
            .setId(this.javaClass.name)
            .setBitmapLoader(coilBitmapLoader)
        // Also available when the service is started without an Activity binding.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }
}
