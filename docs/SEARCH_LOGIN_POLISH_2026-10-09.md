# Search and login layout polish — 2026-10-09

## Behavior

- Search landing uses a compact, single-line field with a 48dp minimum height and a shorter localized hint. Typing, clear, Search IME, query limit, and category filters retain their existing actions.
- The landing field collapses after browsing scroll leaves the top, and returns at the top. It stays composed during collapse so external People requests and category shortcuts can focus it. Active input and search results keep controls available.
- Public playlists appear before source shortcuts and genre/mood categories. They use the same colored tile, corner shape, typography, and tilted artwork as category tiles, while preserving the actual thumbnail, creator, and SharedPlaylistDestination ID. Loading/error/empty/retry states remain available.
- Login uses a centered heading, narrower content, consistent provider button widths, email action, divider, Google action, optional YouTube checkbox, and signup link. Layout reference: https://accounts.spotify.com/en/login . Only supported Gratify providers are shown.
- Email login, signup, password recovery, OTP, Google completion, and optional YouTube scope selection retain the existing behavior.

## Validation

40 JVM tests passed across Search controls/cards/artwork/state, authentication layout, OTP, and Google/YouTube scope behavior. 50 native checks passed on the Android emulator at 411dp / font scale 1.0 and 320dp / font scale 1.5. Checks cover compact height, collapse/restore, stable header, correct public card action, source-triggered focus, selected filter, typing, submit, clear, aligned login choices, YouTube opt-in persistence, masked password, invalid-submit lockout, and signup navigation. Native previews exercise production components with debug-local sample playlist data; authentication navigation never submits credentials or launches Google OAuth.

APK: `artifacts/search-login-polish-2026-10-09/Gratify-debug-search-login-polish-20261009.apk`, version 2.1.0-dev (61), target SDK 36, minimum SDK 26. APK signature v2 verified; bundled DEX includes SearchScrollingControls, SearchCategoryTilesKt, and FriendsNoteEditor.

SHA256: `A944C857FE7728133153CBA9A2FE4E898EF5EC7E27ED931F538104F9162CCC79`.
