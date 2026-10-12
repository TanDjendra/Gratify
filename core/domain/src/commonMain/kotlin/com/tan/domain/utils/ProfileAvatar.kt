package com.tan.domain.utils

/** A Gratify profile photo comes from an explicit upload to the avatar bucket. */
fun String?.uploadedProfileAvatar(): String? = this?.takeIf {
    it.startsWith("https://") && it.contains("/storage/v1/object/public/avatars/")
}
