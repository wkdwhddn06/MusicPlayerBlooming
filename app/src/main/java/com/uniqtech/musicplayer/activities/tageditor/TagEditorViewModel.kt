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

package com.uniqtech.musicplayer.activities.tageditor

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.uniqtech.musicplayer.extensions.files.copyToUri
import com.uniqtech.musicplayer.extensions.files.getBestTag
import com.uniqtech.musicplayer.extensions.files.safeMerge
import com.uniqtech.musicplayer.extensions.files.toAudioFile
import com.uniqtech.musicplayer.http.Result
import com.uniqtech.musicplayer.http.lastfm.LastFmAlbum
import com.uniqtech.musicplayer.http.lastfm.LastFmTrack
import com.uniqtech.musicplayer.misc.TagWriter
import com.uniqtech.musicplayer.misc.TagWriter.WriteInfo
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.mvvm.SaveTagsResult
import com.uniqtech.musicplayer.mvvm.TagEditorResult
import com.uniqtech.musicplayer.repository.Repository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import java.io.File

/**
 * @author Christians M. A. (mardous)
 */
class TagEditorViewModel(private val repository: Repository, private val id: Long, private val name: String?) :
    ViewModel() {

    private val ioHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Failed to read file tags", throwable)
    }

    private val tagResult = MutableLiveData<TagEditorResult>()
    private val artworkResult = MutableLiveData<Artwork>()

    private var paths = mutableListOf<String>()
    private var uris = mutableListOf<Uri>()
    private var artworkId: Long = -1

    override fun onCleared() {
        super.onCleared()
        paths.clear()
        uris.clear()
    }

    fun getPaths(): List<String> = paths

    fun getUris(): List<Uri> = uris

    fun getArtworkId(): Long = artworkId

    fun getTags(): LiveData<TagEditorResult> = tagResult

    fun getArtwork(): LiveData<Artwork> = artworkResult

    /**
     * Request this ViewModel to emit an Artist using the arguments supplied.
     *
     * This method will try to find an album-artist or an artist and if none
     * is found, this method will not emit any result neither throw an exception.
     */
    fun requestArtist(): LiveData<Artist> = liveData(IO) {
        val artist = if (!name.isNullOrEmpty()) {
            repository.albumArtistByName(name)
        } else {
            repository.artistById(id)
        }
        if (artist != Artist.empty) {
            emit(artist)
        }
    }

    fun loadAlbumTags() = viewModelScope.launch(IO + ioHandler) {
        if (paths.isNotEmpty() && uris.isNotEmpty()) {
            loadTags(paths.first())
        } else {
            val album = repository.albumById(id)
            if (album != Album.empty) {
                artworkId = album.id
                album.songs.forEach {
                    paths.add(it.data)
                    uris.add(it.mediaStoreUri)
                }
                if (paths.isNotEmpty()) {
                    loadTags(paths.first())
                }
            }
        }
        loadArtwork()
    }

    fun loadArtistTags() = viewModelScope.launch(IO + ioHandler) {
        if (paths.isNotEmpty() && uris.isNotEmpty()) {
            loadTags(paths.first())
        } else {
            val artist = if (!name.isNullOrEmpty())
                repository.albumArtistByName(name)
            else repository.artistById(id)

            if (artist != Artist.empty) {
                artist.songs.forEach {
                    paths.add(it.data)
                    uris.add(it.mediaStoreUri)
                }
                if (paths.isNotEmpty()) {
                    loadTags(paths.first())
                }
            }
        }
    }

    fun loadSongTags() = viewModelScope.launch(IO + ioHandler) {
        if (paths.isNotEmpty() && uris.isNotEmpty()) {
            loadTags(paths.single())
        } else {
            val song = repository.songById(id)
            if (song != Song.emptySong) {
                artworkId = song.albumId
                paths.add(song.data)
                uris.add(song.mediaStoreUri)
                loadTags(paths.single())
            }
        }
        loadArtwork()
    }

    fun loadArtwork() = viewModelScope.launch(IO + ioHandler) {
        if (paths.isNotEmpty()) {
            val tag = File(paths.first()).toAudioFile()?.getBestTag()
            artworkResult.postValue(tag?.firstArtwork)
        }
    }

    private fun loadTags(path: String) {
        val file = File(path)
        val tag = file.toAudioFile()?.getBestTag()
        if (tag != null) {
            val newValue = TagEditorResult(
                title = tag.getFirst(FieldKey.TITLE),
                album = tag.getFirst(FieldKey.ALBUM),
                artist = tag.getAll(FieldKey.ARTIST).safeMerge(),
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST),
                composer = tag.getAll(FieldKey.COMPOSER).safeMerge(),
                conductor = tag.getAll(FieldKey.CONDUCTOR).safeMerge(),
                publisher = tag.getAll(FieldKey.RECORD_LABEL).safeMerge(),
                genre = tag.getAll(FieldKey.GENRE).safeMerge(),
                year = tag.getFirst(FieldKey.YEAR),
                trackNumber = tag.getFirst(FieldKey.TRACK),
                trackTotal = tag.getFirst(FieldKey.TRACK_TOTAL),
                discNumber = tag.getFirst(FieldKey.DISC_NO),
                discTotal = tag.getFirst(FieldKey.DISC_TOTAL),
                lyrics = tag.getFirst(FieldKey.LYRICS),
                lyricist = tag.getFirst(FieldKey.LYRICIST),
                comment = tag.getFirst(FieldKey.COMMENT)
            )
            tagResult.postValue(newValue)
        }
    }

    fun getAlbumInfo(artistName: String, albumName: String): LiveData<Result<LastFmAlbum>> = liveData(IO) {
        emit(repository.albumInfo(artistName, albumName, null))
    }

    fun getTrackInfo(artistName: String, title: String): LiveData<Result<LastFmTrack>> = liveData(IO) {
        emit(repository.trackInfo(artistName, title))
    }

    fun writeTags(context: Context, writeInfo: WriteInfo): LiveData<SaveTagsResult> =
        liveData(IO) {
            emit(SaveTagsResult(isLoading = true, isSuccess = false))
            val result = runCatching {
                TagWriter.writeTagsToFiles(context, writeInfo)
            }
            emit(SaveTagsResult(isLoading = false, isSuccess = result.isSuccess))
        }

    @RequiresApi(Build.VERSION_CODES.R)
    fun createCacheFiles(context: Context, writeInfo: WriteInfo): LiveData<SaveTagsResult> =
        liveData(IO) {
            emit(SaveTagsResult(isLoading = true, isSuccess = false))
            val result = runCatching {
                TagWriter.writeTagsToFilesR(context, writeInfo)
            }
            if (result.isSuccess) {
                emit(SaveTagsResult(isLoading = false, isSuccess = true, result.getOrThrow()))
            } else {
                emit(SaveTagsResult(isLoading = false, isSuccess = false))
            }
        }

    fun persistChanges(
        context: Context,
        paths: List<String>,
        destUris: List<Uri>,
        cacheFiles: List<File>
    ) = viewModelScope.launch(IO) {
        if (cacheFiles.size == destUris.size) {
            for (i in cacheFiles.indices) {
                try {
                    cacheFiles[i].copyToUri(context, destUris[i])
                } catch (_: Exception) {}
            }
        }
        withContext(Dispatchers.Main) {
            TagWriter.scan(context, paths)
        }
    }

    companion object {
        val TAG: String = TagEditorViewModel::class.java.simpleName
    }
}