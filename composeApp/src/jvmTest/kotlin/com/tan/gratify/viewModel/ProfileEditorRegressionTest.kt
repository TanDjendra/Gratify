package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

class ProfileEditorRegressionTest {
    private class Gateway : ProfileEditorGateway {
        var owner: String? = "owner-a"
        var uploadGate: CompletableDeferred<Unit>? = null
        var metadataGate: CompletableDeferred<Unit>? = null
        var failPhoto = false
        var failUpload = false
        var failWrite = false
        var failCache = false
        var timeoutCache = false
        var uploads = 0
        val metadata = mutableListOf<Triple<String, String, String>>()
        val profiles = mutableListOf<Triple<String, String, String>>()
        val caches = mutableListOf<Triple<String, String, String>>()
        override fun currentUserId() = owner
        override fun initialIdentity() = "Current account" to ""
        override suspend fun uploadAvatar(owner: String, path: String): String {
            uploads++; uploadGate?.await()
            if (failPhoto) throw ProfileWriteException(ProfileSaveProblem.PHOTO_FAILED)
            if (failUpload) throw ProfileWriteException(ProfileSaveProblem.PHOTO_UPLOAD_FAILED)
            return "https://example.invalid/storage/v1/object/public/avatars/$owner.jpg?cacheNonce=1"
        }
        override suspend fun writeMetadata(owner: String, name: String, avatar: String) {
            metadata += Triple(owner, name, avatar)
            metadataGate?.await()
        }
        override suspend fun writeProfile(owner: String, name: String, avatar: String) {
            if (failWrite) error("synthetic offline")
            profiles += Triple(owner, name, avatar)
        }
        override suspend fun cacheIdentity(owner: String, name: String, avatar: String) {
            if (timeoutCache) withTimeout(10) { awaitCancellation() }
            if (failCache) error("synthetic disk failure")
            caches += Triple(owner, name, avatar)
        }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(3000) { while (!condition()) delay(10) }
    private suspend fun fixture(block: suspend (Gateway, CreateProfileViewModel) -> Unit) {
        val gateway = Gateway(); val vm = CreateProfileViewModel(gateway)
        try { block(gateway, vm) } finally { vm.viewModelScope.cancel() }
    }
    @Test fun existingServerIdentityIsTheDraftAndReopeningClearsOldEdits(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { _, vm ->
            vm.beginEditing("owner-a", "Server name", "server-avatar")
            assertEquals("Server name", vm.uiState.value.displayName); assertFalse(vm.uiState.value.hasChanges)
            vm.updateDisplayName("Unsaved name"); assertTrue(vm.uiState.value.hasChanges)
            vm.beginEditing("owner-a", "Server name", "server-avatar")
            assertEquals("Server name", vm.uiState.value.displayName); assertEquals("server-avatar", vm.uiState.value.avatarPath)
            assertFalse(vm.uiState.value.hasChanges)
        }
    }
    @Test fun submissionIsImmutableAndRepeatedTapsDoNotWriteTwice(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.updateDisplayName("  Nara  "); vm.updateAvatarPath("content://synthetic/photo")
            gateway.uploadGate = CompletableDeferred(); vm.saveProfile()
            await { gateway.uploads == 1 }; assertTrue(vm.uiState.value.isSaving)
            vm.updateDisplayName("Changed during save"); vm.updateAvatarPath("other"); vm.saveProfile()
            assertEquals("  Nara  ", vm.uiState.value.displayName)
            gateway.uploadGate!!.complete(Unit); await { vm.uiState.value.saveSuccess }
            assertEquals(1, gateway.profiles.size); assertEquals("Nara", gateway.profiles.single().second)
            assertEquals(gateway.profiles, gateway.caches); assertFalse(vm.uiState.value.hasChanges)
            vm.saveProfile(); delay(30); assertEquals(1, gateway.profiles.size)
        }
    }
    @Test fun accountChangeDuringUploadStopsMetadataProfileAndCacheWrites(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.updateAvatarPath("content://synthetic/photo"); gateway.uploadGate = CompletableDeferred(); vm.saveProfile()
            await { gateway.uploads == 1 }; gateway.owner = "owner-b"; gateway.uploadGate!!.complete(Unit)
            await { !vm.uiState.value.isSaving }
            assertEquals(ProfileSaveProblem.ACCOUNT_CHANGED, vm.uiState.value.problem)
            assertTrue(gateway.metadata.isEmpty()); assertTrue(gateway.profiles.isEmpty()); assertTrue(gateway.caches.isEmpty())
            assertFalse(vm.uiState.value.saveSuccess)
        }
    }
    @Test fun failedWriteKeepsDraftAndRetryCommitsIt(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.updateDisplayName("Nara"); gateway.failWrite = true; vm.saveProfile()
            await { vm.uiState.value.problem != null && !vm.uiState.value.isSaving }
            assertEquals("Nara", vm.uiState.value.displayName); assertFalse(vm.uiState.value.saveSuccess)
            assertTrue(gateway.caches.isEmpty())
            gateway.failWrite = false; vm.saveProfile(); await { vm.uiState.value.saveSuccess }
            assertNull(vm.uiState.value.problem); assertEquals("Nara", gateway.profiles.single().second)
        }
    }
    @Test fun removingPhotoPublishesEmptyAvatarAndUpdatesAllCaches(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.beginEditing("owner-a", "Nara", "https://example.invalid/storage/v1/object/public/avatars/owner-a.jpg")
            vm.updateAvatarPath(""); vm.saveProfile(); await { vm.uiState.value.saveSuccess }
            assertEquals(Triple("owner-a", "Nara", ""), gateway.profiles.single())
            assertEquals(gateway.profiles, gateway.metadata); assertEquals(gateway.profiles, gateway.caches)
            assertEquals(0, gateway.uploads)
        }
    }
    @Test fun photoProcessingErrorIsRecoverableWithoutWrites(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.updateAvatarPath("content://bad/photo"); gateway.failPhoto = true; vm.saveProfile()
            await { !vm.uiState.value.isSaving && vm.uiState.value.problem != null }
            assertEquals(ProfileSaveProblem.PHOTO_FAILED, vm.uiState.value.problem)
            assertEquals("content://bad/photo", vm.uiState.value.avatarPath); assertTrue(gateway.metadata.isEmpty())
            gateway.failPhoto = false; vm.updateAvatarPath(""); vm.saveProfile(); await { vm.uiState.value.saveSuccess }
        }
    }
    @Test fun invalidNamesAndSignedOutOrChangedAccountsCannotSubmit(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.updateDisplayName("  "); vm.saveProfile(); assertEquals(ProfileSaveProblem.NAME_REQUIRED, vm.uiState.value.problem)
            vm.updateDisplayName("a".repeat(65)); vm.saveProfile(); assertEquals(ProfileSaveProblem.NAME_TOO_LONG, vm.uiState.value.problem)
            vm.beginEditing(null, "Nara", null); vm.saveProfile(); assertEquals(ProfileSaveProblem.SIGN_IN_REQUIRED, vm.uiState.value.problem)
            vm.beginEditing("owner-a", "Nara", null); gateway.owner = "owner-b"; vm.saveProfile()
            assertEquals(ProfileSaveProblem.ACCOUNT_CHANGED, vm.uiState.value.problem); assertTrue(gateway.metadata.isEmpty())
        }
    }
    @Test fun cacheFailureDoesNotMisreportConfirmedServerSave(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            gateway.failCache = true; vm.saveProfile(); await { vm.uiState.value.saveSuccess }
            assertEquals(1, gateway.profiles.size); assertNull(vm.uiState.value.problem); assertFalse(vm.uiState.value.isSaving)
        }
    }
    @Test fun cacheTimeoutDoesNotMisreportConfirmedServerSave(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            gateway.timeoutCache = true
            vm.updateAvatarPath("file:///synthetic/new-avatar.jpg")
            vm.saveProfile()
            await { !vm.uiState.value.isSaving }
            assertEquals(1, gateway.profiles.size)
            assertTrue(vm.uiState.value.saveSuccess)
            assertNull(vm.uiState.value.problem)
            assertEquals(gateway.profiles.single().third, vm.uiState.value.avatarPath)
            assertFalse(vm.uiState.value.hasChanges)
        }
    }
    @Test fun uploadFailureKeepsPhotoDraftAndRetryCanSaveIt(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.beginEditing("owner-a", "Nara", "previous-avatar")
            vm.updateAvatarPath("file:///synthetic/replacement.jpg")
            gateway.failUpload = true
            vm.saveProfile(); await { !vm.uiState.value.isSaving }
            assertEquals(ProfileSaveProblem.PHOTO_UPLOAD_FAILED, vm.uiState.value.problem)
            assertNull(vm.uiState.value.saveStage)
            assertEquals("file:///synthetic/replacement.jpg", vm.uiState.value.avatarPath)
            assertEquals("previous-avatar", vm.uiState.value.initialAvatar)
            assertTrue(gateway.profiles.isEmpty()); assertTrue(gateway.metadata.isEmpty())
            gateway.failUpload = false
            vm.saveProfile(); await { vm.uiState.value.saveSuccess }
            assertNull(vm.uiState.value.problem)
            assertEquals(1, gateway.profiles.size)
        }
    }
    @Test fun photoSaveShowsUploadThenUpdateUntilTheConfirmedResult(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            gateway.uploadGate = CompletableDeferred()
            gateway.metadataGate = CompletableDeferred()
            vm.updateAvatarPath("file:///synthetic/new-photo.jpg")
            vm.saveProfile(); await { gateway.uploads == 1 }
            assertTrue(vm.uiState.value.isSaving)
            assertEquals(ProfileSaveStage.UPLOADING_PHOTO, vm.uiState.value.saveStage)
            gateway.uploadGate!!.complete(Unit)
            await { gateway.metadata.size == 1 }
            assertTrue(vm.uiState.value.isSaving)
            assertEquals(ProfileSaveStage.SAVING_PROFILE, vm.uiState.value.saveStage)
            assertTrue(gateway.profiles.isEmpty())
            vm.saveProfile(); assertEquals(1, gateway.uploads)
            gateway.metadataGate!!.complete(Unit)
            await { vm.uiState.value.saveSuccess && !vm.uiState.value.isSaving }
            assertNull(vm.uiState.value.saveStage)
            assertNull(vm.uiState.value.problem)
            assertEquals(1, gateway.profiles.size)
        }
    }
    @Test fun selectedPhotoReplacesExistingAvatarAndPhotoOnlySaveUploadsIt(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            val old = "https://example.invalid/storage/v1/object/public/avatars/owner-a.jpg?cacheNonce=0"
            vm.beginEditing("owner-a", "Nara", old)
            assertTrue(vm.applySelectedAvatar("owner-a", "file:///synthetic/crop.jpg"))
            assertEquals("file:///synthetic/crop.jpg", vm.uiState.value.avatarPath)
            assertTrue(vm.uiState.value.hasChanges)
            vm.saveProfile(); await { vm.uiState.value.saveSuccess }
            assertEquals(1, gateway.uploads)
            assertEquals("Nara", gateway.profiles.single().second)
            assertNotEquals(old, gateway.profiles.single().third)
            assertEquals(gateway.profiles, gateway.caches)
        }
    }
    @Test fun cancelledPickerKeepsDraftAndResultCannotCrossAccounts(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { gateway, vm ->
            vm.beginEditing("owner-a", "Nara", "previous-photo")
            vm.updateDisplayName("Unsaved name")
            assertFalse(vm.applySelectedAvatar("owner-a", null))
            assertEquals("previous-photo", vm.uiState.value.avatarPath)
            assertEquals("Unsaved name", vm.uiState.value.displayName)
            gateway.owner = "owner-b"
            assertFalse(vm.applySelectedAvatar("owner-a", "file:///wrong-account.jpg"))
            vm.beginEditing("owner-b", "Other account", "other-photo")
            assertFalse(vm.applySelectedAvatar("owner-a", "file:///late-result.jpg"))
            assertFalse(vm.applySelectedAvatar(null, "file:///unsolicited.jpg"))
            assertEquals("other-photo", vm.uiState.value.avatarPath)
            assertTrue(gateway.profiles.isEmpty())
        }
    }
}
