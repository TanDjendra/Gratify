package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.repository.UserRepository
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class FollowListState(
    val followersLoading: Boolean = true,
    val followingLoading: Boolean = true,
    val followers: List<UserProfile> = emptyList(),
    val following: List<UserProfile> = emptyList(),
    val followersError: String? = null,
    val followingError: String? = null,
    val moreFollowers: Boolean = false,
    val moreFollowing: Boolean = false,
) {
    val isLoading: Boolean get() = followersLoading || followingLoading
}

class FollowListViewModel(
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient,
) : BaseViewModel() {
    private val _state = MutableStateFlow(FollowListState())
    val state = _state.asStateFlow()
    private var userId: String? = null
    private var viewer: String? = null
    private var generation = 0
    private var followersJob: Job? = null
    private var followingJob: Job? = null
    private var followersOffset = 0
    private var followingOffset = 0

    fun loadData(userId: String) {
        followersJob?.cancel()
        followingJob?.cancel()
        generation++
        this.userId = userId
        viewer = supabase.auth.currentUserOrNull()?.id
        followersOffset = 0
        followingOffset = 0
        _state.value = FollowListState()
        loadPage(true)
        loadPage(false)
    }

    fun loadMoreFollowers() { if (_state.value.moreFollowers && !_state.value.followersLoading) loadPage(true) }
    fun loadMoreFollowing() { if (_state.value.moreFollowing && !_state.value.followingLoading) loadPage(false) }
    fun retryFollowers() { if (!_state.value.followersLoading) loadPage(true) }
    fun retryFollowing() { if (!_state.value.followingLoading) loadPage(false) }

    private fun loadPage(followers: Boolean) {
        val target = userId ?: return
        val request = generation
        val offset = if (followers) followersOffset else followingOffset
        fun active() = request == generation && supabase.auth.currentUserOrNull()?.id == viewer
        _state.update { if (followers) it.copy(followersLoading = true, followersError = null)
            else it.copy(followingLoading = true, followingError = null) }
        val job = viewModelScope.launch {
            try {
                val page = withTimeout(20_000) {
                    (if (followers) userRepository.fetchFollowers(target, offset, 40)
                    else userRepository.fetchFollowing(target, offset, 40)).first().getOrThrow()
                }
                if (!active()) return@launch
                if (followers) followersOffset += page.size else followingOffset += page.size
                _state.update { if (followers) it.copy(followers = (it.followers + page).distinctBy { p -> p.id }, moreFollowers = page.size == 40)
                    else it.copy(following = (it.following + page).distinctBy { p -> p.id }, moreFollowing = page.size == 40) }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                if (active()) setError(followers)
            } catch (_: Exception) {
                if (active()) setError(followers)
            } finally {
                if (active()) _state.update { if (followers) it.copy(followersLoading = false) else it.copy(followingLoading = false) }
            }
        }
        if (followers) followersJob = job else followingJob = job
    }

    private fun setError(followers: Boolean) {
        _state.update { if (followers) it.copy(followersError = "Pengikut belum dapat dimuat. Coba lagi.")
            else it.copy(followingError = "Daftar mengikuti belum dapat dimuat. Coba lagi.") }
    }
}
