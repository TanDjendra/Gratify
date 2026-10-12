package com.tan.domain.utils

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.extension.decodeHtmlEntities

const val LYRICS_TEXT_QUALITY_VERSION = 1

private val WordTiming = Regex("""<\d{1,6}:\d{2}\.\d{2,3}>""")
private val LineTiming = Regex("""\[\d{1,6}:\d{2}(?:\.\d{1,3})?\]""")
private val Spaces = Regex("\\s+")
// Avoid penalizing ordinary short function words. This list never supplies a replacement.
private val CommonShortWords = setOf("i", "a", "am", "an", "as", "at", "be", "by", "do", "go", "he", "if",
    "in", "is", "it", "me", "my", "no", "of", "oh", "on", "or", "so", "to", "up", "us", "we",
    "di", "ke", "ku", "mu", "ya", "la", "na", "da", "ah", "uh")

fun lyricDisplayText(words: String): String = decodeHtmlEntities(words)
    .replace(WordTiming, "").replace(Spaces, " ").trim()

/** A search signal, never an instruction to join tokens or invent words. */
fun hasPotentiallyFragmentedText(words: String): Boolean {
    val text = lyricDisplayText(words)
    if (LineTiming.containsMatchIn(text)) return true
    val tokens = text.split(Spaces).map { it.filter(Char::isLetter) }.filter(String::isNotEmpty)
    // Spaced characters are legitimate in some scripts. This signal covers Latin fragments only.
    if (tokens.any { token -> token.any { it !in 'a'..'z' && it !in 'A'..'Z' } }) return false
    if (tokens.size < 2) return false
    // Repeated vocalizations such as "na na na" are valid lyrics, not word fragments.
    if (tokens.map(String::lowercase).distinct().size <= 2) return false
    val shortCount = tokens.count { it.length <= 2 }
    val unexplainedShort = tokens.count { it.length <= 2 && it.lowercase() !in CommonShortWords }
    var run = 0
    var longestRun = 0
    tokens.forEach { token ->
        run = if (token.length <= 2) run + 1 else 0
        longestRun = maxOf(longestRun, run)
    }
    val isolatedLetters = tokens.count { it.length == 1 && !it.equals("i", ignoreCase = true) }
    return tokens.size >= 6 && shortCount * 10 >= tokens.size * 6 && longestRun >= 4 && unexplainedShort >= 3 ||
        tokens.size >= 4 && shortCount == tokens.size && isolatedLetters >= 2 && unexplainedShort >= 3
}

fun Line.hasConfirmedLyricText(): Boolean = textQualityVersion >= LYRICS_TEXT_QUALITY_VERSION

fun Lyrics.hasPotentiallyFragmentedLines(): Boolean {
    // Transcript-local deduplication; never retain song text in a global RAM cache.
    val checked = mutableSetOf<String>()
    return lines.orEmpty().any {
        !it.hasConfirmedLyricText() && (it.textQualityVersion < 0 ||
            checked.add(it.words) && hasPotentiallyFragmentedText(it.words))
    }
}

/** Once a transcript shows syllable spacing, partial repairs cannot certify the rest. */
fun Lyrics.assessTextQuality(): Lyrics = if (!hasPotentiallyFragmentedLines() &&
    (syncType == "RICH_SYNCED" || lines.orEmpty().none { WordTiming.containsMatchIn(it.words) })) this else copy(
    lines = lines?.map { line ->
        if (line.hasConfirmedLyricText() || !lyricDisplayText(line.words).any(Char::isLetter)) line
        else line.copy(textQualityVersion = -1)
    },
)

/** Reject malformed non-rich payloads too; a format label does not establish text quality. */
fun Lyrics.hasDisplayableLyrics(): Boolean = !error && lines.orEmpty().any {
    lyricDisplayText(it.words).any(Char::isLetterOrDigit)
} && !hasPotentiallyFragmentedLines() &&
    (if (syncType == "RICH_SYNCED") hasVerifiedWordBoundaries()
    else lines.orEmpty().none { WordTiming.containsMatchIn(it.words) || LineTiming.containsMatchIn(it.words) })

fun Lyrics.needsWordBoundaryReference(): Boolean = !hasDisplayableLyrics()

/** Borrow whitespace only when every other character matches, including punctuation. */
fun reconcileLineWords(words: String, referenceWords: String): String? {
    val source = lyricDisplayText(words)
    val reference = lyricDisplayText(referenceWords)
    val compact = source.filterNot(Char::isWhitespace)
    if (compact.isBlank() || !compact.equals(reference.filterNot(Char::isWhitespace), ignoreCase = true)) return null
    var cursor = 0
    return buildString {
        compact.forEach { character ->
            while (cursor < reference.length && reference[cursor].isWhitespace()) append(reference[cursor++])
            append(character)
            cursor++
        }
    }
}
