package com.tan.gratify.ui.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.*
import com.tan.gratify.ui.benchmark.upstream.parseRichSyncWords
import com.tan.gratify.ui.benchmark.upstream.RichSyncLyricsLineItem
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ManguSourceVariantRenderTest {
    @Test fun originalRendererCanShowWholeWordsWhenTheVideoResponseHasWholeWordTiming(): Unit = runBlocking(Dispatchers.Swing) {
        // Captured public fUdfDKyrbjk response, first phrase shown in the user's screenshot.
        val source = "<00:24.85>Suatu <00:25.92>malam <00:27.50>Adam <00:28.57>bercerita"
        val parsed = parseRichSyncWords(source, "24850", "31000")!!
        assertEquals(listOf("Suatu", "malam", "Adam", "bercerita"), parsed.words.map { it.text })
        val scene = ImageComposeScene(390, 220, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF086D72)).padding(20.dp)) {
                    Text("Mangu · Fourtwnty", color = Color.White)
                    RichSyncLyricsLineItem(parsedLine = parsed, translatedWords = null,
                        currentTimeMs = 25920, isCurrent = true, customFontSize = 32.sp,
                        glow = Shadow(Color.White), pendingColorOverride = Color(0xFF9B9B9B),
                        wrappedLineSpacing = 12.dp)
                }
            }
        }
        try {
            repeat(18) { scene.render(it * 16_000_000L).close(); delay(16) }
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve("settings.gradle.kts")) }
            val path = root.resolve("artifacts/simpmusic-source-investigation-2026-10-03/upstream-audio-whole-words.png")
            Files.createDirectories(path.parent)
            scene.render(300_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
        } finally { scene.close() }
    }
}
