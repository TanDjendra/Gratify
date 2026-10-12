package com.tan.gratify.utils

import com.tan.domain.data.model.update.UpdateData
import kotlin.test.*

class UpdatePackagePolicyTest {
    private val installed = UpdatePackageIdentity("com.tan.gratify", 61, "2.1.0", setOf("trusted"), "full")
    private val candidate = installed.copy(versionCode = 62, versionName = "2.1.1")
    private val update = UpdateData("v2.1.1", null, "", "https://github.com/", versionCode = 62)

    @Test fun compatibleUpdatePasses() { validateUpdateIdentity(installed, candidate, update) }
    @Test fun unchangedVersionCodeIsRejectedEvenWhenNameChanges() {
        assertFailsWith<IllegalArgumentException> { validateUpdateIdentity(installed, candidate.copy(versionCode = 61), update) }
    }
    @Test fun downgradeIsRejected() {
        assertFailsWith<IllegalArgumentException> { validateUpdateIdentity(installed, candidate.copy(versionCode = 60), update) }
    }
    @Test fun lostOriginalSigningKeyCannotBeBypassed() {
        assertFailsWith<IncompatibleUpdatePackage> { validateUpdateIdentity(installed, candidate.copy(certificateSha256 = setOf("new-key")), update) }
    }
    @Test fun debugAndReleaseCannotOverwriteEachOther() {
        assertFailsWith<IncompatibleUpdatePackage> { validateUpdateIdentity(installed.copy(packageName = "com.tan.gratify.dev"), candidate, update) }
    }
    @Test fun expectedCodeMustMatchApk() {
        assertFailsWith<IllegalArgumentException> { validateUpdateIdentity(installed, candidate.copy(versionCode = 63), update) }
    }
    @Test fun expectedNameMustMatchApk() {
        assertFailsWith<IllegalArgumentException> { validateUpdateIdentity(installed, candidate.copy(versionName = "2.1.2"), update) }
    }
    @Test fun missingTrustedSignerIsRejected() {
        assertFailsWith<IncompatibleUpdatePackage> { validateUpdateIdentity(installed.copy(certificateSha256 = emptySet()), candidate, update) }
    }
    @Test fun legacyReleaseStillChecksActualApkCodeAndSigner() {
        validateUpdateIdentity(installed, candidate, update.copy(versionCode = null))
    }
    @Test fun mismatchedMetadataPackageIsRejected() {
        assertFailsWith<IncompatibleUpdatePackage> { validateUpdateIdentity(installed, candidate, update.copy(packageName = "other.app")) }
    }
    @Test fun renamedFossApkCannotOverwriteFullVariant() {
        assertFailsWith<IncompatibleUpdatePackage> { validateUpdateIdentity(installed, candidate.copy(channel = "foss"), update) }
    }
    @Test fun missingApkChannelIsRejected() {
        assertFailsWith<IncompatibleUpdatePackage> { validateUpdateIdentity(installed, candidate.copy(channel = null), update) }
    }
}
