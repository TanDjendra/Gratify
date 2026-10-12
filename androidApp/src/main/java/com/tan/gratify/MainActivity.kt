package com.tan.gratify

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eygraber.uri.toKmpUriOrNull
import com.tan.common.FIRST_TIME_MIGRATION
import com.tan.common.SELECTED_LANGUAGE
import com.tan.common.STATUS_DONE
import com.tan.common.SUPPORTED_LANGUAGE
import com.tan.common.SUPPORTED_LOCATION
import com.tan.domain.data.model.intent.GenericIntent
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.gratify.di.AndroidActivityProvider
import com.tan.gratify.di.androidActivityModule
import com.tan.logger.Logger
import com.tan.media3.di.setServiceActivitySession
import com.tan.gratify.service.test.notification.NotifyWork
import com.tan.gratify.utils.VersionManager
import com.tan.gratify.viewModel.SharedViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.context.loadKoinModules
import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    val viewModel: SharedViewModel by inject()
    val mediaPlayerHandler by inject<MediaPlayerHandler>()
    private val supabase: SupabaseClient by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* Android settings remain reachable from the notification inbox. */ }

    private var mBound = false
    private var shouldUnbind = false
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
//                mediaPlayerHandler.setActivitySession(this@MainActivity, MainActivity::class.java, service)
                setServiceActivitySession(this@MainActivity, MainActivity::class.java, service)
                Logger.w("MainActivity", "onServiceConnected: ")
                mBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Logger.w("MainActivity", "onServiceDisconnected: ")
                mBound = false
            }
        }

    override fun onStart() {
        super.onStart()
        AndroidActivityProvider.attach(this)
        startMusicService()
    }

    override fun onResume() {
        super.onResume()
        com.tan.gratify.expect.AndroidAppUpdater.observe()
        checkForUpdate()
    }

    override fun onPause() {
        com.tan.gratify.expect.AndroidAppUpdater.stopObserving()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (shouldUnbind) {
            unbindService(serviceConnection)
            shouldUnbind = false
            mBound = false
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("gratify_update", false) == true) {
            com.tan.gratify.expect.openUpdateStatus()
            intent.removeExtra("gratify_update")
        }
        val extras = intent?.extras ?: return
        val title = extras.getString("title") ?: extras.getString("gcm.notification.title") ?: extras.getString("gcm.n.title")
        val body = extras.getString("body") ?: extras.getString("gcm.notification.body") ?: extras.getString("gcm.n.body")
        val isPush = title != null || body != null || extras.containsKey("google.message_id") || extras.getBoolean("is_developer_notification")
        if (!isPush) return
        if (!extras.getBoolean("already_saved") && (title != null || body != null)) {
            com.tan.gratify.service.DeveloperNotificationWorker.enqueue(this, title ?: "Gratify", body.orEmpty(),
                extras.getString("url") ?: extras.getString("browseId"), show = false)
        }
        if (intent.data == null) intent.data = "gratify://notification".toUri()
        listOf("is_developer_notification", "already_saved", "title", "body", "url", "browseId",
            "gcm.notification.title", "gcm.notification.body", "gcm.n.title", "gcm.n.body", "google.message_id")
            .forEach(intent::removeExtra)
    }

    private fun handleAuthenticationIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme == packageName && uri.host == "youtube-connected") return true
        if (uri.scheme != packageName || uri.host != "login-callback") return false
        val fragment = "https://callback.invalid/?${uri.fragment.orEmpty()}".toUri()
        val recovery = uri.getQueryParameter("flow") == "recovery" ||
            uri.getQueryParameter("type") == "recovery" || fragment.getQueryParameter("type") == "recovery"
        if (recovery) com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator.request()
        supabase.handleDeeplinks(intent) { session ->
            if (recovery) session.user?.id?.let(com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator::verify)
        }
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Logger.d("MainActivity", "New intent received")

        // CRITICAL: Serahkan deep link ke Supabase Auth agar OAuth callback token bisa diproses.
        // Tanpa ini, SDK tidak pernah menerima hasil autentikasi dari browser.
        if (handleAuthenticationIntent(intent)) return

        handleNotificationIntent(intent)
        viewModel.setIntent(
            GenericIntent(
                action = intent.action,
                data = (intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull(),
                type = intent.type,
            ),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.intent.value?.data?.toString()?.let { com.tan.domain.utils.parseWorkspaceInvitation(it) }?.let {
            outState.putString("pending_workspace_invitation", it)
        }
        super.onSaveInstanceState(outState)
    }

    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidActivityProvider.attach(this)
        loadKoinModules(androidActivityModule)
        // Application owns the shared definitions; activity recreation preserves playback state.
        VersionManager.initialize()
        checkForUpdate()
        if (viewModel.recreateActivity.value || viewModel.isServiceRunning) {
            viewModel.activityRecreateDone()
        } else {
            startMusicService()
        }
        Logger.d("MainActivity", "onCreate: ")

        // CRITICAL: Handle deep link dari cold start (app tidak di memory)
        // saat browser redirect kembali ke app setelah OAuth.
        val authenticationCallback = handleAuthenticationIntent(intent)
        // Preserve an unopened invite across activity/process recreation during login.
        // Once Main consumes it, setIntent(null) prevents replay on the next recreation.
        val pendingInvite = savedInstanceState?.getString("pending_workspace_invitation")
        if (viewModel.intent.value == null && pendingInvite != null &&
            com.tan.domain.utils.parseWorkspaceInvitation("gratify-next://invite?token=$pendingInvite") != null) {
            viewModel.setIntent(GenericIntent(action = Intent.ACTION_VIEW,
                data = com.eygraber.uri.Uri.parse("gratify-next://invite?token=$pendingInvite")))
        }

        if (savedInstanceState == null) handleNotificationIntent(intent)
        val data = (intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull()
        if (data != null && !authenticationCallback && savedInstanceState == null) {
            viewModel.setIntent(
                GenericIntent(
                    action = intent.action,
                    data = data,
                    type = intent.type,
                ),
            )
        }
        Logger.d("Italy", "Key: ${Locale.ITALY.toLanguageTag()}")

        lifecycleScope.launch {
            // Check if the migration has already been done or not
            if (getString(FIRST_TIME_MIGRATION) != STATUS_DONE) {
                Logger.d("Locale Key", "onCreate: ${Locale.getDefault().toLanguageTag()}")
                if (SUPPORTED_LANGUAGE.codes.contains(Locale.getDefault().toLanguageTag())) {
                    Logger.d(
                        "Contains",
                        "onCreate: ${
                            SUPPORTED_LANGUAGE.codes.contains(
                                Locale.getDefault().toLanguageTag(),
                            )
                        }",
                    )
                    putString(SELECTED_LANGUAGE, Locale.getDefault().toLanguageTag())
                    if (SUPPORTED_LOCATION.items.contains(Locale.getDefault().country)) {
                        putString("location", Locale.getDefault().country)
                    } else {
                        putString("location", "US")
                    }
                } else {
                    putString(SELECTED_LANGUAGE, "en-US")
                }
                // Fetch the selected language from wherever it was stored. In this case its SharedPref
                getString(SELECTED_LANGUAGE)?.let {
                    Logger.d("Locale Key", "getString: $it")
                    // Set this locale using the AndroidX library that will handle the storage itself
                    val localeList = LocaleListCompat.forLanguageTags(it)
                    AppCompatDelegate.setApplicationLocales(localeList)
                    // Set the migration flag to ensure that this is executed only once
                    putString(FIRST_TIME_MIGRATION, STATUS_DONE)
                }
            }
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() !=
                getString(
                    SELECTED_LANGUAGE,
                )
            ) {
                Logger.d(
                    "Locale Key",
                    "onCreate: ${AppCompatDelegate.getApplicationLocales().toLanguageTags()}",
                )
                putString(SELECTED_LANGUAGE, AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
        }

        enableEdgeToEdge(
            navigationBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
            statusBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
        )
        viewModel.checkIsRestoring()
        val request =
            PeriodicWorkRequestBuilder<NotifyWork>(
                12L,
                TimeUnit.HOURS,
            ).addTag("artist-release-notifications")
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "Artist Worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        lifecycleScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (getString("notification_permission_asked") != "true") {
                    putString("notification_permission_asked", "true")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (getString("notification_permission_do_not_ask") != "true" &&
                    getString("notification_permission_reminder_shown") != "true") {
                    putString("notification_permission_reminder_shown", "true")
                    viewModel.showNotificationPermissionDialog()
                }
            }
        }
        viewModel.getLocation()

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        AndroidActivityProvider.detach(this)
        if (isFinishing && !isChangingConfigurations) {
            com.tan.media3.di.stopService(this)
            viewModel.isServiceRunning = false
        }
        super.onDestroy()
        Logger.d("MainActivity", "onDestroy: ")
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.activityRecreate()
    }

    private fun startMusicService() {
        if (shouldUnbind) return
//        mediaPlayerHandler.startMediaService(this, serviceConnection)
        val bound = com.tan.media3.di
            .startService(this@MainActivity, serviceConnection)
        if (!bound) {
            Logger.w("Service", "Music service binding failed; retry on next Activity start")
            return
        }
        installPlaybackCallbacks(mediaPlayerHandler, viewModel)
        viewModel.isServiceRunning = true
        shouldUnbind = true
        Logger.d("Service", "Service started")
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            if (viewModel.shouldCheckForUpdate()) viewModel.checkForUpdate()
        }
    }

    private suspend fun putString(
        key: String,
        value: String,
    ) {
        viewModel.writePreference(key, value)
    }

    private suspend fun getString(key: String): String? = viewModel.readPreference(key)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.activityRecreate()
    }
}
