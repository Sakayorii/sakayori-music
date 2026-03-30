package com.sakayori.music.di

import com.sakayori.music.viewModel.AlbumViewModel
import com.sakayori.music.viewModel.AnalyticsViewModel
import com.sakayori.music.viewModel.ArtistViewModel
import com.sakayori.music.viewModel.HomeViewModel
import com.sakayori.music.viewModel.LibraryDynamicPlaylistViewModel
import com.sakayori.music.viewModel.LibraryViewModel
import com.sakayori.music.viewModel.LocalPlaylistViewModel
import com.sakayori.music.viewModel.LogInViewModel
import com.sakayori.music.viewModel.MoodViewModel
import com.sakayori.music.viewModel.MoreAlbumsViewModel
import com.sakayori.music.viewModel.NotificationViewModel
import com.sakayori.music.viewModel.NowPlayingBottomSheetViewModel
import com.sakayori.music.viewModel.PlaylistViewModel
import com.sakayori.music.viewModel.PodcastViewModel
import com.sakayori.music.viewModel.RecentlySongsViewModel
import com.sakayori.music.viewModel.SearchViewModel
import com.sakayori.music.viewModel.SettingsViewModel
import com.sakayori.music.viewModel.SharedViewModel
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
