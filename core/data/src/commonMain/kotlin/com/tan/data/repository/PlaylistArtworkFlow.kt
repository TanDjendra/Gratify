package com.tan.data.repository

import com.tan.domain.data.entities.LocalPlaylistEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

private data class ArtworkIdentity(val owner: String?, val id: Long, val url: String?)

private fun LocalPlaylistEntity.artworkIdentity() = ArtworkIdentity(ownerEmail, id, storedPlaylistCoverUrl(thumbnail))

/** Emit local rows immediately, retaining their artwork while private URLs are refreshed. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<List<LocalPlaylistEntity>>.withPlaylistArtwork(
    cachedUrl: suspend (String?) -> String?,
    resolve: suspend (List<LocalPlaylistEntity>) -> List<LocalPlaylistEntity>,
): Flow<List<LocalPlaylistEntity>> = flow {
    // Per collector and bounded to the current rows; never borrow another owner's cover.
    var displayed = emptyMap<ArtworkIdentity, String?>()
    emitAll(distinctUntilChanged().transformLatest { rows ->
        val immediate = rows.map { row ->
            row.copy(thumbnail = cachedUrl(row.thumbnail) ?: displayed[row.artworkIdentity()])
        }
        displayed = rows.zip(immediate).associate { (stored, visible) -> stored.artworkIdentity() to visible.thumbnail }
        emit(immediate)
        if (rows.any { playlistCoverPath(it.thumbnail) != null }) {
            val resolved = resolve(rows)
            displayed = rows.zip(resolved).associate { (stored, visible) -> stored.artworkIdentity() to visible.thumbnail }
            emit(resolved)
        }
    }.distinctUntilChanged())
}
