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

package com.uniqtech.musicplayer.http.deezer

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class DeezerService(private val client: HttpClient) {
    suspend fun artist(artistName: String) =
        try {
            client.get("https://api.deezer.com/search/artist") {
                url {
                    parameters.append("limit", "1")
                    parameters.append("q", artistName)
                }
            }.body<DeezerArtist>()
        } catch (e: Exception) {
            Log.w("DeezerService", "Couldn't decode Deezer response for $artistName", e)
            null
        }
}