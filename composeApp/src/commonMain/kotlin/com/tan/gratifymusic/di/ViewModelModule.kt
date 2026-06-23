package com.tan.gratifymusic.di

import com.tan.gratifymusic.viewModel.AlbumViewModel
import com.tan.gratifymusic.viewModel.AnalyticsViewModel
import com.tan.gratifymusic.viewModel.ArtistViewModel
import com.tan.gratifymusic.viewModel.HomeViewModel
import com.tan.gratifymusic.viewModel.LibraryDynamicPlaylistViewModel
import com.tan.gratifymusic.viewModel.LibraryViewModel
import com.tan.gratifymusic.viewModel.LocalPlaylistViewModel
import com.tan.gratifymusic.viewModel.LogInViewModel
import com.tan.gratifymusic.viewModel.MoodViewModel
import com.tan.gratifymusic.viewModel.MoreAlbumsViewModel
import com.tan.gratifymusic.viewModel.NotificationViewModel
import com.tan.gratifymusic.viewModel.NowPlayingBottomSheetViewModel
import com.tan.gratifymusic.viewModel.PlaylistViewModel
import com.tan.gratifymusic.viewModel.PodcastViewModel
import com.tan.gratifymusic.viewModel.RecentlySongsViewModel
import com.tan.gratifymusic.viewModel.SearchViewModel
import com.tan.gratifymusic.viewModel.SettingsViewModel
import com.tan.gratifymusic.viewModel.SharedViewModel
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
            )
        }
        single {
            SearchViewModel(
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
            )
        }
        viewModel {
            LibraryDynamicPlaylistViewModel(
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
            )
        }
        viewModel {
            NotificationViewModel(
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
    }