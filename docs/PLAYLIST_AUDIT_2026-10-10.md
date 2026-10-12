# Playlist flow and management audit — 10 October 2026

## Findings and changes

- The previous Edit entry opened a menu containing title, cover, queue, YouTube synchronization, visibility, deletion, and a disabled Sync first share action. The owned playlist now opens a dedicated name/cover editor, while Options groups the other actions. Queue is omitted when empty; YouTube connection is a separate expandable section. Connected playlists retain update, disconnect, and YouTube link sharing actions.
- Privatization/publication was only visible deep in the menu. Owned details now expose a Private/Public entry. Unknown visibility is explicitly shown as Check visibility rather than being guessed. The confirmation explains that changes to the public version follow after synchronization, and that hiding preserves the owner's playlist. Requests are bounded, duplicate visibility requests are blocked, and late metadata reads cannot overwrite a confirmed visibility change.
- A failed rename previously closed its editor. The editor now retains the draft until confirmed, trims names, limits new names to 100 characters, disables blank/unchanged submissions, and presents an inline retry error. Cover changes remain immediate after upload, with this behavior explicitly described.
- Owned playlist deletion now requires confirmation, including public-access consequences. Duplicate deletion attempts are blocked while the first is pending, and confirmed navigation runs once.
- The song picker now shows the confirmed total and number added, and offers Done after successful immediate additions. Existing individual +, membership checks, pending/error/retry, owner isolation, search, recent songs, liked songs, and creation navigation remain in place.
- Opening one's own public playlist offers Edit playlist back to the matching owned library record. Matching uses the stable client sync identity, not local numeric IDs or titles. Saved playlists from other creators remain read-only.
- Song removal previously updated the playlist's track list and pair rows separately. It now runs as a single Room transaction: owner/editability checks, removal, normalized positions, latest track list, and metadata outbox revision. Public update flushing reuses the existing queue; failed uploads retry the latest contents without changing visibility. Concurrent additions cannot be lost to a stale removal write.

## Validation scope

Targeted JVM tests cover production management layouts and dialogs, actual editor/delete state logic, retained creation and picker flows, and Room/repository public synchronization. Native checks use production components/navigation with local sample repositories and counters; they do not publish or delete real account data. Two screen/text configurations are used: 411dp / font scale 1.0 and 320dp / font scale 1.5.

Final validation: **44 JVM tests passed** with zero failures/errors, and **83 native UI checks passed** (42 management, 41 creation/add-song checks). Native verification ran on Android emulator API 37; this run did not exercise physical devices or real account publishing/deletion.

The signed universal debug APK is `artifacts/playlist-audit-2026-10-10/Gratify-debug-playlist-audit-20261010.apk`: version 2.1.0-dev (61), minimum SDK 26, target SDK 36, compile SDK 37, three ABIs, 109,142,155 bytes. SHA-256: `067E99A98C61D65F6E41867084E0F0F98FEC4A95244ED06A662CA1D24E1EDF02`. The package's compiled DEX includes the new management/editor and transactional-removal code as well as the earlier Search and Friends Notes components.

Detailed evidence and screenshots are in `artifacts/playlist-audit-2026-10-10/verification.json` and the accompanying test logs.
