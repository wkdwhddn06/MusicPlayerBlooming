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

package com.uniqtech.musicplayer.search

import android.content.Context
import android.os.Parcelable
import android.provider.MediaStore.Audio.AudioColumns
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.extensions.media.displayName
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.model.ReleaseYear
import com.uniqtech.musicplayer.search.SearchQuery.FilterMode
import com.uniqtech.musicplayer.search.filters.BasicSearchFilter
import com.uniqtech.musicplayer.search.filters.LastAddedSearchFilter
import com.uniqtech.musicplayer.search.filters.SmartSearchFilter
import kotlinx.parcelize.Parcelize

/**
 * @author Christians M. A. (mardous)
 */
interface SearchFilter : Parcelable {
    fun getName(): CharSequence

    fun getCompatibleModes(): List<FilterMode>

    suspend fun getResults(searchMode: FilterMode, query: String): List<Any>
}

@Parcelize
class FilterSelection(
    /**
     * What mode this filter work with
     */
    internal val mode: FilterMode,
    /**
     * What column this filter search for
     */
    internal val column: String,
    /**
     * How this filter will select what it's searching.
     */
    internal val selection: String,
    /**
     * What arguments this filter will pass to the repository.
     */
    internal vararg val arguments: String
) : Parcelable

/**
 * Return a [SearchFilter] that may be used to search songs from this album.
 */
fun Album.searchFilter(context: Context) =
    SmartSearchFilter(
        context.getString(R.string.search_album_x_label, name), null,
        FilterSelection(FilterMode.Songs, AudioColumns.TITLE, AudioColumns.ALBUM_ID + "=?", id.toString())
    )

/**
 * Return a [SearchFilter] that may be used to search songs and albums from this artist.
 */
fun Artist.searchFilter(context: Context): SmartSearchFilter {
    return if (isAlbumArtist) {
        SmartSearchFilter(
            context.getString(R.string.search_artist_x_label, displayName()), null,
            FilterSelection(FilterMode.Songs, AudioColumns.TITLE, "${AudioColumns.ALBUM_ARTIST}=?", name),
            FilterSelection(FilterMode.Albums, AudioColumns.ALBUM, "${AudioColumns.ALBUM_ARTIST}=?", name)
        )
    } else {
        SmartSearchFilter(
            context.getString(R.string.search_artist_x_label, displayName()), null,
            FilterSelection(FilterMode.Songs, AudioColumns.TITLE, "${AudioColumns.ARTIST_ID}=?", id.toString()),
            FilterSelection(FilterMode.Albums, AudioColumns.ALBUM, "${AudioColumns.ARTIST_ID}=?", id.toString())
        )
    }
}

fun Genre.searchFilter(context: Context): SearchFilter =
    BasicSearchFilter(context.getString(R.string.search_from_x_label, name), this)

fun ReleaseYear.searchFilter(context: Context): SearchFilter =
    BasicSearchFilter(context.getString(R.string.search_year_x_label, name), this)

fun PlaylistEntity.searchFilter(context: Context): SearchFilter =
    BasicSearchFilter(context.getString(R.string.search_list_x_label, playlistName), this)

fun lastAddedSearchFilter(context: Context): SearchFilter =
    LastAddedSearchFilter(context.getString(R.string.search_last_added))