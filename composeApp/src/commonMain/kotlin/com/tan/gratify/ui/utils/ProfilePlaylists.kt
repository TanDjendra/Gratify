package com.tan.gratify.ui.utils

import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.model.social.CloudPlaylistDto

/** Match stable identities first. A legacy local ID is usable only without a sync identity. */
internal fun profilePlaylistIds(local: List<LocalPlaylistEntity>, cloud: List<CloudPlaylistDto>): Set<Long> =
    local.filter { playlist ->
        cloud.any { published ->
            if (!published.clientSyncId.isNullOrBlank()) published.clientSyncId == playlist.syncId
            else published.localPlaylistId == playlist.id
        }
    }.map { it.id }.toSet()
