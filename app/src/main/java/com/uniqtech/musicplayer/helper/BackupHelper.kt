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

package com.uniqtech.musicplayer.helper

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.database.LyricsDao
import com.uniqtech.musicplayer.database.LyricsEntity
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.database.toSongEntity
import com.uniqtech.musicplayer.extensions.files.zipOutputStream
import com.uniqtech.musicplayer.extensions.showToast
import com.uniqtech.musicplayer.helper.m3u.M3UWriter
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.repository.Repository
import com.uniqtech.musicplayer.repository.SongRepository
import com.uniqtech.musicplayer.service.equalizer.EqualizerManager
import com.uniqtech.musicplayer.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BackupHelper : KoinComponent {

    private val repository by inject<Repository>()
    private val songRepository by inject<SongRepository>()
    private val lyricsDao by inject<LyricsDao>()

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setStrictness(Strictness.LENIENT)
        .create()

    suspend fun createBackup(context: Context, uri: Uri?) {
        if (uri == null) return
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        val zipItems = mutableListOf<ZipItem>()
        zipItems.addAll(getPlaylistZipItems(context))
        zipItems.addAll(getSettingsZipItems(context))
        zipItems.addAll(getLyricsZipItems())
        zipItems.addAll(getCustomArtistZipItems(context))
        zipAll(context, zipItems, outputStream)
        // Clean Cache Playlist Directory
        File(context.filesDir, PLAYLISTS_PATH).deleteRecursively()
    }

    private suspend fun zipAll(context: Context, zipItems: List<ZipItem>, output: OutputStream) =
        withContext(Dispatchers.IO) {
            runCatching {
                output.zipOutputStream().use { out ->
                    out.setComment(context.getString(R.string.app_name_long))
                    for (zipItem in zipItems) {
                        if (zipItem.filePath != null) {
                            File(zipItem.filePath).inputStream().buffered().use { origin ->
                                val entry = ZipEntry(zipItem.zipPath)
                                out.putNextEntry(entry)
                                origin.copyTo(out)
                            }
                        } else if (!zipItem.fileContent.isNullOrEmpty()) {
                            val entry = ZipEntry(zipItem.zipPath)
                            out.putNextEntry(entry)
                            out.bufferedWriter().use { it.write(zipItem.fileContent) }
                        }
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    context.showToast(R.string.backup_failed)
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    context.showToast(R.string.backup_successful)
                }
            }
        }

    private suspend fun getPlaylistZipItems(context: Context): List<ZipItem> {
        val playlistZipItems = mutableListOf<ZipItem>()
        // Cache Playlist files in App storage
        val playlistFolder = File(context.filesDir, PLAYLISTS_PATH)
        if (!playlistFolder.exists()) {
            playlistFolder.mkdirs()
        }
        for (playlist in repository.playlistsWithSongs()) {
            runCatching {
                M3UWriter.writeToDirectory(playlistFolder, playlist)
            }.onSuccess { playlistFile ->
                if (playlistFile.exists()) {
                    playlistZipItems.add(
                        ZipItem(
                            PLAYLISTS_PATH.child(playlistFile.name),
                            playlistFile.absolutePath
                        )
                    )
                }
            }
        }
        return playlistZipItems
    }

    private suspend fun getLyricsZipItems(): List<ZipItem> {
        val allLyrics = lyricsDao.getAllLyrics()
        if (allLyrics.isNotEmpty()) {
            return listOf(ZipItem(LYRICS_PATH.child("lyrics.json"), fileContent = gson.toJson(allLyrics)))
        }
        return emptyList()
    }

    private fun getSettingsZipItems(context: Context): List<ZipItem> {
        val sharedPrefPath = File(context.filesDir.parentFile, "shared_prefs")
        return listOf(
            "${context.packageName}_preferences.xml",
            "${EqualizerManager.PREFERENCES_NAME}.xml"
        ).map {
            ZipItem(SETTINGS_PATH.child(it), File(sharedPrefPath, it).absolutePath)
        }
    }

    private fun getCustomArtistZipItems(context: Context): List<ZipItem> {
        val zipItemList = mutableListOf<ZipItem>()
        val sharedPrefPath = File(context.filesDir.parentFile, "shared_prefs")
        val customArtistImagesDir = FileUtil.customArtistImagesDirectory()
        if (customArtistImagesDir != null) {
            zipItemList.addAll(
                customArtistImagesDir.listFiles()
                    ?.map {
                        ZipItem(
                            CUSTOM_ARTISTS_PATH.child("custom_artist_images").child(it.name),
                            it.absolutePath
                        )
                    }?.toList() ?: listOf()
            )

            File(sharedPrefPath, "custom_artist_images.xml").let {
                if (it.exists()) {
                    zipItemList.add(
                        ZipItem(
                            CUSTOM_ARTISTS_PATH.child("prefs").child("custom_artist_images.xml"),
                            it.absolutePath
                        )
                    )
                }
            }
        }
        return zipItemList
    }

    suspend fun restoreBackup(context: Context, uri: Uri?, contents: List<BackupContent>) {
        if (uri == null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    ZipInputStream(inputStream).use {
                        var entry = it.nextEntry
                        while (entry != null) {
                            if (entry.isPlaylistEntry() && contents.contains(BackupContent.Playlists)) {
                                restorePlaylists(it, entry)
                            } else if (entry.isPreferenceEntry() && contents.contains(BackupContent.Settings)) {
                                restorePreferences(context, it, entry)
                            } else if (entry.isLyricsEntry() && contents.contains(BackupContent.Lyrics)) {
                                restoreLyrics(it)
                            } else if (entry.isCustomArtistEntry() && contents.contains(BackupContent.ArtistImages)) {
                                if (entry.isCustomArtistPrefEntry()) {
                                    restoreCustomArtistPrefs(context, it, entry)
                                } else if (entry.isCustomArtistImageEntry()) {
                                    restoreCustomArtistImages(context, it, entry)
                                }
                            }
                            entry = it.nextEntry
                        }
                    }
                } else {
                    throw FileNotFoundException()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    context.showToast(R.string.could_not_restore_data)
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    context.showToast(R.string.data_restored_successfully)
                }
            }
        }
    }

    private suspend fun restoreLyrics(zipIn: ZipInputStream) {
        val serializedLyrics = zipIn.bufferedReader().readText()
        val lyrics: List<LyricsEntity> = gson.fromJson(
            serializedLyrics, object : TypeToken<List<LyricsEntity>>() {}.type
        )
        lyricsDao.insertLyrics(lyrics)
    }

    private fun restorePreferences(context: Context, zipIn: ZipInputStream, zipEntry: ZipEntry) {
        val file = File(context.filesDir.parent!! + File.separator + "shared_prefs" + File.separator + zipEntry.getFileName())
        if (file.exists()) {
            file.delete()
        }
        file.outputStream().buffered().use { bos ->
            zipIn.copyTo(bos)
        }
    }

    private suspend fun restorePlaylists(zipIn: ZipInputStream, zipEntry: ZipEntry) {
        val playlistName = zipEntry.getFileName().substringBeforeLast(".")
        val songs = mutableListOf<Song>()

        // Get songs from m3u playlist files
        zipIn.bufferedReader().lineSequence().forEach { line ->
            if (line.startsWith(File.separator)) {
                if (File(line).exists()) {
                    songs.addAll(songRepository.songsByFilePath(line))
                }
            }
        }
        val playlistEntity = repository.checkPlaylistExists(playlistName).firstOrNull()
        if (playlistEntity != null) {
            val songEntities = songs.map {
                it.toSongEntity(playlistEntity.playListId)
            }
            repository.insertSongsInPlaylist(songEntities)
        } else {
            val playListId = repository.createPlaylist(PlaylistEntity(playlistName = playlistName))
            val songEntities = songs.map {
                it.toSongEntity(playListId)
            }
            repository.insertSongsInPlaylist(songEntities)
        }
    }

    private fun restoreCustomArtistImages(
        context: Context,
        zipIn: ZipInputStream,
        zipEntry: ZipEntry
    ) {
        val parentFolder = File(context.filesDir, "custom_artist_images")
        if (!parentFolder.exists()) {
            parentFolder.mkdirs()
        }
        val file = File(parentFolder, zipEntry.getFileName())
        file.outputStream().buffered().use { bos ->
            zipIn.copyTo(bos)
        }
    }

    private fun restoreCustomArtistPrefs(
        context: Context,
        zipIn: ZipInputStream,
        zipEntry: ZipEntry
    ) {
        val file = File(context.filesDir.parentFile, "shared_prefs".child(zipEntry.getFileName()))
        file.outputStream().buffered().use { bos ->
            zipIn.copyTo(bos)
        }
    }

    const val BACKUP_EXTENSION = "bmgbak"
    const val APPEND_EXTENSION = ".$BACKUP_EXTENSION"
    private const val PLAYLISTS_PATH = "Playlists"
    private const val SETTINGS_PATH = "prefs"
    private const val LYRICS_PATH = "lyrics"
    private const val CUSTOM_ARTISTS_PATH = "artistImages"

    private fun ZipEntry.isPlaylistEntry(): Boolean {
        return name.startsWith(PLAYLISTS_PATH)
    }

    private fun ZipEntry.isPreferenceEntry(): Boolean {
        return name.startsWith(SETTINGS_PATH)
    }

    private fun ZipEntry.isLyricsEntry(): Boolean {
        return name.startsWith(LYRICS_PATH)
    }

    private fun ZipEntry.isCustomArtistEntry(): Boolean {
        return name.startsWith(CUSTOM_ARTISTS_PATH)
    }

    private fun ZipEntry.isCustomArtistImageEntry(): Boolean {
        return name.startsWith(CUSTOM_ARTISTS_PATH) && name.contains("custom_artist_images")
    }

    private fun ZipEntry.isCustomArtistPrefEntry(): Boolean {
        return name.startsWith(CUSTOM_ARTISTS_PATH) && name.contains("prefs")
    }

    private fun ZipEntry.getFileName(): String {
        return name.substring(name.lastIndexOf(File.separator) + 1)
    }
}

data class ZipItem(
    val zipPath: String,
    val filePath: String? = null,
    val fileContent: String? = null
)

fun CharSequence.sanitize(): String {
    return toString().replace("/", "_")
        .replace(":", "_")
        .replace("*", "_")
        .replace("?", "_")
        .replace("\"", "_")
        .replace("<", "_")
        .replace(">", "_")
        .replace("|", "_")
        .replace("\\", "_")
        .replace("&", "_")
}

fun String.child(child: String): String {
    return this + File.separator + child
}

enum class BackupContent(@StringRes val titleRes: Int) {
    Settings(R.string.backup_settings),
    Lyrics(R.string.backup_synced_lyrics),
    ArtistImages(R.string.backup_artist_images),
    Playlists(R.string.backup_playlists)
}