package com.tan.gratify.ui

import com.tan.gratify.ui.screen.search.*
import kotlin.test.*

class SearchArtworkTest {
    @Test fun genresAndIndonesianMoodsKeepRecognizableBackgroundColors() {
        val subjects = mapOf(
            "Jazz" to SearchCoverTheme.JAZZ, "Rock & metal" to SearchCoverTheme.ROCK,
            "Hip-Hop" to SearchCoverTheme.HIP_HOP, "Classical" to SearchCoverTheme.CLASSICAL,
            "EDM" to SearchCoverTheme.ELECTRONIC, "R&B" to SearchCoverTheme.RNB,
            "Indie Indonesia" to SearchCoverTheme.INDIE, "Musik untuk tidur" to SearchCoverTheme.SLEEP,
            "Fokus & belajar" to SearchCoverTheme.FOCUS, "Olahraga" to SearchCoverTheme.WORKOUT,
            "Lagu cinta" to SearchCoverTheme.LOVE, "Perjalanan pulang yang tenang" to SearchCoverTheme.TRAVEL,
            "Santai sore" to SearchCoverTheme.CHILL, "Dangdut" to SearchCoverTheme.WORLD,
            "Cerita dan podcast" to SearchCoverTheme.PODCAST, "K-pop hits" to SearchCoverTheme.POP,
        )
        subjects.forEach { (title, theme) -> assertEquals(theme, searchCoverTheme(title), title) }
        assertEquals(16, subjects.values.map { it.tile }.distinct().size)
    }
    @Test fun freeTitlesAndSubstringMatchesDoNotGetMisleadingGenreArt() {
        listOf("1", "Nara", "My collection", "Rockefeller", "Population", "", " ").forEach {
            assertEquals(SearchCoverTheme.PERSONAL, searchCoverTheme(it), it)
        }
    }
}
