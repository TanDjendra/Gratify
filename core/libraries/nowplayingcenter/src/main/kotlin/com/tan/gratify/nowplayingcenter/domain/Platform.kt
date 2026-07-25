package com.tan.gratify.nowplayingcenter.domain

sealed class Platform {
    data object MacOs : Platform()
    data object Windows : Platform()
    class Linux(val playerName: String, val desktopEntryFile: String) : Platform()
}
