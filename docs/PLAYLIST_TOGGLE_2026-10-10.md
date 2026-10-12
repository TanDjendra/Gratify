# Playlist add/undo toggle — 2026-10-10

The song picker and inline recommendations now use a membership toggle. Tapping + saves the song immediately and shows an active checkmark. Tapping the checkmark removes the song from the playlist and restores +. Existing songs in the picker can also be removed and added again.

Newly added inline recommendations stay visible with their checkmarks, so users can undo without opening another screen. Membership agrees across recommendations, recent songs, liked songs, and search results. Playlist detail refreshes on additions and removals. The added count tracks songs absent from the initial playlist; undo resets that count and restoring an original song does not inflate it. Done remains available after a confirmed removal or undo.

Each song is locked while its storage operation runs. Operations share the existing serialized queue and account guards. Storage errors retain the last confirmed membership and provide the correct add/remove retry message. Switching accounts cancels pending removals and queued additions.

The new repository removal entry point takes the initiating owner explicitly and uses the existing atomic Room membership/pair-position/outbox transaction. Public playlist updates retain the same identity and visibility; failed cloud uploads keep the latest membership queued for retry. Optional existing YouTube mirroring is asynchronous and best effort.

## Validation

- 30 JVM tests passed: 14 playlist view model tests, 3 flow rendering tests, 5 management rendering tests, and 8 Room/repository/public-sync tests. Coverage includes add–undo–readd, original membership restoration, failed undo/retry, repeated pending taps, account switching, atomic positions, rollback, and public sync identity/visibility.
- 37 native checks passed on the API 37 emulator at 411dp / font scale 1.0 and 320dp / font scale 1.5. Search and inline recommendations both support undo; failed undo retains an active checked row and the confirmed count.
- Native checks use production components/view model/navigation with synthetic repositories and artwork. Room/public-sync checks use a temporary real database and loopback HTTP. No real account or production playlist was modified.
- Fresh universal debug APK built successfully; signature v2 verified. The previous welcome login, original artwork loader, and Gratify loading logo remain compiled in the APK; the removed AI cover atlas remains absent.

APK: `artifacts/playlist-toggle-2026-10-10/Gratify-debug-playlist-toggle-20261010.apk`, version 2.1.0-dev (61), minimum SDK 26, target SDK 36.

SHA256: `ABA99880E5C0389D5FD24E8A4C34D689B006B80F16C35ECB26405BFCA6E0235E`.
