package com.tan.gratify.ui.navigation.destination.login

import kotlinx.serialization.Serializable

@Serializable
data class CreateProfileDestination(val skipYouTubeConnection: Boolean = false)
