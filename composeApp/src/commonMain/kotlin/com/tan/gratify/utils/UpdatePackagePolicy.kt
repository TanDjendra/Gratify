package com.tan.gratify.utils

import com.tan.domain.data.model.update.UpdateData

data class UpdatePackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val certificateSha256: Set<String>,
    val channel: String? = null,
)

class IncompatibleUpdatePackage(message: String) : IllegalArgumentException(message)

fun validateUpdateIdentity(installed: UpdatePackageIdentity, candidate: UpdatePackageIdentity, expected: UpdateData) {
    if (candidate.packageName != installed.packageName || candidate.packageName != expected.packageName) {
        throw IncompatibleUpdatePackage("APK ini memakai identitas aplikasi berbeda. Jangan hapus Gratify lama sebelum data dicadangkan.")
    }
    if (installed.certificateSha256.isEmpty() || installed.certificateSha256 != candidate.certificateSha256) {
        throw IncompatibleUpdatePackage("Pembaruan memakai tanda tangan berbeda dari aplikasi Anda. Data tetap aman di Gratify yang terpasang. Hubungi dukungan untuk perpindahan versi; jangan hapus aplikasi lama sebelum mencadangkan data.")
    }
    if (candidate.channel != expected.channel || candidate.channel != installed.channel) {
        throw IncompatibleUpdatePackage("Varian pembaruan berbeda dari Gratify yang terpasang. Gunakan rilis resmi untuk varian aplikasi Anda.")
    }
    require(candidate.versionCode > installed.versionCode) { "APK ini tidak lebih baru dari Gratify yang terpasang" }
    require(expected.versionCode == null || expected.versionCode == candidate.versionCode) { "Nomor versi APK berbeda dari metadata rilis" }
    require(candidate.versionName == expected.tagName.removePrefix("v")) { "Versi APK berbeda dari rilis yang dipilih" }
}
