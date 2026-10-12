package com.tan.kotlinytmusicscraper.models.gratify

import kotlinx.serialization.Serializable

@Serializable
data class GithubUpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val packageName: String,
    val channel: String,
    val apkName: String,
    val sha256: String,
    val sizeBytes: Long,
    val deltas: List<GithubDeltaManifest> = emptyList(),
)

@Serializable
data class GithubDeltaManifest(
    val fromVersionCode: Long = 0,
    val fromSha256: String = "",
    val fromSizeBytes: Long = 0,
    val patchName: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0,
    val format: String = "gratify-delta-v1",
)
