package com.tan.gratify.expect.ui

import kotlin.math.min
import kotlin.math.roundToInt

/** Coordinates are in the orientation-corrected source bitmap, never the original EXIF space. */
data class SquarePhotoCrop(val centerX: Float, val centerY: Float, val zoom: Float = 1f) {
    fun bounded(width: Int, height: Int): SquarePhotoCrop {
        require(width > 0 && height > 0)
        val z = zoom.coerceIn(1f, 4f)
        val half = min(width, height) / z / 2f
        return SquarePhotoCrop(centerX.coerceIn(half, width - half), centerY.coerceIn(half, height - half), z)
    }
    fun move(width: Int, height: Int, panX: Float, panY: Float, viewport: Float, zoomFactor: Float): SquarePhotoCrop {
        if (viewport <= 0f) return bounded(width, height)
        val current = bounded(width, height)
        val scale = min(width, height) / current.zoom / viewport
        return current.copy(centerX = current.centerX - panX * scale, centerY = current.centerY - panY * scale,
            zoom = current.zoom * zoomFactor).bounded(width, height)
    }
    fun pixels(width: Int, height: Int): CropPixels {
        val crop = bounded(width, height)
        val side = (min(width, height) / crop.zoom).roundToInt().coerceIn(1, min(width, height))
        return CropPixels((crop.centerX - side / 2f).roundToInt().coerceIn(0, width - side),
            (crop.centerY - side / 2f).roundToInt().coerceIn(0, height - side), side)
    }
    companion object { fun centered(width: Int, height: Int) = SquarePhotoCrop(width / 2f, height / 2f) }
}

data class CropPixels(val x: Int, val y: Int, val side: Int)
