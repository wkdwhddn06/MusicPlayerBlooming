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

package com.uniqtech.musicplayer.helper.menu

import android.content.Intent
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.activities.tageditor.AbsTagEditorActivity
import com.uniqtech.musicplayer.activities.tageditor.AlbumTagEditorActivity
import com.uniqtech.musicplayer.activities.tageditor.ArtistTagEditorActivity
import com.uniqtech.musicplayer.activities.tageditor.SongTagEditorActivity
import com.uniqtech.musicplayer.database.PlaylistWithSongs
import com.uniqtech.musicplayer.database.toSongs
import com.uniqtech.musicplayer.dialogs.playlists.AddToPlaylistDialog
import com.uniqtech.musicplayer.dialogs.playlists.DeletePlaylistDialog
import com.uniqtech.musicplayer.dialogs.playlists.RenamePlaylistDialog
import com.uniqtech.musicplayer.dialogs.songs.DeleteSongsDialog
import com.uniqtech.musicplayer.dialogs.songs.SetRingtoneDialog
import com.uniqtech.musicplayer.extensions.getShareSongIntent
import com.uniqtech.musicplayer.extensions.getShareSongsIntent
import com.uniqtech.musicplayer.extensions.navigation.*
import com.uniqtech.musicplayer.extensions.toChooser
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import com.uniqtech.musicplayer.helper.m3u.M3UWriter
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel

fun Song.onSongMenu(
    fragment: Fragment,
    menuItem: MenuItem,
    sharedElements: Array<Pair<View, String>>? = null
): Boolean {
    if (id == -1L) {
        return false
    }
    return when (menuItem.itemId) {
        R.id.action_play_next -> {
            MusicPlayer.playNext(this)
            true
        }

        R.id.action_add_to_playing_queue -> {
            MusicPlayer.enqueue(this)
            true
        }

        R.id.action_add_to_playlist -> {
            AddToPlaylistDialog.create(this)
                .show(fragment.childFragmentManager, "ADD_PLAYLIST")
            true
        }

        R.id.action_go_to_album -> {
            val navController = fragment.findActivityNavController(R.id.fragment_container)
            navController.navigate(R.id.nav_album_detail, albumDetailArgs(this.albumId))
            true
        }

        R.id.action_go_to_artist -> {
            val navController = fragment.findActivityNavController(R.id.fragment_container)
            navController.navigate(R.id.nav_artist_detail, artistDetailArgs(this))
            true
        }

        R.id.action_go_to_genre -> {
            val libraryViewModel = fragment.activityViewModel<LibraryViewModel>().value
            libraryViewModel.genreBySong(this).observe(fragment.viewLifecycleOwner) {
                val navController = fragment.findActivityNavController(R.id.fragment_container)
                navController.navigate(R.id.nav_genre_detail, genreDetailArgs(it))
            }
            true
        }

        R.id.action_set_as_ringtone -> {
            SetRingtoneDialog.create(this).show(fragment.childFragmentManager, "SET_RINGTONE")
            true
        }

        R.id.action_share -> {
            fragment.startActivity(
                fragment.requireContext()
                    .getShareSongIntent(this)
                    .toChooser(fragment.getString(R.string.action_share))
            )
            true
        }

        R.id.action_details -> {
            fragment.findActivityNavController(R.id.fragment_container)
                .navigate(R.id.nav_song_details, songDetailArgs(this))
            true
        }

        R.id.action_tag_editor -> {
            val tagEditorIntent =
                Intent(fragment.requireContext(), SongTagEditorActivity::class.java)
            tagEditorIntent.putExtra(AbsTagEditorActivity.EXTRA_ID, this.id)
            fragment.startActivity(tagEditorIntent)
            true
        }

        R.id.action_delete_from_device -> {
            DeleteSongsDialog.create(this).show(fragment.childFragmentManager, "DELETE_SONGS")
            true
        }

        else -> false
    }
}

fun List<Song>.onSongsMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    if (isEmpty()) {
        return false
    }
    return when (menuItem.itemId) {
        R.id.action_shuffle_all -> {
            MusicPlayer.openQueueShuffle(this, true)
            true
        }

        R.id.action_play_next -> {
            MusicPlayer.playNext(this)
            true
        }

        R.id.action_add_to_playing_queue -> {
            MusicPlayer.enqueue(this)
            true
        }

        R.id.action_add_to_playlist -> {
            AddToPlaylistDialog.create(this)
                .show(fragment.childFragmentManager, "ADD_PLAYLIST")
            true
        }

        R.id.action_share -> {
            fragment.startActivity(
                fragment.requireContext()
                    .getShareSongsIntent(this)
                    .toChooser(fragment.getString(R.string.action_share))
            )
            true
        }

        R.id.action_delete_from_device -> {
            DeleteSongsDialog.create(this).show(fragment.childFragmentManager, "DELETE_SONGS")
            true
        }

        else -> false
    }
}

fun Album.onAlbumMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    return when (menuItem.itemId) {
        R.id.action_go_to_artist -> {
            fragment.findActivityNavController(R.id.fragment_container)
                .navigate(R.id.nav_artist_detail, artistDetailArgs(this))
            true
        }

        R.id.action_tag_editor -> {
            val tagEditorIntent =
                Intent(fragment.requireContext(), AlbumTagEditorActivity::class.java)
            tagEditorIntent.putExtra(AbsTagEditorActivity.EXTRA_ID, this.id)
            fragment.startActivity(tagEditorIntent)
            true
        }

        else -> songs.onSongsMenu(fragment, menuItem)
    }
}

fun List<Album>.onAlbumsMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    fragment.lifecycleScope.launch(Dispatchers.IO) {
        val songs = flatMap { it.songs }
        withContext(Dispatchers.Main) {
            songs.onSongsMenu(fragment, menuItem)
        }
    }
    return true
}

fun Artist.onArtistMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    return when (menuItem.itemId) {
        R.id.action_tag_editor -> {
            val tagEditorIntent =
                Intent(fragment.requireContext(), ArtistTagEditorActivity::class.java)
            if (this.isAlbumArtist)
                tagEditorIntent.putExtra(AbsTagEditorActivity.EXTRA_NAME, this.name)
            else tagEditorIntent.putExtra(AbsTagEditorActivity.EXTRA_ID, this.id)
            fragment.startActivity(tagEditorIntent)
            true
        }

        else -> songs.onSongsMenu(fragment, menuItem)
    }
}

fun List<Artist>.onArtistsMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    fragment.lifecycleScope.launch(Dispatchers.IO) {
        val songs = flatMap { it.songs }
        withContext(Dispatchers.Main) {
            songs.onSongsMenu(fragment, menuItem)
        }
    }
    return true
}

fun PlaylistWithSongs.onPlaylistMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    if (this == PlaylistWithSongs.Empty)
        return false

    when (menuItem.itemId) {
        R.id.action_rename_playlist -> {
            RenamePlaylistDialog.create(playlistEntity)
                .show(fragment.childFragmentManager, "RENAME_PLAYLIST")
            return true
        }

        R.id.action_delete_playlist -> {
            DeletePlaylistDialog.create(this).show(fragment.childFragmentManager, "DELETE_PLAYLIST")
            return true
        }

        R.id.action_export_playlist -> {
            fragment.lifecycleScope.launch {
                M3UWriter.export(fragment.requireContext(), this@onPlaylistMenu)
            }
            return true
        }
    }
    return songs.toSongs().onSongsMenu(fragment, menuItem)
}

fun List<PlaylistWithSongs>.onPlaylistsMenu(fragment: Fragment, menuItem: MenuItem): Boolean {
    when (menuItem.itemId) {
        R.id.action_delete_playlist -> {
            DeletePlaylistDialog.create(this)
                .show(fragment.childFragmentManager, "DELETE_PLAYLISTS")
            return true
        }

        R.id.action_export_playlist -> {
            if (this.size == 1) {
                fragment.lifecycleScope.launch {
                    M3UWriter.export(fragment.requireContext(), first())
                }
            } else {
                fragment.lifecycleScope.launch {
                    M3UWriter.export(fragment.requireContext(), this@onPlaylistsMenu)
                }
            }
            return true
        }
    }
    return flatMap { it.songs.toSongs() }.onSongsMenu(fragment, menuItem)
}

