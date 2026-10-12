package com.tan.domain.utils

import com.tan.domain.extension.decodeHtmlEntities
import com.tan.domain.data.model.metadata.Line

// v1 was applied to every rich response, even when no word-boundary reference matched.
const val RICH_SYNC_TEXT_VERSION = 2

private val RichWordStamp = Regex("""<\d{1,6}:\d{2}\.\d{2,3}>""")
private val WhitespaceRuns = Regex("\\s+")

/** Match characters, never guess word boundaries from short syllables or a dictionary. */
fun richSyncTextSignature(text: String): String = decodeHtmlEntities(text)
    .replace(RichWordStamp, "").filterNot(Char::isWhitespace).lowercase()

/** Reapply a matching plain line's whitespace while retaining every timestamp. */
fun reconcileRichSyncWords(richWords: String, referenceWords: String): String? {
    val source = decodeHtmlEntities(richWords)
    val reference = decodeHtmlEntities(referenceWords).trim().replace(WhitespaceRuns, " ")
    val stamps = RichWordStamp.findAll(source).toList()
    if (stamps.isEmpty() || source.substring(0, stamps.first().range.first).isNotBlank()) return null
    val fragments = stamps.mapIndexed { index, stamp ->
        source.substring(stamp.range.last + 1, stamps.getOrNull(index + 1)?.range?.first ?: source.length)
            .filterNot(Char::isWhitespace)
    }
    val compact = fragments.joinToString("")
    if (compact.isEmpty() || !compact.equals(reference.filterNot(Char::isWhitespace), ignoreCase = true)) return null
    // Keep already-correct rich markup stable, including whether a space precedes a stamp.
    if (source.replace(RichWordStamp, "").trim().replace(WhitespaceRuns, " ").equals(reference, ignoreCase = true)) return source
    var cursor = 0
    return buildString {
        stamps.forEachIndexed { index, stamp ->
            append(stamp.value)
            fragments[index].forEach { character ->
                while (cursor < reference.length && reference[cursor].isWhitespace()) append(reference[cursor++])
                // Borrow only word boundaries, never spelling/capitalization from the reference.
                append(character)
                cursor++
            }
        }
    }
}

/** References supply whitespace only. Never borrow their audio timings or spelling. */
fun com.tan.domain.data.model.metadata.Lyrics.reconcileWordBoundaries(
    reference: com.tan.domain.data.model.metadata.Lyrics?,
): com.tan.domain.data.model.metadata.Lyrics {
    if (hasVerifiedWordBoundaries() || reference == null || !reference.hasDisplayableLyrics() || reference.syncType == "RICH_SYNCED") return this
    val signatures = mutableMapOf<String, String>()
    fun signature(words: String) = signatures.getOrPut(words) { richSyncTextSignature(words) }
    val references = reference.lines.orEmpty().withIndex().filter { it.value.words.isNotBlank() }
        .groupBy { signature(it.value.words) }.mapValues { (_, entries) ->
            entries.map { TimedReference(it.index, it.value.startTimeMs.toLongOrNull() ?: 0, it.value) }
                .sortedWith(compareBy<TimedReference> { it.time }.thenBy { it.index })
                .distinctBy { it.time }
        }
    val repairedWords = mutableMapOf<Pair<String, String>, String?>()
    val repaired = lines.orEmpty().map { line ->
        val matching = references[signature(line.words)]?.nearest(line.startTimeMs.toLongOrNull() ?: 0)?.line
            ?: return@map line
        val words = repairedWords.getOrPut(line.words to matching.words) {
            if (syncType == "RICH_SYNCED") reconcileRichSyncWords(line.words, matching.words)
            else reconcileLineWords(line.words, matching.words)
        }
        words ?: return@map line
        line.copy(words = words,
            richSyncTextVersion = if (syncType == "RICH_SYNCED") RICH_SYNC_TEXT_VERSION else line.richSyncTextVersion,
            textQualityVersion = LYRICS_TEXT_QUALITY_VERSION)
    }
    return if (repaired == lines) this else copy(lines = repaired)
}

private data class TimedReference(val index: Int, val time: Long, val line: Line)

/** Binary search instead of scanning every repeated chorus for each source line. */
private fun List<TimedReference>.nearest(time: Long): TimedReference? {
    if (isEmpty()) return null
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (this[mid].time < time) low = mid + 1 else high = mid
    }
    val before = getOrNull(low - 1)
    val after = getOrNull(low)
    if (before == null) return after
    if (after == null) return before
    fun distance(value: Long) = (if (time >= value) time - value else value - time)
        .takeIf { it >= 0 } ?: Long.MAX_VALUE
    val left = distance(before.time)
    val right = distance(after.time)
    return when {
        left < right -> before
        right < left -> after
        before.index < after.index -> before
        else -> after
    }
}

fun com.tan.domain.data.model.metadata.Lyrics.hasVerifiedWordBoundaries(): Boolean =
    syncType == "RICH_SYNCED" && !lines.isNullOrEmpty() && lines.orEmpty().all {
        it.richSyncTextVersion >= RICH_SYNC_TEXT_VERSION && it.hasConfirmedLyricText()
    }
