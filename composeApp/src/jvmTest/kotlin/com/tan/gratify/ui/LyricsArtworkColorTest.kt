package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.LyricsLineItem
import com.tan.gratify.ui.component.lyricsColorsFromArtwork
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.ui.theme.GratifyColors
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

class LyricsArtworkColorTest {
    private fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun artworkHueChangesAndBrightCoversKeepLyricsReadable() {
        val covers = listOf(Color(0xFFCE173C), Color(0xFF235ADA), Color(0xFFC642D5),
            Color(0xFFFFCE23), Color(0xFF38CCBA), Color.White, Color.Black)
        for (cover in covers) {
            val colors = lyricsColorsFromArtwork(cover)
            assertTrue(contrast(Color.White, colors.background) >= 7f)
            assertTrue(contrast(colors.translation, colors.background) >= 4.5f)
            assertEquals(1f, colors.background.alpha)
            assertNotEquals(GratifyColors.AccentContainer, colors.background)
        }
        val red = lyricsColorsFromArtwork(covers[0])
        val blue = lyricsColorsFromArtwork(covers[1])
        assertTrue(red.background.red > red.background.blue)
        assertTrue(blue.background.blue > blue.background.green)
        assertNotEquals(red.translation, blue.translation)
        assertEquals(GratifyColors.SurfaceRaised, lyricsColorsFromArtwork(Color.Unspecified).background)
        assertEquals(GratifyColors.SurfaceRaised, lyricsColorsFromArtwork(Color.Transparent).background)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun artworkLyricsRenderInRedAndBlue(): Unit = runBlocking(Dispatchers.Swing) {
        for ((name, cover) in listOf("red" to Color(0xFFCE173C), "blue" to Color(0xFF235ADA))) {
            val colors = lyricsColorsFromArtwork(cover)
            val scene = ImageComposeScene(390, 520, Density(1f, 1.2f), coroutineContext = coroutineContext) {
                AppTheme {
                    Column(Modifier.fillMaxSize().background(colors.background).padding(24.dp)) {
                        LyricsLineItem("Langkah kita menuju cahaya", "Our steps lead into the light", true,
                            isCurrent = true, accentColor = colors.translation)
                        LyricsLineItem("Bersama dalam irama", null, true, isCurrent = false, accentColor = colors.translation)
                    }
                }
            }
            try {
                repeat(16) { scene.render(it * 16_000_000L).close(); delay(16) }
                val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve("settings.gradle.kts")) }
                val path = root.resolve("artifacts/lyrics-color/lyrics-$name.png")
                Files.createDirectories(path.parent)
                scene.render(1_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
            } finally { scene.close() }
        }
    }
}
