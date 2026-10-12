package com.tan.data.repository

import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.repository.UpdateRepository
import com.tan.domain.utils.Resource
import com.tan.kotlinytmusicscraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException

internal class UpdateRepositoryImpl(
    private val youTube: YouTube,
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            val result = try {
                val release = youTube.checkForGithubReleaseUpdate().getOrThrow()
                val manifests = release.assets.orEmpty().filterNotNull().filter { it.name == "gratify-update.json" }
                require(manifests.size <= 1) { "Metadata pembaruan duplikat" }
                val manifest = manifests.singleOrNull()?.let { asset ->
                    require(asset.state == "uploaded" && asset.browserDownloadUrl.orEmpty().startsWith(
                        "https://github.com/TanDjendra/Gratify/releases/download/${release.tagName}/"
                    )) { "Tautan metadata tidak valid" }
                    youTube.githubUpdateManifest(asset.browserDownloadUrl.orEmpty())
                }
                Resource.Success(selectGithubUpdate(release, manifest, updatePackageName()))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Resource.Error<UpdateData>(error.message ?: "Gagal memeriksa pembaruan") }
            emit(result)
        }.flowOn(Dispatchers.IO)

    override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> =
        flow {
            if (updatePackageName() != "com.tan.gratify") {
                emit(Resource.Error<UpdateData>("Aplikasi ini belum tersedia di F-Droid"))
                return@flow
            }
            youTube
                .checkForFdroidUpdate()
                .onSuccess { response ->
                    val latestVersion = response.packages.maxByOrNull { it.versionCode }
                    if (latestVersion == null) {
                        emit(Resource.Error<UpdateData>("Gratify belum tersedia di F-Droid"))
                        return@onSuccess
                    }
                    emit(
                        Resource.Success(
                            UpdateData(
                                tagName = latestVersion.versionName,
                                releaseTime = null,
                                body =
                                    $$"""
                                    ### Update via F-Droid, changelogs: 
                                    - https://github.com/TanDjendra/Gratify/blob/main/fastlane/metadata/android/en-US/changelogs/$${latestVersion.versionCode}.txt
                                    """.trimIndent(),
                                apkUrl = "",
                            ),
                        ),
                    )
                }.onFailure {
                    emit(Resource.Error<UpdateData>(it.message ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)
}
