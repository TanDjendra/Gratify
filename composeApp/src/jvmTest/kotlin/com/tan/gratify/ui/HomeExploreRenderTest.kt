package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.domain.data.model.browse.artist.ResultPlaylist
import com.tan.domain.data.model.home.chart.*
import com.tan.domain.data.model.mood.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.theme.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class HomeExploreRenderTest {
    private val mood = Mood(
        arrayListOf(Genre("genre-pop", "Pop"), Genre("genre-indie", "Indie & alternatif"),
            Genre("genre-jazz", "Jazz"), Genre("genre-rnb", "R&B dan soul"), Genre("genre-rock", "Rock")),
        arrayListOf(MoodsMoment("mood-focus", "Fokus dan produktif"), MoodsMoment("mood-relax", "Bersantai"),
            MoodsMoment("mood-commute", "Perjalanan pulang"), MoodsMoment("mood-workout", "Olahraga"),
            MoodsMoment("mood-sleep", "Tidur dan beristirahat")),
    )
    private val chart = Chart(
        Artists(arrayListOf(
            ItemArtist("artist-1", "1", "12 jt", emptyList(), "Nara Prameswari", ""),
            ItemArtist("artist-2", "2", "8,5 jt", emptyList(), "Langit Kota", ""),
            ItemArtist("artist-3", "3", "6,2 jt", emptyList(), "Senja", ""),
        ), ""), null,
        listOf(ChartItemPlaylist("Lagu terpopuler", listOf(
            ResultPlaylist("chart-1", "Gratify", emptyList(), "Top songs Indonesia"),
            ResultPlaylist("chart-2", "Gratify", emptyList(), "Musik yang sedang naik daun"),
            ResultPlaylist("chart-3", "Gratify", emptyList(), "Top songs global"),
        ))),
    )
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun all(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    private fun label(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private suspend fun settle(scene: ImageComposeScene, start: Long = 0) {
        repeat(20) { scene.render(start + it * 16_000_000L).close(); delay(16) }
    }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/home-layout/$name.png")
        Files.createDirectories(path.parent)
        scene.render(2_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }

    @Test fun exploreShelvesFitSmallScreensAndLargeTextAndKeepAllCategoriesReachable(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, fontScale) in listOf(390 to 1f, 360 to 1.5f, 900 to 1.3f)) {
            var opened = ""
            val scene = ImageComposeScene(width, 1800, Density(1f, fontScale), coroutineContext = coroutineContext) {
                AppTheme {
                    Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        HomeExploreCategories(mood) { opened = it }
                        HomeSectionHeading("Tangga lagu", "Pilihan populer hari ini")
                        HomeChartRegionPicker("Indonesia", listOf("Indonesia", "Global")) {}
                        HomeChartShelves(chart, { opened = it }, { opened = it })
                    }
                }
            }
            try {
                settle(scene)
                val focus = all(scene).first { label(it)?.contains("Fokus dan produktif") == true && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(focus.boundsInRoot.left >= 16f && focus.boundsInRoot.right <= width - 16f)
                focus.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("mood-focus", opened)
                val playlist = all(scene).first { label(it)?.contains("Top songs Indonesia") == true && it.config.contains(SemanticsActions.OnClick) }
                playlist.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("chart-1", opened)
                val artist = all(scene).first { label(it)?.contains("Nara Prameswari") == true && it.config.contains(SemanticsActions.OnClick) }
                artist.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("artist-1", opened)
                val lastHeading = all(scene).first { label(it) == "Top artists" }
                assertTrue(lastHeading.boundsInRoot.bottom < 1800f, "Sections overflow vertically at width $width")
                snapshot(scene, "explore-$width")
                // Last categories remain reachable beyond the first visible columns.
                all(scene).first { it.config.contains(SemanticsActions.ScrollBy) }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(2000f, 0f)
                settle(scene, 3_000_000_000L)
                val sleep = all(scene).first { label(it)?.contains("Tidur dan beristirahat") == true && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(sleep.boundsInRoot.left >= 0f && sleep.boundsInRoot.right <= width)
                sleep.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals("mood-sleep", opened)
            } finally { scene.close() }
        }
    }

    @Test fun chartRegionShowsControlledSelectionAfterAnExternalChange(): Unit = runBlocking(Dispatchers.Swing) {
        var selected by mutableStateOf("Indonesia")
        val scene = ImageComposeScene(360, 400, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    HomeChartRegionPicker(selected, listOf("Indonesia", "Global")) { selected = it }
                }
            }
        }
        try {
            settle(scene)
            selected = "Global"
            settle(scene, 1_000_000_000L)
            assertTrue(all(scene).any { label(it)?.contains("Global") == true })
            assertFalse(all(scene).any { label(it)?.contains("Indonesia") == true })
        } finally { scene.close() }
    }
}
