# The existing crossfade adapter swaps this delegate through reflection.
# Keep the field name and writes intact in minified app builds.
-keepclassmembers class androidx.media3.common.ForwardingPlayer {
    private final androidx.media3.common.Player player;
}
