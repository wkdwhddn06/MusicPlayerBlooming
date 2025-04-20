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

package com.uniqtech.musicplayer.extensions.glide

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import com.bumptech.glide.*
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.MediaStoreSignature
import com.bumptech.glide.signature.ObjectKey
import com.uniqtech.musicplayer.appContext
import com.uniqtech.musicplayer.extensions.media.albumCoverUri
import com.uniqtech.musicplayer.extensions.resources.defaultFooterColor
import com.uniqtech.musicplayer.extensions.resources.getDrawableCompat
import com.uniqtech.musicplayer.glide.artistimage.ArtistImage
import com.uniqtech.musicplayer.glide.audiocover.AudioFileCover
import com.uniqtech.musicplayer.glide.palette.BitmapPaletteWrapper
import com.uniqtech.musicplayer.glide.transformation.BlurTransformation
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.util.Preferences
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import com.uniqtech.musicplayer.R

val DEFAULT_ARTIST_IMAGE: Int = R.drawable.default_artist_art
val DEFAULT_SONG_IMAGE: Int = R.drawable.default_audio_art
val DEFAULT_ALBUM_IMAGE: Int = R.drawable.default_album_art

private val DEFAULT_DISK_CACHE_STRATEGY_ARTIST = DiskCacheStrategy.RESOURCE
private val DEFAULT_DISK_CACHE_STRATEGY = DiskCacheStrategy.DATA

private val glideDispatcher: CoroutineDispatcher by lazy {
    Executors.newFixedThreadPool(4).asCoroutineDispatcher()
}

suspend fun Glide.clearCache(clearDiskCache: Boolean = false) {
    clearMemory()
    withContext(Dispatchers.IO) {
        if (clearDiskCache) {
            clearDiskCache()
        }
    }
}

@Suppress("FunctionName")
fun GlideScope(): CoroutineScope = CoroutineScope(SupervisorJob() + glideDispatcher)

@SuppressLint("CheckResult")
fun <T> RequestBuilder<T>.blurImage(context: Context, model: Any?): RequestBuilder<T> = apply {
    load(model)
    transform(BlurTransformation.Builder(context).build())
    error(ColorDrawable(context.defaultFooterColor()))
}

fun RequestManager.asBitmapPalette(): RequestBuilder<BitmapPaletteWrapper> {
    return this.`as`(BitmapPaletteWrapper::class.java)
}

fun <TranscodeType> getDefaultGlideTransition(): GenericTransitionOptions<TranscodeType> {
    return GenericTransitionOptions<TranscodeType>().transition(android.R.anim.fade_in)
}

fun Song.getSongGlideModel(ignoreMediaStore: Boolean = Preferences.ignoreMediaStore): Any {
    return if (ignoreMediaStore) {
        AudioFileCover(data, Preferences.useFolderImages)
    } else {
        albumId.albumCoverUri()
    }
}

@SuppressLint("CheckResult")
fun <T> RequestBuilder<T>.songOptions(song: Song) = apply {
    placeholder(getDrawable(DEFAULT_SONG_IMAGE))
    error(getDrawable(DEFAULT_SONG_IMAGE))
    diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY)
    signature(createSignature(song))
}

fun Artist.getArtistGlideModel(): Any {
    return if (!hasCustomImage()) {
        ArtistImage(this)
    } else {
        getCustomImageFile()
    }
}

@SuppressLint("CheckResult")
fun <T> RequestBuilder<T>.artistOptions(artist: Artist) = apply {
    placeholder(getDrawable(DEFAULT_ARTIST_IMAGE))
    error(getDrawable(DEFAULT_ARTIST_IMAGE))
    diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY_ARTIST)
    priority(Priority.LOW)
    override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
    signature(createSignature(artist))
}

fun Album.getAlbumGlideModel() = safeGetFirstSong().getSongGlideModel()

@SuppressLint("CheckResult")
fun <T> RequestBuilder<T>.albumOptions(album: Album) = apply {
    placeholder(getDrawable(DEFAULT_ALBUM_IMAGE))
    error(getDrawable(DEFAULT_ALBUM_IMAGE))
    signature(createSignature(album))
}

@SuppressLint("CheckResult")
fun <T> RequestBuilder<T>.playlistOptions() = apply {
    diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    placeholder(getDrawable(DEFAULT_ALBUM_IMAGE))
    error(getDrawable(DEFAULT_ALBUM_IMAGE))
}

private fun getDrawable(@DrawableRes id: Int): Drawable? {
    return appContext().getDrawableCompat(id)
}

private fun createSignature(artist: Artist): Key {
    return artist.getSignature()
}

private fun createSignature(album: Album): Key {
    return ObjectKey(album.id.toString())
}

private fun createSignature(song: Song): Key {
    return MediaStoreSignature("", song.dateModified, 0)
}