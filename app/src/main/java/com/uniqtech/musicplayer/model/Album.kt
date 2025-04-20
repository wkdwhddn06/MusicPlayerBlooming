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
package com.uniqtech.musicplayer.model

import java.util.Objects

data class Album(val id: Long, val songs: List<Song>) {

    val name: String
        get() = safeGetFirstSong().albumName

    val artistId: Long
        get() = safeGetFirstSong().artistId

    val artistName: String
        get() = safeGetFirstSong().artistName

    val albumArtistName: String?
        get() = safeGetFirstSong().albumArtistName

    val year: Int
        get() = safeGetFirstSong().year

    val songCount: Int
        get() = songs.size

    val duration: Long
        get() = songs.sumOf { it.duration }

    fun safeGetFirstSong(): Song {
        return songs.firstOrNull() ?: Song.emptySong
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val album = other as Album
        return id == album.id && songs == album.songs
    }

    override fun hashCode(): Int {
        return Objects.hash(id, songs)
    }

    override fun toString(): String {
        return "Album{id=$id, songs=$songs}"
    }

    companion object {
        val empty = Album(-1, arrayListOf())
    }
}