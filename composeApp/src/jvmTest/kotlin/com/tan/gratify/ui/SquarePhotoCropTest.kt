package com.tan.gratify.ui

import com.tan.gratify.expect.ui.SquarePhotoCrop
import com.tan.gratify.expect.ui.CropPixels
import kotlin.test.*

class SquarePhotoCropTest {
    @Test fun portraitAndLandscapeStartWithCenteredSquare() {
        assertEquals(CropPixels(0, 1000, 1000), SquarePhotoCrop.centered(1000, 3000).pixels(1000, 3000))
        assertEquals(CropPixels(1000, 0, 1000), SquarePhotoCrop.centered(3000, 1000).pixels(3000, 1000))
    }
    @Test fun draggingAndZoomCannotExposeEmptyBorders() {
        for ((w, h) in listOf(1000 to 3000, 3000 to 1000, 1 to 1, 999 to 1555)) {
            for (factor in listOf(.001f, 1f, 100f)) {
                val crop = SquarePhotoCrop.centered(w, h).move(w, h, 5000f, -5000f, 320f, factor)
                val box = crop.pixels(w, h)
                assertTrue(crop.zoom in 1f..4f)
                assertTrue(box.x >= 0 && box.y >= 0 && box.side > 0)
                assertTrue(box.x + box.side <= w && box.y + box.side <= h)
            }
        }
    }
    @Test fun panUsesSourcePixelsAndResetRestoresTheCenter() {
        val original = SquarePhotoCrop.centered(1000, 3000)
        assertEquals(750, original.move(1000, 3000, 0f, 80f, 320f, 1f).pixels(1000, 3000).y)
        assertEquals(CropPixels(0, 1000, 1000), original.pixels(1000, 3000))
        assertEquals(250, original.copy(zoom = 4f).pixels(1000, 3000).side)
    }
    @Test fun pinchInAndOutChangesCropAreaWithoutSlider() {
        val original = SquarePhotoCrop.centered(1000, 3000)
        val zoomed = original.move(1000, 3000, 0f, 0f, 320f, 2f)
        assertEquals(2f, zoomed.zoom)
        assertEquals(500, zoomed.pixels(1000, 3000).side)
        val restored = zoomed.move(1000, 3000, 0f, 0f, 320f, .5f)
        assertEquals(original.pixels(1000, 3000), restored.pixels(1000, 3000))
    }
}
