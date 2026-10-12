package com.tan.data.repository

import com.tan.kotlinytmusicscraper.models.gratify.*
import kotlinx.serialization.json.*
import kotlin.test.*

class GithubUpdateSelectionTest {
    private val hash = "a".repeat(64)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun release(names: List<String>, digest: String = "sha256:$hash") = json.decodeFromString<GithubResponse>(
        buildJsonObject {
            put("tag_name", "v2.1.1"); put("draft", false); put("prerelease", false)
            put("assets", buildJsonArray { names.forEach { name -> add(buildJsonObject {
                put("name", name); put("state", "uploaded"); put("size", 100)
                put("digest", digest); put("browser_download_url", "https://github.com/TanDjendra/Gratify/releases/download/v2.1.1/$name")
            }) } })
        }.toString()
    )
    private val manifest = GithubUpdateManifest(62, "2.1.1", "com.tan.gratify", "full", "Gratify.apk", hash, 100)

    @Test fun assetOrderCannotPickFossOrSingleAbi() {
        val selected = selectGithubUpdate(release(listOf("Gratify-foss-universal-release.apk", "Gratify-full-arm64-v8a-release.apk", "Gratify.apk")))
        assertTrue(selected.apkUrl.endsWith("/Gratify.apk")); assertEquals(hash, selected.sha256)
    }
    @Test fun fullUniversalIsRecognized() {
        assertTrue(selectGithubUpdate(release(listOf("Gratify-full-universal-release.apk"))).apkUrl.endsWith("universal-release.apk"))
    }
    @Test fun onlyWrongVariantsAreRejected() {
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(release(listOf("Gratify-foss-universal-release.apk", "debug.apk"))) }
    }
    @Test fun missingHashIsRejected() {
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(release(listOf("Gratify.apk"), "")) }
    }
    @Test fun expectedVersionCodeAndSizeAreMapped() {
        val selected = selectGithubUpdate(release(listOf("Gratify.apk")), manifest)
        assertEquals(62L, selected.versionCode); assertEquals(100L, selected.sizeBytes)
    }
    @Test fun successorRequiresItsOwnPackageMetadata() {
        val r = release(listOf("Gratify.apk"))
        val next = manifest.copy(packageName = "com.tan.gratify.next")
        assertEquals(hash, selectGithubUpdate(r, next, "com.tan.gratify.next").sha256)
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(r, manifest, "com.tan.gratify.next") }
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(r, next) }
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(r, expectedPackageName = "com.tan.gratify.next") }
    }
    @Test fun inconsistentMetadataCannotSelectRelease() {
        for (bad in listOf(manifest.copy(versionName = "9.9.9"), manifest.copy(packageName = "evil.app"),
                manifest.copy(channel = "foss"), manifest.copy(sha256 = "b".repeat(64)), manifest.copy(sizeBytes = 101))) {
            assertFailsWith<IllegalArgumentException> { selectGithubUpdate(release(listOf("Gratify.apk")), bad) }
        }
    }
    @Test fun externalDownloadCannotBeSelected() {
        val r = release(listOf("Gratify.apk"))
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(r.copy(assets = r.assets?.map { it?.copy(browserDownloadUrl = "https://evil.invalid/Gratify.apk") })) }
    }
    @Test fun draftPrereleaseAndDuplicateCanonicalAssetsAreRejected() {
        val r = release(listOf("Gratify.apk"))
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(r.copy(draft = true)) }
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(r.copy(prerelease = true)) }
        assertFailsWith<IllegalArgumentException> { selectGithubUpdate(release(listOf("Gratify.apk", "Gratify.apk"))) }
    }
    private val deltaName = "Gratify-from-61-aaaaaaaaaaaa.gdelta"
    private val delta = GithubDeltaManifest(61, hash, 1000, deltaName, hash, 50)
    private fun deltaRelease() = release(listOf("Gratify.apk", deltaName)).let { r ->
        r.copy(assets = r.assets?.map { if (it?.name == deltaName) it.copy(size = 50) else it })
    }
    @Test fun verifiedPatchMetadataMapsWithoutChangingFullFallback() {
        val selected = selectGithubUpdate(deltaRelease(), manifest.copy(deltas = listOf(delta)))
        assertEquals(1, selected.deltas.size); assertEquals(50L, selected.deltas.single().sizeBytes)
        assertTrue(selected.apkUrl.endsWith("/Gratify.apk"))
    }
    @Test fun missingMalformedExternalOrLargePatchLeavesFullUpdateUsable() {
        for (bad in listOf(delta.copy(fromVersionCode = 62), delta.copy(format = "unknown"), delta.copy(sha256 = "bad"),
            delta.copy(fromSha256 = "bad"), delta.copy(fromSizeBytes = Long.MAX_VALUE), delta.copy(sizeBytes = 71),
            delta.copy(patchName = "../evil.gdelta"))) {
            assertTrue(selectGithubUpdate(deltaRelease(), manifest.copy(deltas = listOf(bad))).deltas.isEmpty())
        }
        assertTrue(selectGithubUpdate(release(listOf("Gratify.apk")), manifest.copy(deltas = listOf(delta))).deltas.isEmpty())
        val external = deltaRelease().let { r -> r.copy(assets = r.assets?.map { if (it?.name == deltaName) it.copy(browserDownloadUrl = "https://evil.invalid/$deltaName") else it }) }
        assertTrue(selectGithubUpdate(external, manifest.copy(deltas = listOf(delta))).deltas.isEmpty())
    }
    @Test fun incompleteOptionalPatchDoesNotBlockFullRelease() {
        val decoded = json.decodeFromString<GithubUpdateManifest>("""{"versionCode":62,"versionName":"2.1.1","packageName":"com.tan.gratify","channel":"full","apkName":"Gratify.apk","sha256":"$hash","sizeBytes":100,"deltas":[{}]}""")
        val selected = selectGithubUpdate(release(listOf("Gratify.apk")), decoded)
        assertTrue(selected.deltas.isEmpty()); assertEquals(62L, selected.versionCode)
    }
}
