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

package com.uniqtech.musicplayer.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.EXTRA_SONG
import com.uniqtech.musicplayer.extensions.media.searchQuery
import com.uniqtech.musicplayer.extensions.openWeb
import com.uniqtech.musicplayer.extensions.toChooser
import com.uniqtech.musicplayer.extensions.withArgs
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.model.WebSearchEngine

class WebSearchDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val song = BundleCompat.getParcelable(requireArguments(), EXTRA_SONG, Song::class.java)!!

        val engines = WebSearchEngine.entries.toTypedArray()
        val titles = engines.map { getString(it.nameRes) }.toTypedArray()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.where_do_you_want_to_search)
            .setNegativeButton(android.R.string.cancel, null)
            .setItems(titles) { _: DialogInterface, position: Int ->
                startActivity(
                    song.searchQuery(engines[position]).openWeb().toChooser(getString(R.string.web_search))
                )
            }
            .create()
    }

    companion object {
        fun create(song: Song) = WebSearchDialog().withArgs {
            putParcelable(EXTRA_SONG, song)
        }
    }
}