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

package com.uniqtech.musicplayer.interfaces

import android.view.MenuItem
import android.view.View
import com.uniqtech.musicplayer.database.PlaylistWithSongs
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.model.Song

interface ISearchCallback {
    fun songMenuItemClick(song: Song, menuItem: MenuItem): Boolean
    fun albumClick(album: Album, sharedElements: Array<Pair<View, String>>)
    fun albumMenuItemClick(album: Album, menuItem: MenuItem, sharedElements: Array<Pair<View, String>>): Boolean
    fun artistClick(artist: Artist, sharedElements: Array<Pair<View, String>>)
    fun artistMenuItemClick(artist: Artist, menuItem: MenuItem, sharedElements: Array<Pair<View, String>>): Boolean
    fun playlistClick(playlist: PlaylistWithSongs)
    fun playlistMenuItemClick(playlist: PlaylistWithSongs, menuItem: MenuItem): Boolean
    fun genreClick(genre: Genre)
}