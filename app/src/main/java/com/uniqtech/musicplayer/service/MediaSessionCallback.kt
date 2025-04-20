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

package com.uniqtech.musicplayer.service

import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.uniqtech.musicplayer.androidauto.AutoMediaIDHelper
import com.uniqtech.musicplayer.database.fromHistoryToSongs
import com.uniqtech.musicplayer.extensions.media.indexOfSong
import com.uniqtech.musicplayer.extensions.utilities.toMutableListIfRequired
import com.uniqtech.musicplayer.helper.ShuffleHelper
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.providers.databases.PlaybackQueueStore
import com.uniqtech.musicplayer.repository.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MediaSessionCallback(private val musicService: MusicService, private val coroutineScope: CoroutineScope) :
    MediaSessionCompat.Callback(), KoinComponent {

    private val repository by inject<Repository>()

    override fun onPrepare() {
        super.onPrepare()
        if (musicService.getCurrentSong() != Song.emptySong) {
            coroutineScope.launch(IO) {
                musicService.restoreState()
            }
        }
    }

    override fun onPlay() {
        super.onPlay()
        if (musicService.getCurrentSong() != Song.emptySong) {
            musicService.play()
        }
    }

    override fun onPause() {
        super.onPause()
        musicService.pause()
    }

    override fun onSkipToNext() {
        super.onSkipToNext()
        musicService.playNextSong(true)
    }

    override fun onSkipToPrevious() {
        super.onSkipToPrevious()
        musicService.playPreviousSong(true)
    }

    override fun onStop() {
        musicService.quit()
    }

    override fun onSeekTo(pos: Long) {
        musicService.seek(pos.toInt())
    }

    override fun onCustomAction(action: String, extras: Bundle) {
        when (action) {
            MusicService.CYCLE_REPEAT -> {
                musicService.cycleRepeatMode()
                musicService.updateMediaSessionPlaybackState()
            }

            MusicService.TOGGLE_SHUFFLE -> {
                musicService.toggleShuffle()
                musicService.updateMediaSessionPlaybackState()
            }

            MusicService.TOGGLE_FAVORITE -> {
                musicService.toggleFavorite()
            }

            else -> Log.d("MediaSession", "Unsupported action: $action")
        }
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
        super.onPlayFromMediaId(mediaId, extras)

        val musicId = AutoMediaIDHelper.extractMusicID(mediaId)
        val itemId = musicId?.toLong() ?: -1

        val songs = ArrayList<Song>()

        coroutineScope.launch(IO) {
            when (val category = AutoMediaIDHelper.extractCategory(mediaId)) {
                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM -> {
                    val album = repository.albumById(itemId)
                    songs.addAll(album.songs)
                    musicService.openQueue(songs, 0, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST -> {
                    val artist = repository.artistById(itemId)
                    songs.addAll(artist.songs)
                    musicService.openQueue(songs, 0, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST -> {
                    val playlist = repository.devicePlaylist(itemId)
                    songs.addAll(playlist.getSongs())
                    musicService.openQueue(songs, 0, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY,
                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS,
                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_QUEUE -> {
                    val tracks: List<Song> = when (category) {
                        AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY -> {
                            repository.historySongs().fromHistoryToSongs()
                        }

                        AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS -> {
                            repository.topPlayedSongs()
                        }

                        else -> {
                            PlaybackQueueStore.getInstance(musicService).savedOriginalPlayingQueue
                        }
                    }
                    songs.addAll(tracks)
                    var songIndex = tracks.indexOfSong(itemId)
                    if (songIndex == -1) {
                        songIndex = 0
                    }
                    musicService.openQueue(songs, songIndex, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SHUFFLE -> {
                    val allSongs = repository.allSongs().toMutableListIfRequired()
                    ShuffleHelper.makeShuffleList(allSongs, -1)
                    musicService.openQueue(allSongs, 0, true)
                }
            }
            musicService.play()
        }
    }

    /**
     * Inspired by https://developer.android.com/guide/topics/media-apps/interacting-with-assistant
     */
    override fun onPlayFromSearch(query: String, extras: Bundle) {
        coroutineScope.launch(IO) {
            val songs = ArrayList<Song>()
            if (query.isEmpty()) {
                songs.addAll(repository.allSongs())
            } else {
                // Build a queue based on songs that match "query" or "extras" param
                val mediaFocus = extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)
                if (mediaFocus.equals(MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)) {
                    val artistQuery = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                    if (artistQuery != null) {
                        val artists = repository.searchArtists(artistQuery)
                        if (artists.isNotEmpty()) {
                            songs.addAll(artists.first().songs)
                        }
                    }
                } else if (mediaFocus.equals(MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)) {
                    val albumQuery = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                    if (albumQuery != null) {
                        val albums = repository.searchAlbums(albumQuery)
                        if (albums.isNotEmpty()) {
                            songs.addAll(albums.first().songs)
                        }
                    }
                }
            }

            // Search by title
            if (songs.isEmpty()) {
                songs.addAll(repository.searchSongs(query))
            }

            musicService.openQueue(songs, 0, true)
            musicService.play()
        }
    }
}