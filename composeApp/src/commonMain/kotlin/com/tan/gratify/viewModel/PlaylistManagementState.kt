package com.tan.gratify.viewModel

internal fun validPlaylistName(name: String): Boolean = name.trim().isNotEmpty() && name.length <= 100

data class PlaylistEditStatus(val saving: Boolean = false, val failed: Boolean = false)
