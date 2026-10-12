package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.tan.domain.utils.reconcileRichSyncWords
import com.tan.gratify.extension.parseRichSyncWords
import com.tan.gratify.ui.component.AppleMusicLyricsLine
import com.tan.gratify.ui.screen.player.simp.component.RichSyncLyricsLineItem
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ManguLyricRenderTest {
    private val source = "<00:22.96>Su <00:23.68>a <00:24.04>tu <00:24.99>ma <00:25.33>lam <00:25.76>A <00:26.17>dam <00:26.58>ber <00:27.44>ce <00:27.85>ri <00:28.24>ta"
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

    @Test fun animatedSyllablesStayOnOneRowPerWordAtBothPhoneWidths(): Unit = runBlocking(Dispatchers.Swing) {
        val corrected = reconcileRichSyncWords(source, "Suatu malam Adam bercerita")!!
        val parsed = parseRichSyncWords(corrected, "22960", "29000")!!
        for (width in listOf(320, 390)) {
            val scene = ImageComposeScene(width, 440, Density(1f), coroutineContext = coroutineContext) {
                AppTheme {
                    Column(Modifier.fillMaxSize().background(Color(0xFF086D72)).padding(20.dp)) {
                        Text("Mangu · Fourtwnty", color = Color.White)
                        RichSyncLyricsLineItem(parsedLine = parsed, translatedWords = null,
                            currentTimeMs = 25000, isCurrent = true, customFontSize = 32.sp,
                            glow = Shadow(Color.White), pendingColorOverride = Color(0xFF9B9B9B),
                            wrappedLineSpacing = 12.dp)
                        Spacer(Modifier.height(32.dp))
                        Text("Mode standar", color = Color.White)
                        AppleMusicLyricsLine(corrected, null, parsed, 25000, true, false,
                            Color.White, animationsEnabled = false)
                    }
                }
            }
            try {
                repeat(18) { scene.render(it * 16_000_000L).close(); delay(16) }
                val all = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                val fragments = parsed.words.map { word -> all.first { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(word.text) } }
                for (indices in listOf(0..2, 3..4, 5..6, 7..10)) {
                    val bounds = indices.map { fragments[it].boundsInRoot }
                    for ((left, right) in bounds.zipWithNext()) {
                        assertTrue(right.left >= left.right - 1f, "Fragments must remain in reading order")
                        assertTrue(right.top < left.bottom && left.top < right.bottom, "A lexical word cannot wrap between syllables")
                    }
                }
                assertTrue(all.any { it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                    text.text == "Suatu malam Adam bercerita" } == true })
                val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve("settings.gradle.kts")) }
                val path = root.resolve("artifacts/lyrics-spacing/mangu-$width.png")
                Files.createDirectories(path.parent)
                scene.render(400_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
            } finally { scene.close() }
        }
    }
}
