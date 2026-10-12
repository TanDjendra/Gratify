package com.tan.gratify.ui.component.lyrics

import com.tan.gratify.viewModel.NowPlayingScreenData

private val RichMarkers = Regex("""<\d{2}:\d{2}\.\d{2,3}>""")
private val ExtraSpaces = Regex("""[ \t]+""")

internal fun stripRichLyricsTimestamps(words: String): String =
    words.replace(RichMarkers, "").replace(ExtraSpaces, " ").trim()

internal fun NowPlayingScreenData.LyricsData.toShareLyricsLines(): List<String> =
    lyrics.lines.orEmpty().map { stripRichLyricsTimestamps(it.words) }
