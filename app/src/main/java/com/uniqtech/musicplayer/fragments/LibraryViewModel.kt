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

package com.uniqtech.musicplayer.fragments

import android.animation.ValueAnimator
import android.app.DownloadManager
import android.content.Context
import android.util.Log
import androidx.core.animation.doOnEnd
import androidx.core.content.getSystemService
import androidx.lifecycle.*
import com.uniqtech.musicplayer.database.*
import com.uniqtech.musicplayer.extensions.dp
import com.uniqtech.musicplayer.http.github.GitHubRelease
import com.uniqtech.musicplayer.http.github.GitHubService
import com.uniqtech.musicplayer.model.*
import com.uniqtech.musicplayer.mvvm.AddToPlaylistResult
import com.uniqtech.musicplayer.mvvm.ImportResult
import com.uniqtech.musicplayer.mvvm.ImportablePlaylistResult
import com.uniqtech.musicplayer.mvvm.SuggestedResult
import com.uniqtech.musicplayer.mvvm.UpdateSearchResult
import com.uniqtech.musicplayer.mvvm.event.Event
import com.uniqtech.musicplayer.repository.RealSmartRepository
import com.uniqtech.musicplayer.repository.Repository
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.Preferences
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class LibraryViewModel(
    private val repository: Repository,
    private val updateService: GitHubService
) : ViewModel() {

    init {
        viewModelScope.launch(IO) {
            initializeBlacklist()
            deleteMissingContent()
        }
    }

    private val suggestions = MutableLiveData(SuggestedResult.Idle)
    private val songs = MutableLiveData<List<Song>>()
    private val albums = MutableLiveData<List<Album>>()
    private val artists = MutableLiveData<List<Artist>>()
    private val playlists = MutableLiveData<List<PlaylistWithSongs>>()
    private val genres = MutableLiveData<List<Genre>>()
    private val years = MutableLiveData<List<ReleaseYear>>()
    private val fabMargin = MutableLiveData(0)
    private val songHistory = MutableLiveData<List<Song>>()
    private val paletteColor = MutableLiveData<Int>()
    private val updateSearch = MutableLiveData<Event<UpdateSearchResult>>()

    fun getSuggestions(): LiveData<SuggestedResult> = suggestions
    fun getSongs(): LiveData<List<Song>> = songs
    fun getAlbums(): LiveData<List<Album>> = albums
    fun getArtists(): LiveData<List<Artist>> = artists
    fun getPlaylists(): LiveData<List<PlaylistWithSongs>> = playlists
    fun getGenres(): LiveData<List<Genre>> = genres
    fun getYears(): LiveData<List<ReleaseYear>> = years
    fun getFabMargin(): LiveData<Int> = fabMargin
    fun getPaletteColor(): LiveData<Int> = paletteColor
    fun getUpdateSearchEvent(): LiveData<Event<UpdateSearchResult>> = updateSearch

    fun setFabMargin(context: Context, bottomMargin: Int) {
        val currentValue = 16.dp(context) + bottomMargin
        ValueAnimator.ofInt(getFabMargin().value!!, currentValue).apply {
            addUpdateListener {
                fabMargin.postValue(it.animatedValue as Int)
            }
            doOnEnd {
                fabMargin.postValue(currentValue)
            }
            start()
        }
    }

    fun setPaletteColor(color: Int) {
        paletteColor.value = color
    }

    suspend fun albumById(id: Long) = repository.albumById(id)
    fun artistById(id: Long) = repository.artistById(id)
    suspend fun devicePlaylistById(id: Long) = repository.devicePlaylist(id)
    fun genreBySong(song: Song): LiveData<Genre> = liveData(IO) {
        emit(repository.genreBySong(song))
    }

    fun shuffleAll() = viewModelScope.launch(IO) {
        val allSongs = repository.allSongs()
        withContext(Main) {
            MusicPlayer.openQueueShuffle(allSongs)
        }
    }

    fun forceReload(reloadType: ReloadType) = viewModelScope.launch(IO) {
        when (reloadType) {
            ReloadType.Songs -> fetchSongs()
            ReloadType.Albums -> fetchAlbums()
            ReloadType.Artists -> fetchArtists()
            ReloadType.Playlists -> fetchPlaylists()
            ReloadType.Genres -> fetchGenres()
            ReloadType.Years -> fetchYears()
            ReloadType.Suggestions -> fetchSuggestions()
        }
    }

    private suspend fun fetchSuggestions() {
        val currentValue = suggestions.value?.copy(state = SuggestedResult.State.Loading)
            ?: SuggestedResult(SuggestedResult.State.Loading)
        suggestions.postValue(currentValue)

        val data = repository.homeSuggestions()
        suggestions.postValue(SuggestedResult(SuggestedResult.State.Ready, data))
    }

    private suspend fun fetchSongs() {
        songs.postValue(repository.allSongs())
    }

    private suspend fun fetchAlbums() {
        albums.postValue(repository.allAlbums())
    }

    private suspend fun fetchArtists() {
        if (Preferences.onlyAlbumArtists) {
            artists.postValue(repository.allAlbumArtists())
        } else {
            artists.postValue(repository.allArtists())
        }
    }

    private suspend fun fetchPlaylists() {
        playlists.postValue(repository.playlistsWithSongs())
    }

    private suspend fun fetchGenres() {
        genres.postValue(repository.allGenres())
    }

    private suspend fun fetchYears() {
        years.postValue(repository.allYears())
    }

    fun artists(type: ContentType): LiveData<List<Artist>> = liveData(IO) {
        when (type) {
            ContentType.TopArtists -> emit(repository.topArtists())
            ContentType.RecentArtists -> emit(repository.recentArtists())
            else -> emit(arrayListOf())
        }
    }

    fun albums(type: ContentType): LiveData<List<Album>> = liveData(IO) {
        when (type) {
            ContentType.TopAlbums -> emit(repository.topAlbums())
            ContentType.RecentAlbums -> emit(repository.recentAlbums())
            else -> emit(arrayListOf())
        }
    }

    fun clearHistory() {
        viewModelScope.launch(IO) {
            repository.clearSongHistory()
        }
        songHistory.value = emptyList()
    }

    fun favorites() = repository.favoriteSongsObservable()

    fun lastAddedSongs(): LiveData<List<Song>> = liveData(IO) {
        emit(repository.recentSongs())
    }

    fun topTracks(): LiveData<List<Song>> = liveData(IO) {
        val songs = repository.playCountSongs().filter { song ->
            if (!File(song.data).exists() || song.id == -1L) {
                repository.deleteSongInPlayCount(song)
                false
            } else true
        }.take(RealSmartRepository.NUMBER_OF_TOP_TRACKS).map {
            it.toSong()
        }
        emit(songs)
    }

    fun observableHistorySongs(): LiveData<List<Song>> {
        viewModelScope.launch(IO) {
            val historySongs = repository.historySongs().filter { song ->
                if (!File(song.data).exists() || song.id == -1L) {
                    repository.deleteSongInHistory(song.id)
                    false
                } else true
            }.map {
                it.toSong()
            }
            songHistory.postValue(historySongs)
        }
        return songHistory
    }

    fun notRecentlyPlayedSongs(): LiveData<List<Song>> = liveData(IO) {
        emit(repository.notRecentlyPlayedSongs())
    }

    fun renamePlaylist(playListId: Long, name: String) = viewModelScope.launch(IO) {
        repository.renamePlaylist(playListId, name)
    }

    fun deleteSongsInPlaylist(songs: List<SongEntity>) {
        viewModelScope.launch(IO) {
            repository.deleteSongsInPlaylist(songs)
            forceReload(ReloadType.Playlists)
        }
    }

    fun deleteSongsFromPlaylist(playlists: List<PlaylistEntity>) = viewModelScope.launch(IO) {
        repository.deletePlaylistSongs(playlists)
    }

    fun deletePlaylists(playlists: List<PlaylistEntity>) = viewModelScope.launch(IO) {
        repository.deletePlaylists(playlists)
    }

    fun addToPlaylist(playlistName: String, songs: List<Song>): LiveData<AddToPlaylistResult> =
        liveData(IO) {
            emit(AddToPlaylistResult(playlistName, isWorking = true))

            val playlists = checkPlaylistExists(playlistName)
            if (playlists.isEmpty()) {
                val playlistId: Long = createPlaylist(PlaylistEntity(playlistName = playlistName))
                insertSongs(songs.map { it.toSongEntity(playlistId) })
                val playlistCreated = (playlistId != -1L)
                emit(AddToPlaylistResult(playlistName, playlistCreated = playlistCreated, insertedSongs = songs.size))
            } else {
                val playlist = playlists.firstOrNull()
                if (playlist != null) {
                    val checkedSongs = songs.filterNot { checkSongExistInPlaylist(playlist, it) }
                    insertSongs(checkedSongs.map {
                        it.toSongEntity(playListId = playlist.playListId)
                    })
                    emit(AddToPlaylistResult(playlistName, insertedSongs = checkedSongs.size))
                } else {
                    emit(AddToPlaylistResult(playlistName))
                }
            }
            forceReload(ReloadType.Playlists)
        }

    fun playlistsAsync(): LiveData<List<PlaylistWithSongs>> = liveData(IO) {
        emit(repository.playlistsWithSongs())
    }

    fun favoritePlaylistAsync(): LiveData<PlaylistEntity> = liveData(IO) {
        emit(repository.favoritePlaylist())
    }

    suspend fun favoritePlaylist() = repository.favoritePlaylist()
    suspend fun checkSongExistInPlaylist(playlistEntity: PlaylistEntity, song: Song) =
        repository.checkSongExistInPlaylist(playlistEntity, song)

    suspend fun isSongFavorite(songId: Long) = repository.isSongFavorite(songId)
    suspend fun insertSongs(songs: List<SongEntity>) = repository.insertSongsInPlaylist(songs)
    suspend fun removeSongFromPlaylist(songEntity: SongEntity) =
        repository.removeSongFromPlaylist(songEntity)

    private suspend fun checkPlaylistExists(playlistName: String): List<PlaylistEntity> =
        repository.checkPlaylistExists(playlistName)

    private suspend fun createPlaylist(playlistEntity: PlaylistEntity): Long =
        repository.createPlaylist(playlistEntity)

    private suspend fun deleteMissingContent() {
        repository.deleteMissingContent()
    }

    fun getDevicePlaylists(): LiveData<List<ImportablePlaylistResult>> = liveData(IO) {
        val devicePlaylists = repository.devicePlaylists()
        val importablePlaylists = devicePlaylists.map {
            ImportablePlaylistResult(it.name, it.getSongs())
        }.filter {
            it.songs.isNotEmpty()
        }
        emit(importablePlaylists)
    }

    fun importPlaylist(context: Context, playlist: ImportablePlaylistResult): LiveData<ImportResult> = liveData(IO) {
        var count = 1
        var playlistName = playlist.playlistName
        while (repository.checkPlaylistExists(playlistName).isNotEmpty() && count <= 100) {
            playlistName = "${playlist.playlistName} $count"
            count++
        }
        if (repository.checkPlaylistExists(playlistName).isEmpty()) {
            val id = repository.createPlaylist(PlaylistEntity(playlistName = playlistName))
            if (id != -1L) {
                repository.insertSongsInPlaylist(playlist.songs.toSongsEntity(id))
                emit(ImportResult.success(context, playlist))
                forceReload(ReloadType.Playlists)
            } else {
                emit(ImportResult.error(context, playlist))
            }
        } else {
            emit(ImportResult.error(context, playlist))
        }
    }

    private suspend fun initializeBlacklist() {
        if (!Preferences.initializedBlacklist) {
            repository.initializeBlacklist()
            Preferences.initializedBlacklist = true
        }
    }

    fun searchForUpdate(fromUser: Boolean, stableUpdate: Boolean = !Preferences.experimentalUpdates) =
        viewModelScope.launch(IO) {
            val current = updateSearch.value?.peekContent() ?: UpdateSearchResult(executedAtMillis = Preferences.lastUpdateSearch)
            if (current.shouldStartNewSearchFor(fromUser, stableUpdate)) {
                updateSearch.postValue(
                    Event(
                        current.copy(
                            state = UpdateSearchResult.State.Searching,
                            wasFromUser = fromUser,
                            wasStableQuery = stableUpdate
                        )
                    )
                )

                val result = runCatching {
                    updateService.latestRelease(isStable = stableUpdate)
                }
                val executedAtMillis = Date().time
                val newState = if (result.isSuccess) {
                    UpdateSearchResult(
                        state = UpdateSearchResult.State.Completed,
                        data = result.getOrThrow(),
                        executedAtMillis = executedAtMillis,
                        wasFromUser = fromUser,
                        wasStableQuery = stableUpdate
                    )
                } else {
                    UpdateSearchResult(
                        state = UpdateSearchResult.State.Failed,
                        data = null,
                        executedAtMillis = executedAtMillis,
                        wasFromUser = fromUser,
                        wasStableQuery = stableUpdate
                    )
                }
                updateSearch.postValue(Event(newState))
            }
        }

    fun downloadUpdate(context: Context, release: GitHubRelease) =
        viewModelScope.launch(IO + ioHandler) {
            val downloadRequest = release.getDownloadRequest(context)
            if (downloadRequest != null) {
                val downloadManager = context.getSystemService<DownloadManager>()
                if (downloadManager != null) {
                    val lastUpdateId = Preferences.lastUpdateId
                    if (lastUpdateId != -1L) {
                        downloadManager.remove(lastUpdateId)
                    }
                    Preferences.lastUpdateId = downloadManager.enqueue(downloadRequest)
                }
            }
        }

    private val ioHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("Coroutines", "An unexpected error occurred", throwable)
    }
}

enum class ReloadType {
    Songs,
    Albums,
    Artists,
    Playlists,
    Genres,
    Years,
    Suggestions
}
