package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.repository.UserRepository
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowListState(
    val isLoading: Boolean = false,
    val followers: List<UserProfile> = emptyList(),
    val following: List<UserProfile> = emptyList(),
)

class FollowListViewModel(
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient
) : BaseViewModel() {

    private val _state = MutableStateFlow(FollowListState())
    val state: StateFlow<FollowListState> = _state.asStateFlow()

    fun loadData(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            // Load followers
            launch {
                userRepository.getFollowers(userId).collectLatest { users ->
                    _state.update { it.copy(followers = users) }
                }
            }
            
            // Load following
            launch {
                userRepository.getFollowing(userId).collectLatest { users ->
                    _state.update { it.copy(following = users, isLoading = false) }
                }
            }
        }
    }
}
