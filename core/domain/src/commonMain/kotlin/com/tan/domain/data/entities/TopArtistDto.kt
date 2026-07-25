package com.tan.domain.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopArtistDto(
    @SerialName("channel_id") val channelId: String,
    @SerialName("name") val name: String,
    @SerialName("thumbnails") val thumbnails: String? = null
)
