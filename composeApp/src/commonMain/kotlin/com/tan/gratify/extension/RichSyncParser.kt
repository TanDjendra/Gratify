package com.tan.gratify.extension

import com.tan.domain.extension.decodeHtmlEntities

/**
 * Represents a single word with its timing information for rich sync lyrics
 */
data class WordTiming(
    val text: String,
    val startTimeMs: Long,
    // A timestamp can mark a syllable inside a word. Only source whitespace separates words.
    val separatorBefore: String = " ",
)

/**
 * Represents a parsed rich sync line with all word timings
 */
data class ParsedRichSyncLine(
    val words: List<WordTiming>,
    val lineStartTimeMs: Long,
    val lineEndTimeMs: Long,
)

fun ParsedRichSyncLine.displayText(): String = words.mapIndexed { index, word ->
    (if (index == 0) "" else word.separatorBefore) + word.text
}.joinToString("")

/** Keep syllables together when wrapping, while retaining every segment's timing. */
fun ParsedRichSyncLine.displayWordGroups(): List<List<Int>> {
    val groups = mutableListOf<MutableList<Int>>()
    words.forEachIndexed { index, word ->
        if (groups.isEmpty() || word.separatorBefore.isNotEmpty()) groups.add(mutableListOf())
        groups.last().add(index)
    }
    return groups
}

/**
 * Parses rich sync lyrics format into word-level timing data
 *
 * Expected format: <MM:SS.mm> word <MM:SS.mm> word ...
 * Example: <00:16.62> Và <00:16.64> em <00:16.68> nói
 *
 * @param words The rich sync string containing timestamps and words
 * @param lineStartTimeMs The start time of the line as a string
 * @param lineEndTimeMs The end time of the line as a string
 * @return ParsedRichSyncLine if parsing succeeds, null if parsing fails or input is invalid
 */
fun parseRichSyncWords(
    words: String,
    lineStartTimeMs: String,
    lineEndTimeMs: String,
): ParsedRichSyncLine? {
    // Handle edge cases
    if (words.isBlank()) return null

    // Strategy: Find all timestamps first, then extract text between them
    // Regex to match timestamp only: <MM:SS.mm> or <MM:SS.mmm>
    val timestampRegex = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>""")

    val wordTimings = mutableListOf<WordTiming>()

    // Find all timestamp matches with their positions
    val timestamps = timestampRegex.findAll(words).toList()
    var previousEndedWithSpace = false

    timestamps.forEachIndexed { index, match ->
        val (minutes, seconds, fraction) = match.destructured

        // Convert time to milliseconds
        // If 2 digits (centiseconds): multiply by 10 to get ms (e.g., 62 -> 620ms)
        // If 3 digits (milliseconds): use as-is (e.g., 620 -> 620ms)
        val fractionMs = fraction.toLongOrNull() ?: 0L
        val timeMs =
            (minutes.toLongOrNull() ?: 0L) * 60000L +
                (seconds.toLongOrNull() ?: 0L) * 1000L +
                if (fraction.length == 2) fractionMs * 10L else fractionMs

        // Extract text after this timestamp until the next timestamp (or end of string)
        val startPos = match.range.last + 1
        val endPos =
            if (index < timestamps.size - 1) {
                timestamps[index + 1].range.first
            } else {
                words.length
            }

        val rawText = decodeHtmlEntities(words.substring(startPos, endPos))
        val textBetween = rawText.trim().replace(Regex("\\s+"), " ")

        // Only add if there's actual text (not just whitespace or empty)
        if (textBetween.isNotBlank()) {
            val boundary = wordTimings.isNotEmpty() &&
                (previousEndedWithSpace || rawText.firstOrNull()?.isWhitespace() == true)
            wordTimings.add(WordTiming(textBetween, timeMs, if (boundary) " " else ""))
        }
        if (rawText.isNotEmpty()) previousEndedWithSpace = rawText.last().isWhitespace()
    }

    // If no valid words were parsed, return null (fallback to LINE_SYNCED)
    if (wordTimings.isEmpty()) return null

    // Parse line timing
    val lineStart = lineStartTimeMs.toLongOrNull() ?: 0L
    val lineEnd = lineEndTimeMs.toLongOrNull() ?: Long.MAX_VALUE

    return ParsedRichSyncLine(
        words = wordTimings,
        lineStartTimeMs = lineStart,
        lineEndTimeMs = lineEnd,
    )
}
