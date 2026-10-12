package com.tan.gratify.viewModel.handler

import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.repository.CommonRepository
import com.tan.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

internal suspend fun syncFollowerNotifications(
    users: UserRepository,
    notifications: CommonRepository,
    currentOwner: () -> String?,
    onNew: suspend (NotificationEntity) -> Unit = {},
) = withTimeout(25_000) {
    val owner = currentOwner() ?: return@withTimeout
    if (notifications.notificationAccountOwner() != owner) return@withTimeout
    var offset = 0
    while (offset < 5000) {
        val followers = users.fetchFollowers(owner, offset, 100).first().getOrThrow()
        if (currentOwner() != owner || notifications.notificationAccountOwner() != owner) return@withTimeout
        for (follower in followers) {
            if (currentOwner() != owner) return@withTimeout
            val entity = NotificationEntity(
                channelId = "follower_activity", thumbnail = follower.avatarUrl,
                name = follower.displayName?.takeIf { it.isNotBlank() } ?: "User",
                single = listOf(mapOf("title" to "mulai mengikuti Anda", "browseId" to follower.id, "ownerId" to owner)),
            )
            if (notifications.insertNotificationOnce(entity) && currentOwner() == owner) onNew(entity)
        }
        if (followers.size < 100) break
        offset += followers.size
    }
}
