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

package com.uniqtech.musicplayer.repository

import android.content.Context
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.database.toSongs
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.model.ReleaseYear
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.search.SearchFilter
import com.uniqtech.musicplayer.search.SearchQuery
import com.uniqtech.musicplayer.util.Preferences

interface SearchRepository {
    suspend fun searchAll(context: Context, query: SearchQuery, filter: SearchFilter?): List<Any>
    suspend fun searchGenreSongs(genre: Genre, query: String): List<Song>
    suspend fun searchPlaylistSongs(playlist: PlaylistEntity, query: String): List<Song>
    suspend fun searchYearSongs(year: ReleaseYear, query: String): List<Song>
}

class RealSearchRepository(
    private val albumRepository: RealAlbumRepository,
    private val songRepository: RealSongRepository,
    private val artistRepository: RealArtistRepository,
    private val playlistRepository: RealPlaylistRepository,
    private val genreRepository: GenreRepository,
    private val specialRepository: SpecialRepository
) : SearchRepository {

    override suspend fun searchAll(context: Context, query: SearchQuery, filter: SearchFilter?): List<Any> {
        val results = ArrayList<Any>()
        if (!query.searched.isNullOrEmpty()) {
            if (filter != null) {
                if (query.filterMode != null) {
                    val filteredResults = filter.getResults(query.filterMode, query.searched)
                    if (filteredResults.isNotEmpty()) {
                        results.addAll(filteredResults)
                    }
                }
                // we do nothing if there is a filter but search mode is not valid
            } else {
                val isOnlyAlbumArtists = Preferences.onlyAlbumArtists
                when (query.filterMode) {
                    SearchQuery.FilterMode.Songs -> results.addAll(getSongs(query.searched))
                    SearchQuery.FilterMode.Albums -> results.addAll(getAlbums(query.searched))
                    SearchQuery.FilterMode.Artists -> results.addAll(getArtists(query.searched, isOnlyAlbumArtists))
                    SearchQuery.FilterMode.Genres -> results.addAll(getGenres(query.searched))
                    SearchQuery.FilterMode.Playlists -> results.addAll(getPlaylists(query.searched))
                    else -> {
                        results.addTitled(getSongs(query.searched), context.getString(R.string.songs_label))
                        results.addTitled(
                            getArtists(query.searched, isOnlyAlbumArtists),
                            if (isOnlyAlbumArtists)
                                context.getString(R.string.album_artists_label)
                            else context.getString(R.string.artists_label)
                        )
                        results.addTitled(getAlbums(query.searched), context.getString(R.string.albums_label))
                        results.addTitled(getGenres(query.searched), context.getString(R.string.genres_label))
                        results.addTitled(getPlaylists(query.searched), context.getString(R.string.playlists_label))
                    }
                }
            }
        }
        return results
    }

    override suspend fun searchGenreSongs(genre: Genre, query: String): List<Song> =
        genreRepository.songs(genre.id, query)

    override suspend fun searchPlaylistSongs(playlist: PlaylistEntity, query: String): List<Song> =
        playlistRepository.searchPlaylistSongs(playlist.playListId, query).toSongs()

    override suspend fun searchYearSongs(year: ReleaseYear, query: String): List<Song> =
        specialRepository.songs(year.year, query)

    private fun getSongs(query: String) = songRepository.songs(query)
    private fun getAlbums(query: String) = albumRepository.albums(query)
    private fun getArtists(query: String, isOnlyAlbumArtists: Boolean) =
        if (isOnlyAlbumArtists)
            artistRepository.albumArtists(query)
        else artistRepository.artists(query)

    private suspend fun getGenres(query: String) = genreRepository.genres(query)
    private suspend fun getPlaylists(query: String) = playlistRepository.searchPlaylists(query)
}

internal fun MutableList<Any>.addTitled(results: List<Any>, header: String) {
    if (results.isNotEmpty()) {
        this.add(header)
        this.addAll(results)
    }
}