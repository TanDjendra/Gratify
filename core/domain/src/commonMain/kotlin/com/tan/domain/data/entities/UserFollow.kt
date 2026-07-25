package com.tan.domain.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserFollow(
    @SerialName("follower_id")
    val followerId: String = "",
    @SerialName("following_id")
    val followingId: String = "",
    @SerialName("created_at")
    val createdAt: String? = null
)

