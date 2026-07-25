package com.tan.gratify.di

import com.tan.gratify.viewModel.AddSongsToPlaylistViewModel
import com.tan.gratify.viewModel.AlbumViewModel
import com.tan.gratify.viewModel.AnalyticsViewModel
import com.tan.gratify.viewModel.ArtistViewModel
import com.tan.gratify.viewModel.HomeViewModel
import com.tan.gratify.viewModel.LibraryDynamicPlaylistViewModel
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

val viewModelModule =
    module {
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
            )
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