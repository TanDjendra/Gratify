# Android Now Playing audit and implementation

Audit date: 2026-10-04. Source checked before implementation; unrelated existing workspace edits were preserved.

## Feasibility and existing architecture

The current source supports native background playback. An updated APK must be built and installed; an already distributed APK cannot acquire these changes without an update.

- Kotlin Multiplatform/Compose UI uses `MediaPlayerHandler` and `MediaPlayerInterface`, provided as application-scoped Koin singletons.
- Android uses Media3 **1.10.1**, with `CrossfadeExoPlayerAdapter` managing multiple ExoPlayer instances and a stable `DelegatingForwardingPlayer` exposed to `MediaLibrarySession`.
- `SimpleMediaService` already extends `MediaLibraryService` and uses `DefaultMediaNotificationProvider`. The app manifest already declares `mediaPlayback`, `stopWithTask=false`, network/wake-lock permissions, and the MediaSession/MediaLibrary/browser service actions.
- Song and generic media mappings already supply title, artist, album, and artwork URI. `CoilBitmapLoader` supplies software bitmaps for the session/notification. Artwork remains dependent on a valid URI and its availability in cache or on the network.
- ExoPlayer uses network wake mode and handles headphones becoming disconnected. Audio focus belongs to the crossfade adapter, so releasing an outgoing player does not abandon focus for the incoming track.
- Home/minimize does not intentionally pause playback. Previously, swipe-away defaulted to `killServiceOnExit=true`, released shared handler scopes, and called `exitProcess(0)`. Service destruction only cleaned up conditionally. This conflicted with background playback and reuse in the same process.
- Next/Previous were dispatched by both the session callback and the forwarding player. Play/Pause from system controllers bypassed the adapter's crossfade and audio-focus handling. A service-owned self-controller was never released, and Activity binding could be registered twice.

## Changes

1. Keep the existing player, MediaLibraryService, notification provider, metadata, artwork loader, queue, download/cache, equalizer, and crossfade architecture.
2. Route session Play/Pause/stop/repeat/shuffle through the same adapter as the UI; route queue navigation once through its existing queue provider.
3. Check audio-focus grants before starting audio, including track loads and crossfade. A deliberate pause cancels automatic resume following transient focus loss.
4. Default `killServiceOnExit` to false. Explicit saved user preferences are retained; turn off **Kill service on exit** if it was previously enabled. The shared preference default also applies to desktop; no desktop player code changes.
5. Preserve ongoing playback after task removal. Stop through Media3 when requested, without killing the app process, and release the session to disconnect external controllers when stopping. The existing keep-alive option can retain a paused session during Media3's foreground inactivity window; an inactive background service is always stopped on task removal. Media3 manages foreground promotion and its inactivity timeout rather than keeping a paused notification in the foreground forever.
6. Explicitly register the session with the service using `addSession`: this app controls its adapter directly, so an external controller cannot be relied upon to register it for foreground/notification management. Always release the service-owned MediaSession and subscriptions at service destruction. Stop both playback players and abandon audio focus; retain the application-owned adapter/handler and their shared scope for reuse. Re-prepare an idle player on subsequent play.
7. Provide a session launch PendingIntent even without an Activity binding, remove the leaked self-controller, and balance Activity bind/unbind registrations.
8. Explicitly declare the base foreground-service permission. Keep the native media notification and lock-screen integration. Add a consumer rule for the existing reflective crossfade delegate field in minified builds.
9. Remove the unsupported cold-start `MediaButtonReceiver`: this source has no `onPlaybackResumption` implementation and its asynchronous queue restoration cannot satisfy Media3's resumption contract yet. Active-session headset/Bluetooth/system controls continue through MediaSession. Restart after process death/reboot via a headset is a separate, unimplemented feature; reopening the app retains the existing saved queue restoration behavior.
10. Deliver aggregated player events and the new timeline when the crossfade delegate changes, keeping system metadata and available buttons synchronized. Execute transport mutations immediately on the player main thread. Use valid semantic Media3 icons for the existing custom buttons. Pause the entire crossfade adapter when a headphone disconnect event arrives.
11. Fix a reproduced crossfade cancellation bug: an interrupted volume ramp must not release the incoming player after pause/skip has adopted it as the current player. Otherwise resume can become stuck buffering.
12. Declare the media module's own existing notification/scheduler permissions so its lint analysis agrees with the app manifest. Document the deliberate always-active audio filter override for lint without changing its behavior.

## Android and OEM support

| Feature | Support and ownership |
| --- | --- |
| Minimum Android | Existing APK minimum remains Android 8.0, API 26; target SDK 36, compile SDK 37. No new library or minimum SDK increase. |
| Background playback | Native Media3 mediaPlayback foreground service, including minimize and ongoing playback after swipe-away unless the saved stop-on-exit setting is enabled. |
| Title, artist, artwork; Play/Pause/Next/Previous | MediaSession and MediaStyle notification. Navigation availability follows the queue; Previous can restart the current song according to the existing player behavior. |
| Lock screen and headset/Bluetooth commands | Android MediaSession integration. Appearance depends on system UI, notification settings, and the lock screen. Physical Bluetooth hardware still requires device testing. |
| Android 13 notification permission | Existing runtime request is retained for ordinary app notifications. Media-session notifications are exempt from the POST_NOTIFICATIONS behavior change; audio playback is not gated on granting it. |
| Android 16 Live Updates | Promoted ongoing notifications have specific eligible styles. MediaStyle is not an eligible style in the Android 16 framework. No duplicate progress notification or promoted permission is added for music. |
| Vivo/iQOO pill or punch-hole UI | OEM/system-owned behavior, varying with device, OS version, region, settings, and supported apps. A standards-compliant media session supplies metadata/controls the OEM may use; this does not guarantee Gratify is accepted or shown in the island. No universal public Android API positions a media island or forces OEM eligibility. |

Force-stop in Android Settings, the system's active-app Stop action, reboot, or OS process termination may stop playback. A foreground service does not guarantee survival against these actions or OEM battery policies.

## Sources

- [Media3 background playback and resumption](https://developer.android.com/media/media3/session/background-playback)
- [Android notification permission: media-session exemption](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)
- [Android 16 framework notification implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/java/android/app/Notification.java)
- [Vivo OriginOS: device/version-dependent feature availability](https://www.vivo.com/id/originos)

## Verification

Verified against the final production source on the Android API 37 emulator (`emulator-5554`), using offline AAC fixtures through the real Koin player, cache, MediaLibrarySession, and service. No physical device installation was performed.

- `:media3:testDebugUnitTest`, `:androidApp:assembleDebug`, `:media3:lintDebug`, and `:androidApp:assembleRelease` completed successfully. Four JVM tests passed, including three new transport/navigation regression tests. Module lint: zero errors, eight warnings (including existing resource warnings and debug fixture recommendations).
- All **17 native regression checks** passed: initial metadata/playback, Home, single-step Next/Previous, system Pause/Play, task removal, foreground `mediaPlayback` service, native MediaStyle title/artist/bitmap artwork, crossfade metadata, pause/resume during a fade, transient focus loss/gain, deliberate pause preventing focus resume, explicit stop-on-exit, and player reuse after service recreation.
- The native lock-screen media card was visually inspected. System transport Pause/Play also worked while locked. Evidence: `artifacts/now-playing/lockscreen.png`, `session-lockscreen-pause.txt`, and `session-lockscreen-play.txt`.
- With `POST_NOTIFICATIONS` revoked, playback remained PLAYING and the media foreground notification remained present. The lock-screen card was captured under this condition. Permission and lock-screen settings were restored afterward. Evidence: `session-permission-denied.txt`, `notification-permission-denied.txt`, and `service-permission-denied.txt`.
- Release shrinking preserves `ForwardingPlayer.player`, confirmed in the final R8 mapping. Debug fixture audio is absent from the release APK; the audit Activity exists only in the debug source set.
- Repeatable checks: `scripts/verify-android-media.ps1`. Machine-readable outcomes: `artifacts/now-playing/verification.json`. Final combined build log: `artifacts/now-playing-build-complete.log`.

Debug APK: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk` (`com.tan.gratify.dev`, test build). Release APK: `androidApp/build/outputs/apk/release/androidApp-universal-release-unsigned.apk`; it requires the application's production signing key before distribution or an upgrade of the installed release app. No signing, publication, or deployment was performed.

Physical headset unplug events, Bluetooth hardware, Vivo/iQOO island eligibility, and the full Android/OEM version matrix still require device testing. Offline regression fixtures do not validate every streaming provider or network recovery scenario. Active-session transport support is implemented; cold resumption after process death/reboot is outside this change.

## Connected iQOO device follow-up

After the initial emulator verification, the user authorized USB installation. The ARM64 debug APK (`2.1.0-dev`, version code 61) was installed successfully and MainActivity opened on the connected I2218 device. Its reported Android version is 16 and firmware is `PD2361BF_EX_A_16.2.11.0.W20`. The user reports that Spotify appears in the native island.

Read-only inspection during the user's Gratify playback confirmed:

- Gratify's MediaSession is active and PLAYING; its native mediaPlayback service is foreground.
- Its native MediaStyle notification contains a title, artist, and bitmap artwork.
- Vivo's current music-control selection points to `com.spotify.music`; its additional music-type preference is empty. These preferences show the current selection, not conclusive proof of the complete supported-app list.
- The device's `com.vivo.notification.ISLAND_NOTI_SUPPORT` permission is protected by `signature`. The IslandService access permission is `signature|privileged`. These direct internal entry points cannot be granted to an ordinary Gratify APK. This does not imply Spotify holds these permissions; OEM components can consume approved apps' standard MediaSessions.

The strongest working explanation is OEM music-app selection/eligibility rather than a missing standard media session. Actual eligibility still needs confirmation in Origin Island's supported music-app settings. No phone preferences, app identities, or OEM allowlists were changed. No undocumented island integration was added.

A [Salt Player maintainer response](https://github.com/Moriafly/SaltPlayerSource/issues/1941) also reports that third-party music support depends on the OriginOS version's whitelist. This is corroborating developer experience, not a Vivo guarantee for Gratify or this firmware. I did not find a public documented Vivo API for automatic registration of Gratify as an eligible island music app. The official route is the device's exposed music-app selection, if Gratify is offered, or OEM support/approval for Gratify's own package identity.
