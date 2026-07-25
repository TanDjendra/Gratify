package com.tan.data.di

import com.tan.common.Config.SERVICE_SCOPE
import com.tan.data.io.fileDir
import com.tan.data.repository.AccountRepositoryImpl
import com.tan.data.repository.AlbumRepositoryImpl
import com.tan.data.repository.AnalyticsRepositoryImpl
import com.tan.data.repository.ArtistRepositoryImpl
import com.tan.data.repository.CommonRepositoryImpl
import com.tan.data.repository.HomeRepositoryImpl
import com.tan.data.repository.LocalPlaylistRepositoryImpl
import com.tan.data.repository.LyricsCanvasRepositoryImpl
import com.tan.data.repository.PlaylistRepositoryImpl
import com.tan.data.repository.PodcastRepositoryImpl
import com.tan.data.repository.SearchRepositoryImpl
import com.tan.data.repository.SongRepositoryImpl
import com.tan.data.repository.StreamRepositoryImpl
import com.tan.data.repository.UpdateRepositoryImpl
import com.tan.data.repository.SharedPlaylistRepositoryImpl
import com.tan.data.repository.UserDataSyncRepositoryImpl
import com.tan.domain.repository.AccountRepository
import com.tan.domain.repository.AlbumRepository
import com.tan.domain.repository.AnalyticsRepository
import com.tan.domain.repository.ArtistRepository
import com.tan.domain.repository.CommonRepository
import com.tan.domain.repository.HomeRepository
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.LyricsCanvasRepository
import com.tan.domain.repository.PlaylistRepository
import com.tan.domain.repository.PodcastRepository
import com.tan.domain.repository.SearchRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.repository.StreamRepository
import com.tan.domain.repository.UpdateRepository
import com.tan.domain.repository.SharedPlaylistRepository
import com.tan.domain.repository.UserDataSyncRepository
import com.tan.domain.repository.UserRepository
import com.tan.data.repository.UserRepositoryImpl
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule =
    module {
        single<AccountRepository>(createdAtStart = true) {
            AccountRepositoryImpl(get(), get())
        }

        single<AlbumRepository>(createdAtStart = true) {
            AlbumRepositoryImpl(get(), get())
        }

        single<ArtistRepository>(createdAtStart = true) {
            ArtistRepositoryImpl(get(), get())
        }

        single<CommonRepository>(createdAtStart = true) {
            CommonRepositoryImpl(get(named(SERVICE_SCOPE)), get(), get(), get(), get(), get()).apply {
                this.init("${fileDir()}/ytdlp-cookie.txt", get())
            }
        }

        single<HomeRepository>(createdAtStart = true) {
            HomeRepositoryImpl(get(), get())
        }

        single<LocalPlaylistRepository>(createdAtStart = true) {
            LocalPlaylistRepositoryImpl(get(), get())
        }

        single<LyricsCanvasRepository>(createdAtStart = true) {
            LyricsCanvasRepositoryImpl(get(), get(), get(), get(), get())
        }

        single<PlaylistRepository>(createdAtStart = true) {
            PlaylistRepositoryImpl(get(), get(), get())
        }

        single<PodcastRepository>(createdAtStart = true) {
            PodcastRepositoryImpl(get(), get())
        }

        single<SearchRepository>(createdAtStart = true) {
            SearchRepositoryImpl(get(), get())
        }

        single<SongRepository>(createdAtStart = true) {
            SongRepositoryImpl(get(), get(), get())
        }

        single<StreamRepository>(createdAtStart = true) {
            StreamRepositoryImpl(get(), get())
        }

        single<UpdateRepository>(createdAtStart = true) {
            UpdateRepositoryImpl(get())
        }

        single<AnalyticsRepository>(createdAtStart = true) {
            AnalyticsRepositoryImpl(get())
        }
        
        single<SharedPlaylistRepository>(createdAtStart = true) {
            SharedPlaylistRepositoryImpl(get())
        }
        
        single<UserRepository>(createdAtStart = true) {
            UserRepositoryImpl(get())
        }

        single<com.tan.domain.repository.SocialRepository>(createdAtStart = true) {
            com.tan.data.repository.SocialRepositoryImpl(get(), get())
        }

        single<UserDataSyncRepository>(createdAtStart = true) {
            UserDataSyncRepositoryImpl(get(), get(), get())
        }
    }