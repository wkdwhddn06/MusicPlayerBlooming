/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.uniqtech.musicplayer

import androidx.preference.PreferenceManager
import androidx.room.Room
import com.uniqtech.musicplayer.activities.tageditor.TagEditorViewModel
import com.uniqtech.musicplayer.androidauto.AutoMusicProvider
import com.uniqtech.musicplayer.database.BoomingDatabase
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import com.uniqtech.musicplayer.fragments.albums.AlbumDetailViewModel
import com.uniqtech.musicplayer.fragments.artists.ArtistDetailViewModel
import com.uniqtech.musicplayer.fragments.equalizer.EqualizerViewModel
import com.uniqtech.musicplayer.fragments.genres.GenreDetailViewModel
import com.uniqtech.musicplayer.fragments.info.InfoViewModel
import com.uniqtech.musicplayer.fragments.lyrics.LyricsViewModel
import com.uniqtech.musicplayer.fragments.playlists.PlaylistDetailViewModel
import com.uniqtech.musicplayer.fragments.search.SearchViewModel
import com.uniqtech.musicplayer.fragments.years.YearDetailViewModel
import com.uniqtech.musicplayer.http.deezer.DeezerService
import com.uniqtech.musicplayer.http.github.GitHubService
import com.uniqtech.musicplayer.http.jsonHttpClient
import com.uniqtech.musicplayer.http.lastfm.LastFmService
import com.uniqtech.musicplayer.http.lyrics.LyricsService
import com.uniqtech.musicplayer.http.provideDefaultCache
import com.uniqtech.musicplayer.http.provideOkHttp
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.providers.MediaStoreWriter
import com.uniqtech.musicplayer.repository.*
import com.uniqtech.musicplayer.service.equalizer.EqualizerManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {
    factory {
        jsonHttpClient(get())
    }
    factory {
        provideDefaultCache()
    }
    factory {
        provideOkHttp(get(), get())
    }
    single {
        GitHubService(get())
    }
    single {
        DeezerService(get())
    }
    single {
        LastFmService(get())
    }
    single {
        LyricsService(androidContext(), get())
    }
}

private val autoModule = module {
    single {
        AutoMusicProvider(androidContext(), get())
    }
}

private val mainModule = module {
    single {
        androidContext().contentResolver
    }
    single {
        EqualizerManager(androidContext())
    }
    single {
        MediaStoreWriter(androidContext(), get())
    }
    single {
        PreferenceManager.getDefaultSharedPreferences(androidContext())
    }
}

private val roomModule = module {
    single {
        Room.databaseBuilder(androidContext(), BoomingDatabase::class.java, "music_database.db")
            .build()
    }

    factory {
        get<BoomingDatabase>().playlistDao()
    }

    factory {
        get<BoomingDatabase>().playCountDao()
    }

    factory {
        get<BoomingDatabase>().historyDao()
    }

    factory {
        get<BoomingDatabase>().inclExclDao()
    }

    factory {
        get<BoomingDatabase>().lyricsDao()
    }
}

private val dataModule = module {
    single {
        RealRepository(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    } bind Repository::class

    single {
        RealSongRepository(get())
    } bind SongRepository::class

    single {
        RealAlbumRepository(get())
    } bind AlbumRepository::class

    single {
        RealArtistRepository(get(), get())
    } bind ArtistRepository::class

    single {
        RealPlaylistRepository(androidContext(), get(), get())
    } bind PlaylistRepository::class

    single {
        RealGenreRepository(get(), get())
    } bind GenreRepository::class

    single {
        RealSearchRepository(get(), get(), get(), get(), get(), get())
    } bind SearchRepository::class

    single {
        RealSmartRepository(androidContext(), get(), get(), get(), get(), get())
    } bind SmartRepository::class

    single {
        RealSpecialRepository(get())
    } bind SpecialRepository::class
}

private val viewModule = module {
    viewModel {
        LibraryViewModel(get(), get())
    }

    viewModel {
        EqualizerViewModel(get(), get(), get())
    }

    viewModel { (albumId: Long) ->
        AlbumDetailViewModel(get(), albumId)
    }

    viewModel { (artistId: Long, artistName: String?) ->
        ArtistDetailViewModel(get(), artistId, artistName)
    }

    viewModel { (playlistId: Long) ->
        PlaylistDetailViewModel(get(), playlistId)
    }

    viewModel { (genre: Genre) ->
        GenreDetailViewModel(get(), genre)
    }

    viewModel { (year: Int) ->
        YearDetailViewModel(get(), year)
    }

    viewModel {
        SearchViewModel(get())
    }

    viewModel { (id: Long, name: String?) ->
        TagEditorViewModel(get(), id, name)
    }

    viewModel {
        LyricsViewModel(get(), get())
    }

    viewModel {
        InfoViewModel(get())
    }
}

val appModules = listOf(networkModule, autoModule, mainModule, roomModule, dataModule, viewModule)