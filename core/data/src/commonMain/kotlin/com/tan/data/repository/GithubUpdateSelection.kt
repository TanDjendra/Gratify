package com.tan.data.repository

import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.data.model.update.UpdateDelta
import com.tan.kotlinytmusicscraper.models.gratify.GithubResponse
import com.tan.kotlinytmusicscraper.models.gratify.GithubUpdateManifest

internal fun selectGithubUpdate(release: GithubResponse, manifest: GithubUpdateManifest? = null,
                                expectedPackageName: String = "com.tan.gratify"): UpdateData {
    require(release.draft != true && release.prerelease != true) { "Rilis ini belum tersedia di jalur stabil" }
    val tag = release.tagName.orEmpty()
    require(tag.removePrefix("v").matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Nomor versi rilis tidak valid" }
    val assets = release.assets.orEmpty().filterNotNull().filter {
        it.state == "uploaded" && it.browserDownloadUrl.orEmpty().startsWith(
            "https://github.com/TanDjendra/Gratify/releases/download/$tag/"
        )
    }
    // Explicit names avoid accidentally picking an ABI-specific, FOSS or unsigned APK.
    val names = listOf("Gratify.apk", "Gratify-full-universal-release.apk", "Gratify-full-universal.apk")
    require(manifest != null || expectedPackageName == "com.tan.gratify") {
        "Metadata pembaruan untuk aplikasi ini belum tersedia"
    }
    val apk = if (manifest != null) {
        require(manifest.packageName == expectedPackageName && manifest.channel == "full") { "Varian pembaruan tidak sesuai" }
        require(manifest.versionName == tag.removePrefix("v") && manifest.versionCode > 0) { "Versi metadata tidak sesuai rilis" }
        require(manifest.apkName in names) { "APK pembaruan harus varian universal resmi" }
        assets.singleOrNull { it.name == manifest.apkName }
    } else names.firstNotNullOfOrNull { name -> assets.singleOrNull { it.name == name } }
    requireNotNull(apk) { "APK universal Gratify belum tersedia pada rilis ini" }
    val digest = apk.digest?.removePrefix("sha256:")?.lowercase()
    val sha = manifest?.sha256?.lowercase() ?: digest
    require(sha != null && sha.matches(Regex("[a-f0-9]{64}"))) { "Verifikasi SHA-256 rilis belum tersedia" }
    if (manifest != null) {
        require(digest == null || digest == sha) { "Hash metadata berbeda dari aset rilis" }
        require(manifest.sizeBytes == apk.size?.toLong()) { "Ukuran metadata berbeda dari aset rilis" }
    }
    val size = manifest?.sizeBytes ?: apk.size?.toLong()
    require(size != null && size in 1..300L * 1024 * 1024) { "Ukuran APK tidak valid" }
    // Invalid or unavailable optional patches must not prevent a verified full update.
    val deltas = manifest?.deltas.orEmpty().take(8).mapNotNull { delta ->
        val asset = assets.singleOrNull { it.name == delta.patchName } ?: return@mapNotNull null
        if (delta.format != "gratify-delta-v1" || delta.fromVersionCode !in 1 until manifest!!.versionCode ||
            !delta.fromSha256.matches(Regex("[a-fA-F0-9]{64}")) ||
            !delta.sha256.matches(Regex("[a-fA-F0-9]{64}")) ||
            delta.fromSizeBytes !in 1..300L * 1024 * 1024 ||
            delta.sizeBytes !in 1..(size * 7 / 10) || asset.size?.toLong() != delta.sizeBytes ||
            !delta.patchName.matches(Regex("Gratify-from-[0-9]+-[a-f0-9]{12}\\.gdelta")) ||
            (asset.digest != null && asset.digest?.removePrefix("sha256:")?.lowercase() != delta.sha256.lowercase())) return@mapNotNull null
        UpdateDelta(delta.fromVersionCode, delta.fromSha256.lowercase(), delta.fromSizeBytes,
            asset.browserDownloadUrl!!, delta.sha256.lowercase(), delta.sizeBytes, delta.format)
    }.distinctBy { it.fromSha256 }
    return UpdateData(tag, release.publishedAt, release.body.orEmpty(), apk.browserDownloadUrl.orEmpty(),
        versionCode = manifest?.versionCode, sha256 = sha, sizeBytes = size, deltas = deltas)
}
