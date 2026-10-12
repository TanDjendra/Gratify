# YouTube Music artwork — 10 October 2026

User preference: use original YouTube Music artwork for music imagery; do not generate replacement music photos with AI.

Subsequent placeholder revision: the user requested Gratify's loading logo instead of initials. See [Gratify loading cover](LOADING_COVER_2026-10-10.md) for the latest behavior and APK; the validation below records the earlier artwork-source revision.

The bundled AI cover atlas was removed from application resources. Search cards, browse tiles, and immediate playlist song rows now share a neutral initials placeholder while their original remote artwork loads. Missing or failed image requests retain initials rather than substituting an unrelated genre illustration. User-selected playlist covers remain supported; ordinary playlist collages already use the songs' YouTube thumbnails.

Search category covers are fetched from the playlist thumbnails in the corresponding YouTube Music browse response, keyed by the server's category parameters. Only composed category rows request metadata. A screen-scoped cache deduplicates pending and successful requests; a semaphore limits them to two concurrent requests. Requests time out after 12 seconds, and failed requests can retry after 30 seconds when a category is revisited. Navigating away cancels the work. The Music shortcut uses a returned discovery item's thumbnail; the Podcast shortcut uses an actual podcast search result's thumbnail.

The read-only debug artwork preview loads live YouTube Music category names and cover URLs using the same production repositories and artwork loader. It performs no account writes.

Live Android verification also exposed a startup race: applying an unchanged disabled proxy closed the YouTube HTTP client while the first category request was running (`Parent job is Completed`). Proxy updates now keep the existing client when the setting is equivalent; actual proxy changes still replace it. Dedicated client lifecycle tests cover both cases.

Final build, tests, package checks, and live preview evidence are recorded in `artifacts/yt-artwork-2026-10-10/`.

Final validation: **20 JVM tests**, zero failures/errors, and **4 native Android checks** passed. Cold-start live metadata successfully loaded original covers; the visually reviewed screenshot shows twelve covers from YouTube Music. Missing covers were also checked at enlarged text size. Native verification used the API 37 Android emulator, not a physical phone.

APK: `artifacts/yt-artwork-2026-10-10/Gratify-debug-yt-artwork-20261010.apk`, 104,587,599 bytes, APK v2 signature verified. Package inspection confirms the AI atlas is absent and the earlier playlist management, Search controls, and Friends Notes components remain compiled. SHA-256: `F4F76BDD6987746490EF87D623B231D2650DD1CCDF09298DE9494FD4BD36C0EC`.
