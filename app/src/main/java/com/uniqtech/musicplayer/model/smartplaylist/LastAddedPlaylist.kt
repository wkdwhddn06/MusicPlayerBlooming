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

package com.uniqtech.musicplayer.model.smartplaylist

import android.content.Context
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.appContext
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.repository.SmartRepository
import com.uniqtech.musicplayer.util.Preferences
import kotlinx.parcelize.Parcelize
import org.koin.core.component.get

@Parcelize
class LastAddedPlaylist : AbsSmartPlaylist(
    appContext().getString(R.string.last_added_label),
    R.drawable.ic_library_add_24dp
) {
    override fun description(context: Context): String {
        // we must pass our activity context here in order to get
        // a string located for the current LocaleList passed to our
        // AppCompatDelegate.
        return buildInfoString(Preferences.getLastAddedCutoff(context).description, super.description(context))
    }

    override fun getSongs(): List<Song> {
        val smartRepository = get<SmartRepository>()
        return smartRepository.recentSongs()
    }
}