package com.tan.domain.data.model.update

data class UpdateData(
    val tagName: String,
    val releaseTime: String?,
    val body: String,
    val apkUrl: String,
    val minVersion: String? = null,
)