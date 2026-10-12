package com.tan.gratify.ui.utils

/** A refreshed access token does not change an immutable cover object; viewer accounts stay isolated. */
fun playlistImageCacheKey(url: String, serverUrl: String, viewerId: String?): String? {
    val prefix = serverUrl.trimEnd('/') + "/storage/v1/object/sign/playlist-covers/"
    if (!url.startsWith(prefix) || viewerId.isNullOrBlank()) return null
    val path = url.removePrefix(prefix).substringBefore('?')
    val parts = path.split('/')
    if (parts.size != 3 || parts.any { it.isBlank() || it == "." || it == ".." || '%' in it }) return null
    return "playlist-cover-v1:$viewerId:$prefix$path"
}
