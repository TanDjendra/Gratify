package com.tan.gratify.di

import com.tan.gratify.viewModel.AddSongsToPlaylistViewModel
import com.tan.gratify.viewModel.AlbumViewModel
import com.tan.gratify.viewModel.AnalyticsViewModel
import com.tan.gratify.viewModel.ArtistViewModel
import com.tan.gratify.viewModel.HomeViewModel
import com.tan.gratify.viewModel.LibraryDynamicPlaylistViewModel
import com.tan.gratify.viewModel.YouTubeConnectionViewModel
import com.tan.gratify.viewModel.LibraryViewModel
import com.tan.gratify.viewModel.LocalPlaylistViewModel
import com.tan.gratify.viewModel.LogInViewModel
import com.tan.gratify.viewModel.SignUpViewModel
import com.tan.gratify.viewModel.EmailLoginViewModel
import com.tan.gratify.viewModel.CreateProfileViewModel
import com.tan.gratify.viewModel.ForgotPasswordViewModel
import com.tan.gratify.viewModel.MoodViewModel
import com.tan.gratify.viewModel.MoreAlbumsViewModel
import com.tan.gratify.viewModel.NotificationViewModel
import com.tan.gratify.viewModel.NowPlayingBottomSheetViewModel
import com.tan.gratify.viewModel.PlaylistViewModel
import com.tan.gratify.viewModel.PodcastViewModel
import com.tan.gratify.viewModel.RecentlySongsViewModel
import com.tan.gratify.viewModel.SearchViewModel
import com.tan.gratify.viewModel.SettingsViewModel
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UserProfileViewModel
import com.tan.gratify.viewModel.FollowListViewModel
import com.tan.gratify.viewModel.FriendsActivityViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlinx.coroutines.flow.map

val viewModelModule =
    module {
        single<com.tan.gratify.viewModel.PlaylistWorkspaceGateway> { com.tan.gratify.viewModel.SupabasePlaylistWorkspaceGateway(get()) }
        viewModel { com.tan.gratify.viewModel.PlaylistWorkspaceViewModel(get(),get<com.tan.data.sync.AccountSessionCoordinator>().access,get(),get(),get(),get(),get()) }
        single<com.tan.gratify.viewModel.ProfileEditorGateway> {
            com.tan.gratify.viewModel.SupabaseProfileEditorGateway(get(), get(), get())
        }
        viewModel { com.tan.gratify.viewModel.FriendsSocialViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { com.tan.gratify.viewModel.WrappedViewModel(get(), get(), get(), get(),
            get<com.tan.domain.manager.DataStoreManager>().localTrackingEnabled.map { it == com.tan.domain.manager.DataStoreManager.TRUE },
            get<com.tan.data.sync.AccountSessionCoordinator>().access.map { access ->
                when (access) {
                    is com.tan.data.sync.AccountAccess.Ready -> com.tan.gratify.viewModel.WrappedAccess(access.owner)
                    com.tan.data.sync.AccountAccess.Loading -> com.tan.gratify.viewModel.WrappedAccess(ready = false, loading = true)
                    else -> com.tan.gratify.viewModel.WrappedAccess(ready = false)
                }
            }) }
        viewModel { YouTubeConnectionViewModel(get()) }
        single {
            SharedViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single {
            SearchViewModel(
                get(),
                get(),
                get(),
                get(),
                getOrNull(),
                getOrNull<com.tan.data.sync.AccountSessionCoordinator>()?.access?.map { access ->
                    com.tan.gratify.viewModel.SearchAccess((access as? com.tan.data.sync.AccountAccess.Ready)?.owner,
                        access is com.tan.data.sync.AccountAccess.Ready)
                } ?: kotlinx.coroutines.flow.flowOf(com.tan.gratify.viewModel.SearchAccess()),
            )
        }
        viewModel(org.koin.core.qualifier.named("artist-picker")) {
            SearchViewModel(get(), get(), get(), get(), getOrNull(),
                getOrNull<com.tan.data.sync.AccountSessionCoordinator>()?.access?.map { access ->
                    com.tan.gratify.viewModel.SearchAccess((access as? com.tan.data.sync.AccountAccess.Ready)?.owner,
                        access is com.tan.data.sync.AccountAccess.Ready)
                } ?: kotlinx.coroutines.flow.flowOf(com.tan.gratify.viewModel.SearchAccess()))
        }
        viewModel {
            NowPlayingBottomSheetViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryDynamicPlaylistViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AddSongsToPlaylistViewModel(
                get(),
                get(),
                get(),
                get(),
                get<com.tan.data.sync.AccountSessionCoordinator>().access,
            )
        }
        viewModel {
            AlbumViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            HomeViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            SettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ArtistViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            PlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LogInViewModel(
                get(),
            )
        }
        viewModel {
            SignUpViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            CreateProfileViewModel(
                get(),
            )
        }
        viewModel {
            ForgotPasswordViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            EmailLoginViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            PodcastViewModel(
                get(),
            )
        }
        viewModel {
            MoreAlbumsViewModel(
                get(),
            )
        }
        viewModel {
            RecentlySongsViewModel(
                get(),
            )
        }
        viewModel {
            LocalPlaylistViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            NotificationViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            MoodViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AnalyticsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            UserProfileViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            FollowListViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            FriendsActivityViewModel(
                get(),
                get(),
                get(),
            )
        }
    }
