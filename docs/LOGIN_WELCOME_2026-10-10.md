# Login welcome screen — 2026-10-10

The newer [centered mobile layout](LOGIN_CENTERED_2026-10-10.md) supersedes the lower-positioned welcome layout described here. Validation below records the earlier APK.

The welcome screen follows the user's attached Spotify mobile reference: dark background, white Gratify mark, centered two-line headline, green pill-shaped signup button, outlined Google button, and a plain sign-in action. Content sits near the bottom of tall screens and scrolls when the available height is small. Width is capped at 400dp; actions retain accessible touch targets and adapt to large text.

“Daftar gratis” opens the existing signup flow; “Masuk” opens the email/password form. Google remains the supported external provider. Optional YouTube consent stays off initially, survives returning from email login, and locks while Google authentication or completion is in progress. Error, cancel, and completion retry states remain intact. The welcome screen exposes a quieter privacy link; full terms and privacy links remain on signup.

No authentication endpoints, scopes, account data, OTP behavior, or provider credentials were changed. The Google verification demo guide now links the latest APK and uses its actual button labels.

## Validation

- 15 existing JVM tests passed: authentication rendering/navigation callbacks and input/consent guards, Google login controller, and optional YouTube scope behavior.
- 29 native checks passed using the production authentication navigation on the API 37 emulator at 411dp / font scale 1.0 and 320dp / font scale 1.5, including Indonesian previews, signup/email navigation, optional YouTube persistence, empty-credential lockout, and privacy dialog access.
- Screenshots were visually inspected. No credentials were submitted and no real Google OAuth grant was started during these checks.
- Fresh universal debug APK built successfully; signature v2 verified; compiled welcome and prior playlist/artwork/loading/Friends components verified. Removed AI cover atlas remains absent.

APK: `artifacts/login-welcome-2026-10-10/Gratify-debug-login-welcome-20261010.apk`, version 2.1.0-dev (61), minimum SDK 26, target SDK 36.

SHA256: `EBA205CD56D242466B3B8A392FAAE24953776463CBD7BF701F4A6D041697F0A9`.

Previews and validation outputs: `artifacts/login-welcome-2026-10-10/`.
