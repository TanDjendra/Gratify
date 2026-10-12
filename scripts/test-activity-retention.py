"""Non-Gradle integration guards for process-lived Android references.

These source guards complement the JVM provider test and parent Android verification;
they are not a device heap/GC or Activity recreation test.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "androidApp/src/main/java/com/tan/gratify/MainActivity.kt"
PROVIDER = ROOT / "composeApp/src/androidMain/kotlin/com/tan/gratify/di/AndroidActivityProvider.kt"
REFERENCE = ROOT / "composeApp/src/androidJvmMain/kotlin/com/tan/gratify/di/WeakActivityReference.kt"
CALLBACKS = ROOT / "androidApp/src/main/java/com/tan/gratify/PlaybackCallbacks.kt"


class ActivityRetentionTest(unittest.TestCase):
    def test_activity_definition_never_caches_or_captures_activity(self):
        main = MAIN.read_text(encoding="utf-8")
        self.assertNotRegex(main, r"single\s*<AppCompatActivity>")
        self.assertNotIn("single<AppCompatActivity> { this@MainActivity }", main)
        self.assertTrue(PROVIDER.exists(), "Activity resolver must have an explicit lifetime")
        provider = PROVIDER.read_text(encoding="utf-8")
        self.assertIn("factory<AppCompatActivity>", provider)
        self.assertIn("WeakReference", REFERENCE.read_text(encoding="utf-8"))
        self.assertIn("!it.isFinishing && !it.isDestroyed", provider)

    def test_destroy_detaches_only_its_own_activity(self):
        main = MAIN.read_text(encoding="utf-8")
        self.assertIn("AndroidActivityProvider.attach(this)", main)
        destroy = main.split("override fun onDestroy()", 1)[1].split("override fun onRestart()", 1)[0]
        self.assertIn("AndroidActivityProvider.detach(this)", destroy)
        before_destroy = main.split("override fun onDestroy()", 1)[0]
        self.assertNotIn("AndroidActivityProvider.detach", before_destroy,
                         "Permission/share handoff must not detach on pause/stop")
        self.assertLess(destroy.index("AndroidActivityProvider.detach(this)"), destroy.index("super.onDestroy()"))
        self.assertTrue(PROVIDER.exists())
        self.assertIn("activity.get() === owner", REFERENCE.read_text(encoding="utf-8"))

    def test_process_callbacks_are_installed_outside_activity(self):
        main = MAIN.read_text(encoding="utf-8")
        self.assertNotRegex(main, r"mediaPlayerHandler\.(?:showToast|pushPlayerError)\s*=")
        self.assertIn("installPlaybackCallbacks(mediaPlayerHandler, viewModel)", main)
        self.assertTrue(CALLBACKS.exists())
        callbacks = CALLBACKS.read_text(encoding="utf-8")
        self.assertNotRegex(callbacks, r"MainActivity|AppCompatActivity|this@")
        self.assertIn("sharedViewModel.makeToast(", callbacks)
        self.assertIn("pushPlayerError(error)", callbacks)
        self.assertIn("StringType.EXPLICIT_CONTENT_BLOCKED", callbacks)
        self.assertIn("StringType.TIME_OUT_ERROR, type.error", callbacks)

    def test_permissions_update_and_playback_lifetimes_remain_intact(self):
        main = MAIN.read_text(encoding="utf-8")
        for expected in (
            "registerForActivityResult(",
            "notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)",
            "AndroidAppUpdater.observe()",
            "AndroidAppUpdater.stopObserving()",
            "if (shouldUnbind) {",
            "unbindService(serviceConnection)",
            "if (isFinishing && !isChangingConfigurations)",
            "com.tan.media3.di.stopService(this)",
            "setServiceActivitySession(this@MainActivity, MainActivity::class.java, service)",
            "if (shouldUnbind) return",
            "loadKoinModules(androidActivityModule)",
        ):
            self.assertIn(expected, main)
        self.assertNotIn("unloadKoinModules", main)
        provider = PROVIDER.read_text(encoding="utf-8") if PROVIDER.exists() else ""
        self.assertNotRegex(provider, r"single<AppCompatActivity>")


if __name__ == "__main__":
    unittest.main()
