"""Source integration guard: legacy slider animation must not run in Apple UI.
Runtime CPU/gfx evidence is required in addition to this structural regression.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/player/NowPlayingScreen.kt'

class PlayerAnimationBudgetTest(unittest.TestCase):
    def test_legacy_crossfade_animation_is_after_primary_player_early_return(self):
        source = SOURCE.read_text(encoding='utf-8')
        primary = source.index('if (!isInPipMode) {\n        AppleMusicPlayerContent(')
        primary_return = source.index('\n        return\n    }', primary)
        self.assertGreater(source.index('rememberInfiniteTransition(label = "crossfadeRainbow")'), primary_return,
                           'Invisible legacy slider animation currently drives the primary player every frame')

    def test_legacy_animation_is_only_created_for_active_visible_crossfade(self):
        source = SOURCE.read_text(encoding='utf-8')
        pos = source.index('rememberInfiniteTransition(label = "crossfadeRainbow")')
        scope = source[max(0, pos-900):pos]
        self.assertIn('timelineState.isCrossfading && controllerState.isPlaying && playerLifecycleState.isAtLeast(Lifecycle.State.RESUMED)', scope,
                      'Paused/background playback must not allocate an infinite rainbow transition')
        self.assertIn('if (animateLegacyCrossfade)', scope)

if __name__ == '__main__':
    unittest.main()
