# Search cards audit — 9 October 2026

Scope: Search recommendations and results (songs, videos, albums, artists, playlists, podcasts, people), public playlists, genres and moods. Existing filters and backend search/paging behaviour are retained.

## Findings and changes

- Fixed-size browse cards allowed long titles and enlarged text to overlap decorative covers. Cards now reserve separate space for artwork, titles and secondary information and grow with content. Cards in the same grid row share a height. Grid columns follow the available window width and font setting, including narrow/split-screen layouts.
- Mood titles were converted into lowercase hashtags without spaces. They now retain the original readable names and real browse parameters.
- Public playlists used unrelated stock photos when the cover was missing. They now use the actual cover, or a local music symbol, and show the creator when available. Genre/mood cards no longer download unrelated stock photography.
- Public playlists with empty IDs and categories with empty parameters could navigate to invalid destinations. Unreachable discovery items are filtered, valid items are deduplicated, and row keys are stable.
- Search songs required a swipe gesture with a strict offset to enqueue. Search-specific cards now provide distinct 48dp queue and options actions. Existing playback, queue handling, bottom sheet, album/artist/playlist/podcast navigation are preserved.
- Song/video/album metadata now identifies the result type, creator, available duration/year and explicit status. Empty metadata is omitted and missing titles are localized. Download status remains observed from the song repository.
- The old animation implied that a selected song was playing even when playback was paused. The current item retains its cover and receives a static accent and accessible “Current track” description.
- Recommendations use the same result presentation. People cards use the existing validated avatar component, readable names and profile navigation.
- Pull-to-refresh now clears its local busy flag in a `finally` block.
- Added matching English and Indonesian resource strings, including the legacy Indonesian locale alias.

## Verification

Production components are exercised with local sample data; native checks do not log into an account or write user playlists. All 25 JVM tests pass: SearchCardsTest (4), SearchRenderTest (6), SearchAuditTest (10), SearchUsersPagingTest (4), and SearchPickerIsolationTest (1). JVM rendering covers 320dp at 150% and 200% font scale, 390dp, and 1100dp, including equal-height grid rows with different title lengths. Validation results and the signed debug APK are saved under `artifacts/search-cards-2026-10-09`.

The final signed APK also passes all 12 native touch checks on the API 37 emulator: 48dp action targets, queue/options without opening the track, row opening, and browse opening, at approximately 411dp/default text and 320dp/150% text. Native screenshots confirm matching heights within each grid row. Full Android debug assembly and APK v2 signature verification pass. The final APK SHA-256 is `508D892BEEF77A96A03377822CA7EA7F7713212CD1C15B0BEAA6BC7C216BD6A0`.

The APK includes the earlier login/optional YouTube connection, Supabase TLS and playback handoff fixes. Google OAuth verification still requires the separate real consent demonstration and Google's review.
