package com.tan.gratify.ui.navigation

import com.eygraber.uri.Uri

data class SocialNotificationTarget(val owner: String, val peer: String? = null, val story: String? = null)
fun parseSocialNotificationTarget(value: String, owner: String): SocialNotificationTarget? = runCatching {
    val uri = Uri.parse(value)
    val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    if (uri.scheme != "gratify" || uri.host != "friends" || uri.getQueryParameter("owner") != owner) return null
    val peer = uri.getQueryParameter("peer")
    val story = uri.getQueryParameter("story")
    if (!owner.matches(uuid) || (peer == null) == (story == null) || !(peer ?: story.orEmpty()).matches(uuid)) return null
    SocialNotificationTarget(owner, peer, story)
}.getOrNull()
