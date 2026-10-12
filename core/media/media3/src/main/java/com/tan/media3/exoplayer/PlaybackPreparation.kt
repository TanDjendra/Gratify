package com.tan.media3.exoplayer

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.withTimeout

/** An errored/idle preload must be resolved again instead of promoted silently. */
@UnstableApi
internal fun isPreparedPreload(player: Player): Boolean =
    player.playerError == null && player.mediaItemCount > 0 &&
        player.playbackState in listOf(Player.STATE_BUFFERING, Player.STATE_READY)

/** Follow the real queue order, keeping at most [limit] distinct upcoming players. */
internal fun upcomingPreloadIndices(order: List<Int>, current: Int, repeatAll: Boolean, limit: Int): List<Int> {
    val position = order.indexOf(current)
    if (position < 0 || limit <= 0) return emptyList()
    val following = order.drop(position + 1) + if (repeatAll) order.take(position) else emptyList()
    return following.distinct().take(limit)
}

/** Bound all URL lookup stages together so ExoPlayer can report and retry a stalled source. */
internal suspend fun <T> resolveStreamWithDeadline(timeoutMs: Long = 30_000, block: suspend () -> T): T =
    withTimeout(timeoutMs) { block() }
