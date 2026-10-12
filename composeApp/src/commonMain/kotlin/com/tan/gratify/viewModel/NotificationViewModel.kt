package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.extension.now
import com.tan.domain.repository.CommonRepository
import com.tan.domain.repository.UserRepository
import com.tan.gratify.viewModel.base.BaseViewModel
import com.tan.gratify.viewModel.handler.syncFollowerNotifications
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class RemoteNotification(val title: String, val body: String, val url: String? = null, val time: String? = null)

enum class NotificationSyncProblem { ANNOUNCEMENTS, SOCIAL, FOLLOWERS, FOLLOWING }

class NotificationViewModel(
    private val commonRepository: CommonRepository,
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient,
    private val fetchSocial: suspend (String) -> List<NotificationEntity> = { com.tan.gratify.viewModel.handler.fetchSocialNotifications(supabase, it) },
    // The retired public JSON feed returned 404. Production announcements are cached by the push worker.
    private val fetchRemote: (suspend () -> List<RemoteNotification>)? = null,
) : BaseViewModel() {
    private val _listNotification = MutableStateFlow<List<NotificationEntity>?>(null)
    val listNotification = _listNotification.asStateFlow()
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following = _following.asStateFlow()
    private val _followingBusy = MutableStateFlow<Set<String>>(emptySet())
    val followingBusy = _followingBusy.asStateFlow()
    private val _followingReady = MutableStateFlow(false)
    val followingReady = _followingReady.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _readFailed = MutableStateFlow(false)
    val readFailed = _readFailed.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _syncProblems = MutableStateFlow<Set<NotificationSyncProblem>>(emptySet())
    val syncProblems = _syncProblems.asStateFlow()
    private val _deleting = MutableStateFlow<Set<Long>>(emptySet())
    val deleting = _deleting.asStateFlow()
    private var readJob: kotlinx.coroutines.Job? = null
    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            supabase.auth.sessionStatus.map { supabase.auth.currentUserOrNull()?.id }.distinctUntilChanged().collect {
                refreshJob?.cancelAndJoin()
                readJob?.cancelAndJoin()
                _listNotification.value = null
                _error.value = null
                _following.value = emptySet(); _followingBusy.value = emptySet(); _followingReady.value = false
                readNotifications()
                refresh()
            }
        }
    }

    private fun readNotifications() {
        if (readJob?.isActive == true) return
        _readFailed.value = false
        readJob = viewModelScope.launch {
            try {
                commonRepository.getAllNotifications().collect { rows ->
                    _readFailed.value = false
                    val owner = supabase.auth.currentUserOrNull()?.id
                    val databaseOwner = commonRepository.notificationAccountOwner()
                    _listNotification.value = if (databaseOwner == owner) rows.orEmpty()
                        .filter { it.single.firstOrNull()?.get("ownerId")?.let { expected -> expected == owner } != false }
                        .sortedWith(compareByDescending<NotificationEntity> { it.time }.thenByDescending { it.id }) else emptyList()
                    // Account restoration can finish after auth; the DB observer emits on ownership changes.
                    if (owner != null && !_refreshing.value && !_followingReady.value && databaseOwner == owner) refresh()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _readFailed.value = true
                if (_listNotification.value == null) _listNotification.value = emptyList()
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        if (_readFailed.value || readJob?.isActive != true) readNotifications()
        val owner = supabase.auth.currentUserOrNull()?.id
        _refreshing.value = true; _syncProblems.value = emptySet()
        refreshJob = viewModelScope.launch {
            try {
                kotlinx.coroutines.supervisorScope {
                    if (fetchRemote != null) launch { syncPart(NotificationSyncProblem.ANNOUNCEMENTS) { syncAnnouncements() } }
                    if (owner != null) {
                        suspend fun ready(): Boolean {
                            return withTimeout(5_000) {
                                while (supabase.auth.currentUserOrNull()?.id == owner && commonRepository.notificationAccountOwner() != owner) kotlinx.coroutines.delay(100)
                                supabase.auth.currentUserOrNull()?.id == owner && commonRepository.notificationAccountOwner() == owner
                            }
                        }
                        launch { syncPart(NotificationSyncProblem.SOCIAL) {
                            if (ready()) fetchSocial(owner).forEach { row ->
                                if (supabase.auth.currentUserOrNull()?.id == owner && commonRepository.notificationAccountOwner() == owner) commonRepository.insertNotificationOnce(row)
                            }
                        } }
                        launch { syncPart(NotificationSyncProblem.FOLLOWERS) {
                            if (ready()) syncFollowerNotifications(userRepository, commonRepository, { supabase.auth.currentUserOrNull()?.id })
                        } }
                        launch { syncPart(NotificationSyncProblem.FOLLOWING) {
                            if (ready()) {
                                val ids = mutableSetOf<String>()
                                var offset = 0
                                do {
                                    val page = userRepository.fetchFollowing(owner, offset, 100).first().getOrThrow()
                                    if (supabase.auth.currentUserOrNull()?.id != owner) return@syncPart
                                    ids += page.map { it.id }; offset += page.size
                                } while (page.size == 100)
                                if (supabase.auth.currentUserOrNull()?.id == owner && commonRepository.notificationAccountOwner() == owner) {
                                    _following.value = ids; _followingReady.value = true
                                }
                            }
                        } }
                    }
                }
            } finally { _refreshing.value = false }
        }
    }

    private suspend fun syncPart(part: NotificationSyncProblem, block: suspend () -> Unit) {
        try { withTimeout(30_000) { block() } }
        catch (_: kotlinx.coroutines.TimeoutCancellationException) { _syncProblems.update { it + part } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _syncProblems.update { it + part } }
    }

    private suspend fun syncAnnouncements() {
        val fetch = fetchRemote ?: return
        for (remote in fetch().distinct()) {
            if (remote.title.isBlank() && remote.body.isBlank()) continue
            val time = remote.time?.let { value ->
                runCatching { LocalDateTime.parse(value) }.getOrNull()
                    ?: runCatching { Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
            } ?: now()
            commonRepository.insertNotificationOnce(NotificationEntity(
                channelId = "developer", name = remote.title.take(240),
                single = listOf(mapOf("title" to remote.body.take(1200), "browseId" to remote.url.orEmpty().take(800))), time = time,
            ))
        }
    }

    fun followBack(followerId: String) {
        if (followerId.isBlank() || followerId in _followingBusy.value || followerId in _following.value) return
        val owner = supabase.auth.currentUserOrNull()?.id
        if (owner == null) { _error.value = "Masuk untuk mengikuti akun ini."; return }
        if (followerId == owner) return
        if (!_followingReady.value) { _error.value = "Status mengikuti belum dimuat. Ketuk Perbarui lalu coba lagi."; return }
        _followingBusy.update { it + followerId }
        viewModelScope.launch {
            try {
                check(supabase.auth.currentUserOrNull()?.id == owner && commonRepository.notificationAccountOwner() == owner)
                withTimeout(15_000) { userRepository.followUser(owner, followerId).first().getOrThrow() }
                if (supabase.auth.currentUserOrNull()?.id == owner) _following.update { it + followerId }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                if (supabase.auth.currentUserOrNull()?.id == owner) _error.value = "Koneksi terlalu lama. Coba lagi."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (supabase.auth.currentUserOrNull()?.id == owner) _error.value = "Gagal mengikuti akun. Coba lagi." }
            finally { if (supabase.auth.currentUserOrNull()?.id == owner) _followingBusy.update { it - followerId } }
        }
    }

    fun clearError() { _error.value = null }
    fun actionFailed(message: String) { _error.value = message }
    fun openAccountNotification(row: NotificationEntity, action: () -> Unit) {
        val owner = supabase.auth.currentUserOrNull()?.id
        viewModelScope.launch {
            val expected = row.single.firstOrNull()?.get("ownerId")
            try {
                if (owner != null && expected == owner && supabase.auth.currentUserOrNull()?.id == owner && commonRepository.notificationAccountOwner() == owner) action()
                else _error.value = "Notifikasi ini tidak tersedia untuk akun saat ini. Perbarui kotak notifikasi."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _error.value = "Tujuan notifikasi belum dapat dibuka. Coba lagi." }
        }
    }
    fun deleteNotification(id: Long) {
        if (id in _deleting.value) return
        val row = _listNotification.value?.firstOrNull { it.id == id } ?: return
        val owner = supabase.auth.currentUserOrNull()?.id
        _deleting.update { it + id }
        viewModelScope.launch {
            try {
                check(supabase.auth.currentUserOrNull()?.id == owner)
                check(commonRepository.notificationAccountOwner() == owner)
                row.single.firstOrNull()?.get("ownerId")?.let { check(it == owner && commonRepository.notificationAccountOwner() == owner) }
                commonRepository.deleteNotification(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (supabase.auth.currentUserOrNull()?.id == owner) _error.value = "Notifikasi belum berhasil dihapus. Coba lagi." }
            finally { _deleting.update { it - id } }
        }
    }
}
