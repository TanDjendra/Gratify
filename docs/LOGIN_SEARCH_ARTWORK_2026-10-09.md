# Login presentation and contextual Search artwork — 9 October 2026

The login landing page now follows a simpler Spotify-inspired hierarchy: centered Gratify brand and bold heading, equal-width Google and email actions, restrained optional YouTube consent, and compact legal links. It retains Gratify branding and all existing authentication/state handling. The email form uses labels above rectangular fields, masked passwords, a green pill action, and aligned recovery/signup links. No authentication scopes, account writes or OTP behaviour are changed.

The optional YouTube choice remains off by default and is locked during an active Google request. Provider and email actions stay separate; connecting YouTube continues to require explicit user consent. Legal documents remain available. The contact email is omitted from the landing footer; other existing legal-link call sites keep their default behaviour.

Search covers now use a bundled, original AI-generated 4×4 artwork atlas (`search_cover_atlas_v1.png`). Each known genre or mood selects a corresponding illustration using English/Indonesian title words: pop, rock, hip-hop, jazz, classical, electronic, R&B, indie/retro, chill, sleep, focus, workout, love, travel, podcast, and world/Indonesian music. Matching uses whole words to avoid accidental mappings such as “Population” to pop or “Rockefeller” to rock. Illustrations do not require network access. The app renders the selected atlas region directly; the original generated image is preserved.

Actual remote song/album/artist/playlist covers remain above this fallback. Free-form titles without a recognized theme use a stable colored title monogram instead of a gray music-note placeholder. This does not fabricate official artist images or album covers. Browse cards retain separate text/artwork space, adaptive columns, matching row heights and valid navigation targets.

Reference for the login structure: [Spotify account login](https://accounts.spotify.com/en/login). Generated artwork provenance is retained in `artifacts/login-artwork-2026-10-09`.

## Verification

42 JVM checks pass: authentication rules, view models, Google controller/combined scopes, responsive authentication rendering, Search state, cover classification and Search card actions/layout. Rendered layouts cover 390dp, 320dp/150% text and 1100dp; existing card action checks also cover 320dp/200% text. APK assembly and native checks are recorded with the final debug APK under `artifacts/login-artwork-2026-10-09`.

The final APK passes 26 native checks on the API 37 emulator: 12 Search touch checks and 14 authentication navigation/presentation checks. Checks cover approximately 411dp/default text and 320dp/150% text, separate queue/options dispatch, matching provider/email button widths, default-off YouTube consent, correct combined-action label, email navigation, password masking and retained choice after returning. Native checks do not launch Google OAuth or submit credentials. Screenshots confirm the correct cover regions and the production landing/email screens.

Full Android debug assembly succeeds. APK v2 signature verification passes. Final APK: `Gratify-debug-login-artwork-20261009.apk`, built at 12:04 WIB, version 2.1.0-dev (61). SHA-256: `32B50CDEF6EE2A447CA92CD579C29AC808E0B0993B4B9209B1977E859BAEBC6A`. It includes the earlier playback and Supabase TLS fixes.

Google OAuth verification remains a separate pending review; changing this presentation does not make the app Google-verified.
