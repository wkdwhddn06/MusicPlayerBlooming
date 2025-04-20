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

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.database.PlaylistWithSongs
import com.uniqtech.musicplayer.extensions.EXTRA_PLAYLISTS
import com.uniqtech.musicplayer.extensions.extraNotNull
import com.uniqtech.musicplayer.extensions.toHtml
import com.uniqtech.musicplayer.extensions.withArgs
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import com.uniqtech.musicplayer.fragments.ReloadType
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class DeletePlaylistDialog : DialogFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModel()
    private val playlists: List<PlaylistEntity> by extraNotNull(EXTRA_PLAYLISTS)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleRes: Int
        val content: CharSequence
        if (playlists.size > 1) {
            titleRes = R.string.delete_playlists_title
            content = getString(R.string.delete_x_playlists, playlists.size).toHtml()
        } else {
            titleRes = R.string.delete_playlist_title
            content = getString(R.string.delete_playlist_x, playlists[0].playlistName).toHtml()
        }

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(titleRes)
            .setMessage(content)
            .setPositiveButton(R.string.delete_action) { _: DialogInterface, _: Int ->
                libraryViewModel.deleteSongsFromPlaylist(playlists)
                libraryViewModel.deletePlaylists(playlists)
                libraryViewModel.forceReload(ReloadType.Playlists)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun create(playlist: PlaylistWithSongs): DeletePlaylistDialog {
            return create(listOf(playlist))
        }

        fun create(playlists: List<PlaylistWithSongs>): DeletePlaylistDialog {
            val playlistEntities = playlists.map { it.playlistEntity }
            return DeletePlaylistDialog().withArgs {
                putParcelableArrayList(EXTRA_PLAYLISTS, ArrayList(playlistEntities))
            }
        }
    }
}