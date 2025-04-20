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

package com.uniqtech.musicplayer.fragments.lyrics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.uniqtech.musicplayer.appContext
import com.uniqtech.musicplayer.database.LyricsDao
import com.uniqtech.musicplayer.database.toLyricsEntity
import com.uniqtech.musicplayer.extensions.files.getBestTag
import com.uniqtech.musicplayer.extensions.files.getContentUri
import com.uniqtech.musicplayer.extensions.files.readString
import com.uniqtech.musicplayer.extensions.files.toAudioFile
import com.uniqtech.musicplayer.extensions.hasR
import com.uniqtech.musicplayer.extensions.isAllowedToDownloadMetadata
import com.uniqtech.musicplayer.extensions.media.isArtistNameUnknown
import com.uniqtech.musicplayer.http.Result
import com.uniqtech.musicplayer.http.lyrics.LyricsService
import com.uniqtech.musicplayer.lyrics.LrcUtils
import com.uniqtech.musicplayer.misc.TagWriter
import com.uniqtech.musicplayer.model.DownloadedLyrics
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.mvvm.LyricsResult
import com.uniqtech.musicplayer.mvvm.SaveLyricsResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.EnumMap
import java.util.regex.Pattern

/**
 * @author Christians M. A. (mardous)
 */
class LyricsViewModel(
    private val lyricsDao: LyricsDao,
    private val lyricsService: LyricsService
) : ViewModel() {

    private val silentHandler = CoroutineExceptionHandler { _, _ -> }

    fun getOnlineLyrics(song: Song, title: String, artist: String): LiveData<Result<DownloadedLyrics>> = liveData(IO) {
        if (song.id == Song.emptySong.id) {
            emit(Result.Error(IllegalArgumentException("Song is not valid")))
        } else {
            emit(Result.Loading)
            if (artist.isArtistNameUnknown()) {
                emit(Result.Error(IllegalArgumentException("Artist name is <unknown>")))
            } else {
                val result = try {
                    Result.Success(lyricsService.getLyrics(song, title, artist))
                } catch (e: Exception) {
                    Result.Error(e)
                }
                emit(result)
            }
        }
    }

    fun getAllLyrics(song: Song, allowDownload: Boolean = false, isFallbackAllowed: Boolean = false): LiveData<LyricsResult> =
        liveData(IO + silentHandler) {
            check(song.id != Song.emptySong.id)
            val embeddedLyrics = getEmbeddedLyrics(song, isFallbackAllowed)
            val syncedLyrics = lyricsDao.getLyrics(song.id)
            if (syncedLyrics == null && allowDownload && appContext().isAllowedToDownloadMetadata()) {
                val onlineLyrics = lyricsService.getLyrics(song)
                if (onlineLyrics.isSynced) {
                    val lrcData = LrcUtils.parse(onlineLyrics.syncedLyrics!!)
                    if (lrcData.hasLines) {
                        lyricsDao.insertLyrics(song.toLyricsEntity(lrcData.getText(), autoDownload = true))
                        emit(LyricsResult(song.id, embeddedLyrics, lrcData))
                    }
                }
            } else if (syncedLyrics != null) {
                val lrcData = LrcUtils.parse(syncedLyrics.syncedLyrics)
                emit(LyricsResult(song.id, embeddedLyrics, lrcData))
            } else {
                if (!embeddedLyrics.isNullOrEmpty()) {
                    val parsedLrc = LrcUtils.parse(embeddedLyrics)
                    if (parsedLrc.hasLines) {
                        emit(LyricsResult(song.id, data = embeddedLyrics, lrcData = parsedLrc))
                    } else {
                        emit(LyricsResult(song.id, embeddedLyrics))
                    }
                } else {
                    emit(LyricsResult(song.id, embeddedLyrics))
                }
            }
        }

    fun getLyrics(song: Song, isFallbackAllowed: Boolean = false): LiveData<LyricsResult> =
        liveData(IO + silentHandler) {
            if (song.id != Song.emptySong.id) {
                emit(LyricsResult(song.id, getEmbeddedLyrics(song, isFallbackAllowed)))
            }
        }

    fun deleteLyrics() = viewModelScope.launch(IO) {
        lyricsDao.removeLyrics()
    }

    fun shareSyncedLyrics(context: Context, song: Song): LiveData<Uri?> = liveData(IO) {
        if (song.id == Song.emptySong.id) {
            emit(null)
        } else {
            val lyrics = lyricsDao.getLyrics(song.id)
            if (lyrics != null) {
                val tempFile = appContext().externalCacheDir
                    ?.resolve("${song.artistName} - ${song.title}.lrc")
                if (tempFile == null) {
                    emit(null)
                } else {
                    val result = runCatching {
                        tempFile.bufferedWriter().use {
                            it.write(lyrics.syncedLyrics)
                        }
                        tempFile.getContentUri(context)
                    }
                    if (result.isSuccess) {
                        emit(result.getOrThrow())
                    } else {
                        emit(null)
                    }
                }
            } else {
                emit(null)
            }
        }
    }

    fun saveLyrics(
        context: Context,
        song: Song,
        plainLyrics: String?,
        syncedLyrics: String?,
        plainLyricsModified: Boolean
    ): LiveData<SaveLyricsResult> = liveData(IO) {
        saveSyncedLyrics(song, syncedLyrics)
        if (!plainLyricsModified) {
            emit(SaveLyricsResult(isPending = false, isSuccess = true))
        } else {
            val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java).apply {
                put(FieldKey.LYRICS, plainLyrics)
            }
            val writeInfo = TagWriter.WriteInfo(listOf(song.data), fieldKeyValueMap, null)
            if (hasR()) {
                val pending = runCatching {
                    TagWriter.writeTagsToFilesR(context, writeInfo).first() to song.mediaStoreUri
                }
                if (pending.isSuccess) {
                    emit(SaveLyricsResult(isPending = true, isSuccess = false, pending.getOrThrow()))
                } else {
                    emit(SaveLyricsResult(isPending = false, isSuccess = false))
                }
            } else {
                val result = runCatching {
                    TagWriter.writeTagsToFiles(context, writeInfo)
                }
                if (result.isSuccess) {
                    emit(SaveLyricsResult(isPending = false, isSuccess = true))
                } else {
                    emit(SaveLyricsResult(isPending = false, isSuccess = false))
                }
            }
        }
    }

    private suspend fun saveSyncedLyrics(song: Song, syncedLyrics: String?) {
        if (syncedLyrics.isNullOrEmpty()) {
            val lyrics = lyricsDao.getLyrics(song.id)
            if (lyrics != null) {
                if (lyrics.autoDownload) {
                    // The user has deleted an automatically downloaded lyrics, perhaps
                    // because it was incorrect. In this case we do not delete the
                    // registry, we simply clean it, this way it will prevent us from
                    // trying to download it again in the future.
                    lyricsDao.insertLyrics(song.toLyricsEntity("", userCleared = true))
                } else if (!lyrics.userCleared) {
                    lyricsDao.removeLyrics(song.id)
                }
            }
        } else {
            val parsedLyrics = LrcUtils.parse(syncedLyrics)
            if (parsedLyrics.hasLines) {
                lyricsDao.insertLyrics(song.toLyricsEntity(parsedLyrics.getText()))
            }
        }
    }

    fun setLRCContentFromUri(context: Context, song: Song, uri: Uri?): LiveData<Boolean> =
        liveData(IO) {
            val path = uri?.path
            if (path != null && path.endsWith(".lrc")) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = runCatching { stream.readString() }
                    if (content.isSuccess) {
                        val parsed = LrcUtils.parse(content.getOrThrow())
                        if (parsed.hasLines) {
                            lyricsDao.insertLyrics(song.toLyricsEntity(parsed.getText()))
                            emit(true)
                        } else {
                            emit(false)
                        }
                    } else {
                        emit(false)
                    }
                }
            } else {
                emit(false)
            }
        }

    private fun getEmbeddedLyrics(song: Song, isFallbackAllowed: Boolean): String? {
        var lyrics: String? = null

        val file = File(song.data)

        try {
            lyrics = file.toAudioFile()?.getBestTag(false)?.getFirst(FieldKey.LYRICS)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        if (lyrics.isNullOrEmpty() && isFallbackAllowed) {
            val dir = file.absoluteFile.parentFile
            if (dir != null && dir.exists() && dir.isDirectory) {
                val format = ".*%s.*\\.(lrc|txt)"

                val filename = Pattern.quote(file.nameWithoutExtension)
                val songtitle = Pattern.quote(song.title)

                val patterns = ArrayList<Pattern>().apply {
                    add(
                        Pattern.compile(
                            String.format(format, filename),
                            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                        )
                    )
                    add(
                        Pattern.compile(
                            String.format(format, songtitle),
                            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                        )
                    )
                }

                val files = dir.listFiles { f: File ->
                    for (pattern in patterns) {
                        if (pattern.matcher(f.name).matches()) return@listFiles true
                    }
                    false
                }

                if (files != null && files.isNotEmpty()) {
                    for (f in files) {
                        try {
                            val newLyrics = f.readText()
                            if (newLyrics.trim().isNotEmpty()) {
                                lyrics = newLyrics
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return lyrics
    }
}