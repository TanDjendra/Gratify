# Gratify website

Indonesian introduction and Android download website for Gratify, using the
new green logo and real application screenshots, including active Now Playing.
The player leads the desktop composition; on mobile, the three screenshots
form a swipeable gallery with native keyboard scrolling. Static HTML, CSS and
JavaScript; no app credentials, tracking scripts or sign-up forms.

The 11 October 2026 visual revision integrates organic forms into the page's
layout, inspired by the flowing forms and bold color blocks of Spotify Wrapped.
The headline has its own lime field, app screenshots sit inside a sculptural
stage, and feature content lives inside staggered organic surfaces. The download
heading and card form one composition. CSS geometry scales with the content;
there are no independent sticker illustrations in the current page. Subtle
entry and scroll reveals follow reading order and respect reduced motion.
Existing downloads, mobile gallery, legal pages, and menus remain available.

## Preview

From the repository root:

```text
python -m http.server 4173 --bind 127.0.0.1 --directory website/dist
```

Open http://127.0.0.1:4173/. The `dist` folder is the complete portable website.

## Release links

The APK button points to the verified GitHub v2.1.0 release asset. Update
its URL in `dist/index.html` when publishing a new release. The adjacent
release-notes link always points to GitHub's latest release page. Desktop
downloads are intentionally marked as in development.

## Hosting and domain

The Site identity is stored in `.openai/hosting.json`. The owner approved
public access on 4 October 2026. On that date, `gratify.web.id`
was registered with Sites and its two apex A records and two verification
TXT records were saved in IDwebhost. Nameservers remain ns1.idwebhost.id
and ns2.idwebhost.id. Sites reports the domain and HTTPS active. Google
Search Console verified domain ownership for the managing account after
an additional Google TXT record was saved. The public canonical URL is
https://gratify.web.id. Google OAuth is In production, and branding is
verified and published. The optional YouTube scope is still unverified
with Google's 100-user cap; submission awaits a real YouTube demo video.

Privacy and Terms pages are available at `/privacy/` and `/terms/`.
The owner approved Privacy on 4 October 2026; Terms retains its DRAFT label.
The privacy policy includes the optional read-only Google/YouTube connection,
encrypted server tokens, disconnect/revocation, stories, messaging and FCM.
Source documents and the identical app copy are maintained in the parent
repository. Refresh pages with `scripts/build-legal-site.py`, then copy the
rendered privacy and terms pages into `dist` before publishing.
The current OAuth status and video instructions are recorded in the parent
repository at `docs/YOUTUBE_GOOGLE_REVIEW_2026-10-04.md`.

The static files can also be hosted on another static hosting service or
uploaded to a compatible hosting account without a build step.

## Sources

- Brand: `assets/branding/gratify-mark.svg` in the parent repository.
- Screenshots: existing `artifacts/ui-refresh/` Android captures.
- Now Playing: existing `artifacts/music-quality/now-playing-android.png`.
- Features and beta/desktop status: project README.
- Reference: https://www.spotify.com/id-id/premium/
- Verified release: https://github.com/TanDjendra/Gratify/releases/tag/v2.1.0
