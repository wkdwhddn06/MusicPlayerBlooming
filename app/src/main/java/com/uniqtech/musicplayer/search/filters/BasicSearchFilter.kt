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

package com.uniqtech.musicplayer.search.filters

import android.os.Parcelable
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.model.ReleaseYear
import com.uniqtech.musicplayer.repository.SearchRepository
import com.uniqtech.musicplayer.search.SearchFilter
import com.uniqtech.musicplayer.search.SearchQuery.FilterMode
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @author Christians M. A. (mardous)
 */
@Parcelize
class BasicSearchFilter<T : Parcelable>(private val name: String, private val argument: T) : SearchFilter,
    KoinComponent {

    @IgnoredOnParcel
    private val searchRepository: SearchRepository by inject()

    override fun getName(): CharSequence {
        return name
    }

    override fun getCompatibleModes(): List<FilterMode> {
        return listOf(FilterMode.Songs)
    }

    override suspend fun getResults(searchMode: FilterMode, query: String): List<Any> {
        return when (argument) {
            is Genre -> searchRepository.searchGenreSongs(argument, query)
            is ReleaseYear -> searchRepository.searchYearSongs(argument, query)
            is PlaylistEntity -> searchRepository.searchPlaylistSongs(argument, query)
            else -> arrayListOf()
        }
    }
}