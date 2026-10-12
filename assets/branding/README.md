# Gratify logo — 3 October 2026

The new identity follows the supplied concept: a green circular disc with
rounded G-shaped cutouts and three audio bars (the middle bar is tallest).
Brand green: `#1ED760`. Icon background: `#101411`.

- `gratify-mark-source.png`: original transparent image from the built-in imagegen tool, preserved unchanged.
- `gratify-mark.svg`: editable native vector traced from the original silhouette.
- `gratify-mark.png`: transparent 1024 px mark.
- `gratify-app-icon.png`: 1024 px application tile.
- `generation-prompt.txt`: final prompt used to create the original mark.
- `gratify-concept-reference.png`: user-supplied concept, preserved unchanged.
- `gratify-mark-source-v1.png`: previous logo source, retained for recovery.

Platform assets are reproduced with:

```
python artifacts/branding/generate-platform-icons.py
```

Requires Pillow. The converter traces alpha contours, removes tiny isolated
generation artifacts, simplifies edges, and exports vector/XML, PNG, WebP,
ICO, and ICNS assets. The generated source is never overwritten.

Android adaptive and themed icons keep the mark inside a 31 dp radius within
the 108 dp layer. Notification icons use a separate full-size white silhouette.
The Android system splash uses the padded green variant as well.
Sizing follows the [Android adaptive icon specification](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
and [Android splash screen specification](https://developer.android.com/develop/ui/views/launch/splash-screen).
Shared app logos use green; cover placeholders use a neutral gray variant.

Applied to Android launchers (all five densities), Play Store/fastlane icon,
notifications, widget logo, splash, login, app dialogs, profile/about,
loading/cover fallbacks, desktop window/tray/mini player, and desktop packages.

Current concept validation: `artifacts/branding/assets-verification.json`
records the generated assets and source hash; `concept-build.log` records
Android resource and shared desktop build validation. The older
`release-verification.json` describes the previous logo's packaged release,
not this revision. Existing installed applications need a new build to show
the updated assets.
