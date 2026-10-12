package com.tan.gratify.viewModel

import com.tan.domain.data.entities.UserProfile
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.UserRepository
import com.tan.domain.utils.uploadedProfileAvatar
import com.tan.gratify.utils.compressImage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface ProfileEditorGateway {
    fun currentUserId(): String?
    fun initialIdentity(): Pair<String, String>
    suspend fun uploadAvatar(owner: String, path: String): String
    suspend fun writeMetadata(owner: String, name: String, avatar: String)
    suspend fun writeProfile(owner: String, name: String, avatar: String)
    suspend fun cacheIdentity(owner: String, name: String, avatar: String)
}

class ProfileWriteException(val problem: ProfileSaveProblem) : Exception(problem.name)

class SupabaseProfileEditorGateway(
    private val supabase: SupabaseClient,
    private val users: UserRepository,
    private val preferences: DataStoreManager,
) : ProfileEditorGateway {
    override fun currentUserId() = supabase.auth.currentUserOrNull()?.id
    override fun initialIdentity(): Pair<String, String> {
        val metadata = supabase.auth.currentUserOrNull()?.userMetadata
        return metadata?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty() to
            metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull.uploadedProfileAvatar().orEmpty()
    }
    private fun checkOwner(owner: String) {
        if (currentUserId() != owner) throw ProfileWriteException(ProfileSaveProblem.ACCOUNT_CHANGED)
    }
    override suspend fun uploadAvatar(owner: String, path: String): String {
        checkOwner(owner)
        val bytes = try { requireNotNull(compressImage(path)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw ProfileWriteException(ProfileSaveProblem.PHOTO_FAILED) }
        checkOwner(owner)
        val bucket = supabase.storage.from("avatars")
        val file = "$owner.jpg"
        try {
            bucket.upload(path = file, data = bytes) { upsert = true; contentType = ContentType.Image.JPEG }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw ProfileWriteException(ProfileSaveProblem.PHOTO_UPLOAD_FAILED) }
        checkOwner(owner)
        return bucket.publicUrl(file) + "?cacheNonce=${Clock.System.now().toEpochMilliseconds()}"
    }
    override suspend fun writeMetadata(owner: String, name: String, avatar: String) {
        checkOwner(owner)
        supabase.auth.updateUser { data = buildJsonObject {
            put("display_name", name); put("avatar_url", avatar)
        } }
    }
    override suspend fun writeProfile(owner: String, name: String, avatar: String) {
        checkOwner(owner)
        users.upsertUserProfile(UserProfile(owner, name, avatar.uploadedProfileAvatar())).first().getOrThrow()
    }
    override suspend fun cacheIdentity(owner: String, name: String, avatar: String) {
        checkOwner(owner); preferences.putString("AppProfileName", name)
        checkOwner(owner); preferences.putString("AccountName", name)
        checkOwner(owner); preferences.putString("AppProfileImage", avatar)
    }
}
