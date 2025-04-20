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

package com.uniqtech.musicplayer.dialogs.playlists

import android.content.DialogInterface
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.dialogs.InputDialog
import com.uniqtech.musicplayer.extensions.extraNotNull
import com.uniqtech.musicplayer.extensions.withArgs
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import com.uniqtech.musicplayer.fragments.ReloadType
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * @author Christians M. A. (mardous)
 */
class RenamePlaylistDialog : InputDialog() {

    private val libraryViewModel: LibraryViewModel by activityViewModel()
    private val playlistEntity: PlaylistEntity by extraNotNull(EXTRA_PLAYLIST)

    override fun inputConfig(): InputConfig {
        return Builder(requireContext())
            .title(R.string.rename_playlist_title)
            .hint(R.string.playlist_name_empty)
            .prefill(playlistEntity.playlistName)
            .positiveText(R.string.rename_action)
            .createConfig()
    }

    override fun processInput(
        dialog: DialogInterface,
        text: String?,
        isChecked: Boolean
    ): Boolean {
        val playlistName = text?.trim()
        if (!playlistName.isNullOrEmpty()) {
            libraryViewModel.renamePlaylist(playlistEntity.playListId, playlistName)
            libraryViewModel.forceReload(ReloadType.Playlists)
            return true
        } else {
            inputLayout().error = getString(R.string.playlist_name_cannot_be_empty)
            return false
        }
    }

    companion object {
        private const val EXTRA_PLAYLIST = "extra_playlist"

        fun create(playlist: PlaylistEntity) =
            RenamePlaylistDialog().withArgs { putParcelable(EXTRA_PLAYLIST, playlist) }
    }
}