# Playlist creation and immediate additions — 9 October 2026

The production entry from Library now asks only for a playlist name. Creation persists an empty owner-scoped playlist and replaces the creation destination with that playlist detail. Existing restored builder selections are explicitly excluded from this action. The old batch selection UI is no longer used by the production entry.

Owned playlist details use a compact cover/title header, an Edit entry for the existing title/cover menu, a prominent Add to this playlist button, and up to six recommendations with individual + controls. Empty playlists omit sorting, reordering, and download controls. Imported saved playlists remain read-only.

The add page has Songs (related music and listening-library suggestions), Recently played, and Liked songs. Search is anchored at the bottom and searches actual song/artist results. Unsupported Episode or collaborator features are not exposed. Actual track covers have priority; bundled title-themed artwork or title initials handle unavailable covers.

Each + immediately invokes `addTracksToLocalPlaylistForOwner` using the captured current account. Pending track IDs block duplicate taps; writes are serialized in tap order. Membership changes only after repository success. A zero append result is treated as existing membership because the repository atomically deduplicates. Failures leave an enabled + with a retry message. Account changes cancel pending operations and clear private discovery data. Writes have a bounded timeout. Returning from the picker refreshes the owning playlist.

The repository's atomic songs/pairs/playlist/outbox transaction and public-playlist synchronization are reused. Public playlist contents therefore follow local additions after successful synchronization; offline changes stay queued. No new cloud publishing action is introduced.

Suggestions use related-song results when a seed exists, plus actual recent and liked songs. Empty listening histories offer song search instead of fabricated songs. Network and repository failures remain distinguishable from empty results.

Validation artifacts: `artifacts/playlist-flow-2026-10-09/`. JVM checks cover production creation/picker components, large text/narrow layouts, failure/pending/account states, owner-scoped immediate persistence, and repository/public-sync safeguards. Android checks use the production screens/navigation and detail components with synthetic repositories, without modifying real accounts or user data. These are emulator checks, not a claim of physical-device playback verification.

Final result: 34 JVM tests and 39 Android emulator checks passed; debug APK built and APK v2 signature verified. See artifact summary.json for the exact SHA-256 and build timestamp.
