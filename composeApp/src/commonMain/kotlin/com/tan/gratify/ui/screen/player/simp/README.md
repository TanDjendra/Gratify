# SimpMusic Apple Music presentation

Upstream: https://github.com/maxrave-dev/SimpMusic/tree/62e0073ba818d899b22c75909deb9b034516d5db/composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/content
License: GPL-3.0, as supplied by upstream and this repository's LICENSE.
The mesh implementation also retains its original BitChord attribution in its source header.

The player bodies, artwork mesh/fade, typography, vector glyphs, volume row, transport,
compact headers, timed lyric/interlude rendering, lyric focus and fullscreen layouts
come from this upstream revision. Gratify's surrounding app theme is unchanged.

Compatibility adapters:
- `AppleMusicPlayerContent` maps Gratify playback state/actions and persistent motion preference.
- Native device volume uses Android AudioManager, with upstream's desktop null implementation.
- Android system-bar icons use light glyphs while the dark player is open, restoring their
  previous appearance on disposal.
- Main track/queue operations use Gratify's existing player, including YouTube likes.
- The queue row retains upstream geometry; metadata/actions come from Gratify.
- Cached artwork can be drawn when its URL is absent.
- Word animations honor pause and seeks; no-animation mode removes the lyric wipe/blur/motion.
- Existing PNG share/save adapters are retained, branded Gratify.
- Output routing/Cast, Listen Together, lyric romanization and lyrics-delay settings are absent
  from Gratify's backend/settings. Output/group buttons explain unavailability; there is no fake
  session or fabricated output device. Romanization rows remain unset and lyric delay defaults to zero.
- Animated artwork's optional upstream video poster is not supplied by Gratify's canvas model;
  its existing album artwork remains the fallback.

`previewAndroid` and the local media/volume preview handles exist to render the production
Android geometry in deterministic JVM tests. They do not replace native services in the app.
