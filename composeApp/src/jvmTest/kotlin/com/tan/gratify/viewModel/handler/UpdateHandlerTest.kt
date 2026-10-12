package com.tan.gratify.viewModel.handler

import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.UpdateRepository
import com.tan.domain.utils.Resource
import com.tan.gratify.utils.VersionManager
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class UpdateHandlerTest {
    private class Fixture {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val values = mutableMapOf<String, String>()
        val toasts = mutableListOf<String>()
        var time = 100_000_000L
        var channel = DataStoreManager.GITHUB
        var calls = 0
        var wait: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        var update = UpdateData("v2.1.1", null, "", "https://github.com/", versionCode = VersionManager.getVersionCode() + 1)
        val store = Proxy.newProxyInstance(DataStoreManager::class.java.classLoader, arrayOf(DataStoreManager::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> flowOf(values[args!![0]])
                "putString" -> { values[args!![0] as String] = args[1] as String; Unit }
                "getUpdateChannel" -> flowOf(channel)
                else -> error("Unexpected datastore operation: ${method.name}")
            }
        } as DataStoreManager
        val repo = object : UpdateRepository {
            override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> = flow {
                calls++; wait?.await(); failure?.let { throw it }; emit(Resource.Success(update))
            }
            override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> = checkForGithubReleaseUpdate()
        }
        fun handler(timeout: Long = 1000) = UpdateHandler(scope, store, repo, { toasts += it }, { _, _ -> }, { time }, timeout)
    }
    private fun fixture(block: suspend (Fixture) -> Unit): Unit = runBlocking {
        VersionManager.initialize()
        val f = Fixture()
        try { block(f) } finally { f.job.cancelAndJoin() }
    }
    private suspend fun waitDone(handler: UpdateHandler) = withTimeout(2000) {
        while (handler.isCheckingUpdate.value) delay(5)
    }

    @Test fun successfulAutomaticChecksAreThrottledAndManualBypassesThrottle() = fixture { f ->
        val h = f.handler(); h.checkForUpdate(); h.checkForUpdate(); assertEquals(1, f.calls)
        h.checkForUpdate(isManual = true); assertEquals(2, f.calls)
        assertEquals(f.time.toString(), f.values["CheckForUpdateAt"])
    }
    @Test fun failuresDoNotClaimSuccessfulCheckAndCanRetryAfterFifteenMinutes() = fixture { f ->
        f.failure = IllegalStateException("offline")
        val h = f.handler(); h.checkForUpdate(); h.checkForUpdate(); assertEquals(1, f.calls)
        assertNull(f.values["CheckForUpdateAt"]); assertTrue(f.toasts.isEmpty())
        f.time += UpdateHandler.FAILED_CHECK_INTERVAL; h.checkForUpdate(); assertEquals(2, f.calls)
    }
    @Test fun unknownChannelDoesNotLeaveCheckingStuck() = fixture { f ->
        f.channel = "unsupported"
        val h = f.handler(); h.checkForUpdate(true)
        assertFalse(h.isCheckingUpdate.value); assertEquals(0, f.calls); assertEquals(1, f.toasts.size)
    }
    @Test fun failingFlowResetsBusyAndManualCheckExplainsFailure() = fixture { f ->
        f.failure = IllegalStateException("network")
        val h = f.handler(); h.checkForUpdate(true)
        assertFalse(h.isCheckingUpdate.value); assertNull(h.updateResponse.value); assertEquals(1, f.toasts.size)
    }
    @Test fun sameReleaseCanBeOpenedAgainByManualCheck() = fixture { f ->
        val h = f.handler(); h.checkForUpdate(true); h.showedUpdateDialog = false
        h.checkForUpdate(true); assertTrue(h.showedUpdateDialog); assertEquals(f.update, h.updateResponse.value)
        assertEquals(2L, h.updateDialogRequest.value)
    }
    @Test fun semanticEqualTagWithoutPrefixReportsAlreadyUpdated() = fixture { f ->
        f.update = f.update.copy(tagName = VersionManager.getVersionName(), versionCode = null)
        val h = f.handler(); h.checkForUpdate(true)
        assertFalse(h.showedUpdateDialog); assertTrue(f.toasts.single().contains("versi terbaru"))
    }
    @Test fun concurrentChecksShareOneRequest() = fixture { f ->
        f.wait = CompletableDeferred()
        val h = f.handler(); h.checkForUpdate(true); h.checkForUpdate(true)
        assertEquals(1, f.calls); assertTrue(h.isCheckingUpdate.value)
        f.wait!!.complete(Unit); waitDone(h); assertFalse(h.isCheckingUpdate.value)
    }
    @Test fun timeoutResetsCheckingAndAllowsAnotherAttempt() = fixture { f ->
        f.wait = CompletableDeferred()
        val h = f.handler(timeout = 30); h.checkForUpdate(true); waitDone(h)
        assertNull(f.values["CheckForUpdateAt"]); assertEquals(1, f.toasts.size)
        f.wait = null; h.checkForUpdate(true); assertEquals(2, f.calls); assertFalse(h.isCheckingUpdate.value)
    }
    @Test fun cancellationReleasesBusyState() = fixture { f ->
        f.wait = CompletableDeferred()
        val h = f.handler(); h.checkForUpdate(true); assertTrue(h.isCheckingUpdate.value)
        f.job.cancelAndJoin(); assertFalse(h.isCheckingUpdate.value)
    }
    @Test fun emptyFlowDoesNotLeaveCheckingStuck() = fixture { f ->
        val repo = object : UpdateRepository {
            override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> = emptyFlow()
            override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> = emptyFlow()
        }
        val h = UpdateHandler(f.scope, f.store, repo, { f.toasts += it }, { _, _ -> })
        h.checkForUpdate(true); assertFalse(h.isCheckingUpdate.value); assertEquals(1, f.toasts.size)
    }
}
