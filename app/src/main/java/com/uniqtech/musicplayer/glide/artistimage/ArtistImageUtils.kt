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
package com.uniqtech.musicplayer.glide.artistimage

import com.uniqtech.musicplayer.http.deezer.DeezerArtist
import com.uniqtech.musicplayer.util.ImageSize

object ArtistImageUtils {

    fun getDeezerArtistImageUrl(
        deezerArtist: DeezerArtist.Result?,
        requestedImageSize: String?
    ) = deezerArtist?.let { artist ->
        getImageUrl(artist, requestedImageSize)
            ?.takeIf { it.isNotBlank() && !it.contains("/images/artist//") }
    }

    private fun getImageUrl(
        result: DeezerArtist.Result,
        requestedImageSize: String?
    ): String? {
        return when (requestedImageSize) {
            ImageSize.LARGE -> result.largeImage ?: result.mediumImage
            ImageSize.SMALL -> result.smallImage ?: result.mediumImage
            else -> result.mediumImage
        }
    }
}
