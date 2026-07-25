package com.tan.gratify.expect

expect fun openUrl(url: String)

expect fun shareUrl(
    title: String,
    url: String,
)

expect fun shareImage(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    targetPackage: String? = null,
)