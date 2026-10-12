package com.tan.domain.data.model.update

data class UpdateData(
    val tagName: String,
    val releaseTime: String?,
    val body: String,
    val apkUrl: String,
    val minVersion: String? = null,
    val versionCode: Long? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val packageName: String = "com.tan.gratify",
    val channel: String = "full",
    val deltas: List<UpdateDelta> = emptyList(),
)

data class UpdateDelta(
    val fromVersionCode: Long,
    val fromSha256: String,
    val fromSizeBytes: Long,
    val patchUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val format: String = "gratify-delta-v1",
)
