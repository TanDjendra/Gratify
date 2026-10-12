# Centered mobile welcome — 2026-10-10

Following the user's physical-phone screenshot and request to center the login page, the logo, headline, signup/Google/sign-in actions, optional YouTube choice, and privacy footer now form a vertically centered group in the available safe screen area. The existing horizontal alignment, spacing, colors, and 400dp maximum width remain. Short screens and large text retain scrolling.

Validation: 6 existing authentication rendering/navigation/input tests passed. 32 native checks passed on the API 37 emulator at 411dp / font scale 1.0 and 320dp / font scale 1.5. Checks verify vertical balance, visible aligned actions, optional YouTube selection/persistence, masked email login, signup navigation, privacy access, and Indonesian labels. Native screenshots were visually inspected. No credentials or real Google OAuth grant were submitted.

Fresh universal debug APK built successfully; signature v2 verified. The earlier playlist add/undo toggle, original YouTube Music artwork loader, and Gratify loading logo remain compiled; the removed AI cover atlas is absent.

APK: `artifacts/login-centered-2026-10-10/Gratify-debug-login-centered-20261010.apk`, version 2.1.0-dev (61), minimum SDK 26, target SDK 36.

SHA256: `7D2EAFB58F56AD020776A0A63217E4F0493AE09D4CD0C2DA64458D5FD265B6C0`.

Previews: `artifacts/login-centered-2026-10-10/native-welcome-id-phone.png` and `native-welcome-id-small-large-text.png`.
