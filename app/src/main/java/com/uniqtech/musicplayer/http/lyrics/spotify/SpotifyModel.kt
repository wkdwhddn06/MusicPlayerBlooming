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

package com.uniqtech.musicplayer.http.lyrics.spotify

import kotlinx.serialization.Serializable

@Serializable
class SpotifyTokenResponse(
    val accessToken: String,
    val accessTokenExpirationTimestampMs: Long,
)

@Serializable
class TrackSearchResult(val tracks: Tracks) {
    @Serializable
    class Tracks(val items: List<Track>) {
        @Serializable
        class Track(val name: String, val id: String)
    }
}

@Serializable
class SyncedLinesResponse(val error: Boolean, val lines: List<Line>) {
    @Serializable
    class Line(val timeTag: String?, val words: String)
}