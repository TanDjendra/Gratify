package com.tan.gratify.ui.utils

import com.tan.domain.data.model.home.Content
import com.tan.domain.data.model.home.HomeItem

internal enum class DiscoverySeedKind { SONG, PLAYLIST, ALBUM }
internal data class DiscoverySeed(val content: Content, val kind: DiscoverySeedKind, val id: String) {
    val key: String get() = "$kind:$id"
}

/** Collection-only home responses can still lead to songs and their related recommendations. */
internal fun nextDiscoverySeed(items: List<HomeItem>, explored: Set<String>): DiscoverySeed? {
    val seeds = items.asReversed().flatMap { it.contents.filterNotNull() }.mapNotNull { content ->
        when {
            !content.videoId.isNullOrBlank() -> DiscoverySeed(content, DiscoverySeedKind.SONG, content.videoId!!)
            !content.playlistId.isNullOrBlank() -> DiscoverySeed(content, DiscoverySeedKind.PLAYLIST, content.playlistId!!)
            content.browseId?.startsWith("VL") == true -> DiscoverySeed(content, DiscoverySeedKind.PLAYLIST, content.browseId!!)
            content.browseId?.startsWith("MPRE") == true -> DiscoverySeed(content, DiscoverySeedKind.ALBUM, content.browseId!!)
            else -> null
        }
    }.filter { it.key !in explored }
    return seeds.firstOrNull { it.kind == DiscoverySeedKind.SONG } ?: seeds.firstOrNull()
}

private fun Content.discoveryId(): String = when {
    !videoId.isNullOrBlank() -> "song:$videoId"
    !browseId.isNullOrBlank() -> "browse:$browseId"
    !playlistId.isNullOrBlank() -> "playlist:$playlistId"
    else -> "title:$title:${artists?.joinToString { it.name }}"
}

/** Never create an endless illusion by repeating the same songs and collections. */
internal fun appendDiscovery(current: List<HomeItem>, incoming: List<HomeItem>): List<HomeItem> {
    val seen = current.flatMap { it.contents.filterNotNull() }.map { it.discoveryId() }.toMutableSet()
    return current + incoming.mapNotNull { section ->
        val fresh = section.contents.filterNotNull().filter { seen.add(it.discoveryId()) }
        if (fresh.isEmpty()) null else section.copy(contents = fresh)
    }
}
