package com.tan.gratify.expect

import coil3.PlatformContext
import androidx.compose.ui.graphics.ImageBitmap

/** Android shares a PNG to the chosen app or chooser; Desktop supports image copying. */
enum class LyricsShareTarget { SYSTEM, INSTAGRAM, WHATSAPP }
expect fun lyricsShareTargetAvailable(context: PlatformContext, target: LyricsShareTarget): Boolean
expect suspend fun shareLyricsImage(image: ImageBitmap, context: PlatformContext, target: LyricsShareTarget = LyricsShareTarget.SYSTEM): Boolean

/** Writes only to the location selected with the system file saver. */
expect suspend fun saveLyricsImage(image: ImageBitmap, context: PlatformContext, uri: String): Boolean
