<div align="center">

# Gratify

**A free YouTube Music client for Android** — background playback, synced
lyrics, offline caching, and cross-device sync.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Fork of SimpMusic](https://img.shields.io/badge/fork%20of-maxrave--dev%2FSimpMusic-8A2BE2)](https://github.com/maxrave-dev/SimpMusic)
[![Discord](https://img.shields.io/badge/Discord-join-5865F2?logo=discord&logoColor=white)](https://discord.gg/Rq5tWHg)

</div>

---

## This is a fork

**Gratify is a modified version of [SimpMusic](https://github.com/maxrave-dev/SimpMusic)
by [maxrave-dev](https://github.com/maxrave-dev) (Nguyễn Đức Tuấn Minh).**

Nearly all of the code here was written by the SimpMusic team and its 30+
contributors over more than three years. This fork adds a Supabase sync layer,
social features, a restructured player UI, and a self-hosted update channel.
Everything else is theirs.

If this app is useful to you, star
[the original project](https://github.com/maxrave-dev/SimpMusic) first — that is
where the work happens.

| | |
|---|---|
| Upstream | https://github.com/maxrave-dev/SimpMusic |
| Exact diff | [`SimpMusic...TanDjendra:Gratify`](https://github.com/maxrave-dev/SimpMusic/compare/main...TanDjendra:Gratify:main) |
| Upstream on F-Droid | [`com.maxrave.simpmusic`](https://f-droid.org/en/packages/com.maxrave.simpmusic/) |

---

## What this fork changes

| Area | Change |
|---|---|
| **Sync** | Supabase-backed account sync for playlists, liked songs and listening history |
| **Social** | Social features built on top of that sync backend |
| **UI** | Restructured player and library screens |
| **Updates** | Self-hosted OTA channel — the app reads a version manifest on launch and prompts when a newer build exists, no store required |
| **Notifications** | In-app announcement feed pulled from a JSON endpoint, so notices ship without cutting a release |
| **Build** | Desktop packaging fixes (ProGuard / kotlinx-datetime), CI keystore handling, and a guard against shipping builds with empty Supabase credentials |

Package id: `com.tan.gratify`

---

## Features

Inherited from SimpMusic unless marked:

- Ad-free playback from YouTube Music, including background and screen-off
- Browse Home, Charts, Podcasts, Moods & Genres
- Search across YouTube
- Synced lyrics from LRCLIB and YouTube transcripts
- Offline caching
- Custom playlists, synced with your YouTube Music account
- SponsorBlock and Return YouTube Dislike support
- Sleep timer, Android Auto, Discord Rich Presence
- **Cross-device sync and social features** *(this fork)*
- **In-app update prompts and announcements** *(this fork)*

> **Beta.** This app depends on YouTube Music's web surface, so playback errors
> happen when Google changes something upstream. Report bugs via the Discord
> server or email.

---

## Download

> **Android only.** The desktop target compiles, but there is no public release
> of it yet.

| Route | Status |
|---|---|
| [Direct APK](https://tanweb.vercel.app/releases/GratifyMusic.apk) | ✅ Available |
| GitHub Releases | ⏳ Planned — this repository is not published yet |
| F-Droid | ⏳ Planned — not submitted yet |
| Desktop (Windows / macOS / Linux) | ⏳ In development |

Release page with FAQ and install notes:
**https://tanweb.vercel.app/gratify.html**

---

## Building

Requires JDK 17+ and the Android SDK.

```bash
git clone https://github.com/TanDjendra/Gratify.git
cd Gratify
```

Create `local.properties` in the project root:

```properties
sdk.dir=/path/to/Android/Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key
```

`local.properties` is git-ignored and must stay that way — it holds your
credentials. The same goes for any `*.jks` signing keystore.

```bash
./gradlew :androidApp:assembleRelease                     # Android
./gradlew :desktopApp:packageDistributionForCurrentOS     # Desktop (WIP)
```

Modules: `androidApp` · `composeApp` · `desktopApp` · `core` · `supabase` ·
`crashlytics`

---

## Contributing

Bug reports and pull requests are welcome. Please read
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) first.

Fixes that are not specific to this fork belong
[upstream](https://github.com/maxrave-dev/SimpMusic) instead — that way every
SimpMusic user benefits, not only Gratify's.

---

## Legal

Gratify is a third-party client that behaves like a specialised browser: it
parses publicly available YouTube Music pages and renders them in its own
interface. It is built for education and personal, non-commercial use.

No copyrighted media is hosted, stored or distributed by this project. All
audio and video is streamed directly from Google's servers. If you value the
artists and creators behind the music, subscribe to
[YouTube Premium](https://www.youtube.com/premium).

Legal enquiries: tandjendra11@gmail.com

---

## License

**GNU General Public License v3.0** — see [LICENSE](LICENSE).

SimpMusic is GPL-3.0, so this fork is too. Anything built on top of it must also
be GPL-3.0 and must keep this attribution intact.

### Credits

- **[maxrave-dev](https://github.com/maxrave-dev)** — creator of SimpMusic and
  author of the overwhelming majority of this codebase
- **The SimpMusic contributors** — see the
  [upstream contributor list](https://github.com/maxrave-dev/SimpMusic/graphs/contributors)
- **[TanDjendra](https://github.com/TanDjendra)** — this fork
