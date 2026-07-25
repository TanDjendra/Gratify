package com.tan.gratify.ui.navigation.destination.social

import kotlinx.serialization.Serializable

@Serializable
data class FollowListDestination(val userId: String, val initialTab: Int = 0)
