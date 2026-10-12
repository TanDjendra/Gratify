package com.tan.gratify.viewModel.support

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val SUPPORT_PROMPT_PREFERENCE = "developer_support_prompt_v1"

enum class SupportPromptStatus { Idle, Pending, Visible }

@Serializable
internal data class SupportPromptHistory(
    val openings: Long = 0,
    val lastShownOpening: Long? = null,
    val optedOut: Boolean = false,
)

/** Installation-wide history, independent of account data and the legacy review counter. */
class SupportPromptController(
    private val read: suspend () -> String?,
    private val write: suspend (String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var history: SupportPromptHistory? = null
    private var lastSession: String? = null
    private val _status = MutableStateFlow(SupportPromptStatus.Idle)
    val status = _status.asStateFlow()

    /** The root app provides a saved session ID, so recomposition and rotation don't count twice. */
    suspend fun registerOpening(session: String) = storageOperation {
        if (lastSession == session) return@storageOperation
        val previous = load()
        val next = previous.copy(openings = previous.openings.coerceAtMost(Long.MAX_VALUE - 1) + 1)
        persist(next)
        lastSession = session
        val due = !next.optedOut && (next.lastShownOpening == null || next.openings - next.lastShownOpening >= 20)
        // Returning from the background must preserve an already open sheet and its choices.
        if (_status.value != SupportPromptStatus.Visible) {
            _status.value = if (due) SupportPromptStatus.Pending else SupportPromptStatus.Idle
        }
    }

    /** Only the eligible homepage calls this; blocked openings don't consume a presentation. */
    suspend fun present() = storageOperation {
        if (_status.value != SupportPromptStatus.Pending) return@storageOperation
        val current = load()
        if (current.optedOut) return@storageOperation
        persist(current.copy(lastShownOpening = current.openings))
        _status.value = SupportPromptStatus.Visible
    }

    suspend fun dismiss(doNotShowAgain: Boolean) = storageOperation {
        _status.value = SupportPromptStatus.Idle
        val current = load()
        if (doNotShowAgain && !current.optedOut) {
            // Keep the choice in memory even if storage temporarily becomes unavailable.
            history = current.copy(optedOut = true)
            persist(history!!)
        }
    }

    private suspend fun load(): SupportPromptHistory {
        history?.let { return it }
        val encoded = read()
        val decoded = if (encoded.isNullOrBlank()) SupportPromptHistory() else
            runCatching { json.decodeFromString<SupportPromptHistory>(encoded) }.getOrDefault(SupportPromptHistory())
        val openings = decoded.openings.coerceAtLeast(0)
        return decoded.copy(openings = openings,
            lastShownOpening = decoded.lastShownOpening?.coerceIn(0, openings)).also { history = it }
    }

    private suspend fun persist(value: SupportPromptHistory) {
        write(json.encodeToString(value))
        history = value
    }

    private suspend fun storageOperation(action: suspend () -> Unit) {
        try {
            mutex.withLock { action() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A support prompt must never interrupt app startup or block playback.
            _status.value = SupportPromptStatus.Idle
        }
    }
}
