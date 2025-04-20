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

package com.uniqtech.musicplayer.service

import android.content.*
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.MediaStore.Audio.AudioColumns
import android.util.Log
import androidx.core.content.ContextCompat
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.audio.AudioDevice
import com.uniqtech.musicplayer.audio.getDeviceType
import com.uniqtech.musicplayer.extensions.hasPie
import com.uniqtech.musicplayer.extensions.showToast
import com.uniqtech.musicplayer.extensions.utilities.isInRange
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.repository.RealSongRepository
import com.uniqtech.musicplayer.service.MusicService.MusicBinder
import com.uniqtech.musicplayer.service.playback.Playback
import com.uniqtech.musicplayer.util.FileUtil
import com.uniqtech.musicplayer.util.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.WeakHashMap
import kotlin.random.Random

object MusicPlayer : KoinComponent {

    private const val TAG = "MediaManager"

    private val songRepository by inject<RealSongRepository>()
    private val mConnectionMap = WeakHashMap<Context, ServiceBinder>()
    internal var musicService: MusicService? = null
        private set

    val routedDevice: AudioDevice?
        get() {
            if (hasPie()) {
                val deviceInfo = musicService?.getRoutedDevice()
                if (deviceInfo != null) {
                    return AudioDevice(deviceInfo.type, deviceInfo.getDeviceType(), deviceInfo.productName)
                }
            }
            return null
        }

    val audioSessionId: Int
        get() = musicService?.getAudioSessionId() ?: AudioEffect.ERROR_BAD_VALUE

    val currentSong: Song
        get() = musicService?.getCurrentSong() ?: Song.emptySong

    val playingQueue: List<Song>
        get() = musicService?.getPlayingQueue() ?: ArrayList()

    val songProgressMillis: Int
        get() = musicService?.getSongProgressMillis() ?: -1

    val songDurationMillis: Int
        get() = musicService?.getSongDurationMillis() ?: -1

    var position: Int
        get() = musicService?.getPosition() ?: -1
        set(position) {
            musicService?.setPosition(position)
        }

    var repeatMode: Int
        get() = musicService?.getRepeatMode() ?: Playback.RepeatMode.OFF
        set(repeatMode) {
            musicService?.setRepeatMode(repeatMode)
        }

    var shuffleMode: Int
        get() = musicService?.getShuffleMode() ?: Playback.ShuffleMode.OFF
        set(shuffleMode) {
            musicService?.setShuffleMode(shuffleMode)
        }

    var isPlaying: Boolean
        get() = musicService?.isPlaying == true
        set(isPlaying) {
            if (isPlaying) musicService?.play()
            else musicService?.pause()
        }

    var pendingQuit: Boolean
        get() = musicService?.pendingQuit ?: false
        set(pendingQuit) {
            musicService?.pendingQuit = pendingQuit
        }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    fun playSongAt(position: Int) {
        musicService?.playSongAt(position)
    }

    fun playNextSong() {
        musicService?.playNextSong(true)
    }

    fun playPreviousSong() {
        musicService?.playPreviousSong(true)
    }

    fun back() {
        musicService?.back(true)
    }

    fun openQueue(
        queue: List<Song>,
        position: Int = 0,
        startPlaying: Boolean = true,
        keepShuffleMode: Boolean = Preferences.rememberShuffleMode
    ) {
        musicService?.openQueue(queue, position, startPlaying)
        if (!keepShuffleMode) {
            shuffleMode = Playback.ShuffleMode.OFF
        }
    }

    fun openQueueShuffle(queue: List<Song>, startPlaying: Boolean = true) {
        var startPosition = 0
        if (queue.isNotEmpty()) {
            startPosition = Random.Default.nextInt(queue.size)
        }
        if (!tryToHandleOpenPlayingQueue(queue, startPosition, startPlaying) && musicService != null) {
            openQueue(queue, startPosition, startPlaying)
            shuffleMode = Playback.ShuffleMode.ON
        }
    }

    private fun tryToHandleOpenPlayingQueue(queue: List<Song?>, startPosition: Int, startPlaying: Boolean): Boolean {
        if (playingQueue === queue) {
            if (startPlaying) {
                playSongAt(startPosition)
            } else {
                position = startPosition
            }
            return true
        }
        return false
    }

    fun getSongAt(position: Int): Song = musicService?.getSongAt(position) ?: Song.emptySong

    fun getQueueDurationMillis(position: Int): Long = musicService?.getQueueDurationMillis(position) ?: -1

    fun getQueueDurationInfo(): String? =
        musicService?.getQueueDurationInfo()

    fun getNextSong() = musicService?.getNextSong()

    fun getNextSongInfo(context: Context) = musicService?.getNextSongInfo(context)

    fun seekTo(millis: Int) {
        musicService?.seek(millis)
    }

    fun cycleRepeatMode() {
        musicService?.cycleRepeatMode()
    }

    fun toggleShuffleMode() {
        musicService?.toggleShuffle()
    }

    fun playNext(song: Song, play: Boolean = false) {
        if (musicService != null) {
            if (playingQueue.isNotEmpty()) {
                musicService!!.playNext(song)
            } else {
                openQueue(listOf(song), startPlaying = false)
            }
            if (play) {
                playNextSong()
            } else {
                musicService?.showToast(musicService!!.getString(R.string.added_title_to_playing_queue))
            }
        }
    }

    fun playNext(songs: List<Song>) {
        if (musicService != null) {
            if (playingQueue.isNotEmpty()) {
                musicService!!.playNext(songs)
            } else {
                openQueue(songs, startPlaying = false)
            }

            musicService?.showToast(
                if (songs.size == 1)
                    musicService!!.getString(R.string.added_title_to_playing_queue)
                else musicService!!.getString(R.string.added_x_titles_to_playing_queue, songs.size)
            )
        }
    }

    fun enqueue(song: Song, toPosition: Int = -1) {
        if (musicService != null) {
            if (playingQueue.isNotEmpty()) {
                if (toPosition >= 0) {
                    musicService!!.addSong(toPosition, song)
                } else musicService!!.addSong(song)
            } else {
                openQueue(listOf(song), startPlaying = false)
            }
            musicService?.showToast(musicService!!.getString(R.string.added_title_to_playing_queue))
        }
    }

    fun enqueue(songs: List<Song>) {
        if (musicService != null) {
            if (playingQueue.isNotEmpty()) {
                musicService!!.addSongs(songs)
            } else {
                openQueue(songs, startPlaying = false)
            }
            musicService?.showToast(
                if (songs.size == 1)
                    musicService!!.getString(R.string.added_title_to_playing_queue)
                else musicService!!.getString(R.string.added_x_titles_to_playing_queue, songs.size)
            )
        }
    }

    fun removeFromQueue(song: Song) {
        musicService?.removeSong(song)
    }

    fun removeFromQueue(position: Int) {
        musicService?.removeSong(position)
    }

    fun removeFromQueue(songs: List<Song>): Boolean {
        if (musicService != null) {
            musicService!!.removeSongs(songs)
            return true
        }
        return false
    }

    fun moveToNextPosition(fromPosition: Int) {
        val nextPosition = musicService?.getNextPosition(false)
        if (nextPosition != null) {
            moveSong(fromPosition, nextPosition)
        }
    }

    private fun areValidPositions(vararg positions: Int) = positions.all { it.isInRange(0, playingQueue.size) }

    fun moveSong(from: Int, to: Int) {
        if (areValidPositions(from, to)) {
            musicService?.moveSong(from, to)
        }
    }

    fun clearQueue() {
        if (playingQueue.isNotEmpty()) {
            musicService?.clearQueue()
        }
    }

    fun shuffleQueue() {
        musicService?.shuffleQueue()
    }

    /**
     * Set the position of the last song to play before stopping playback.
     *
     * @return *true* if there was a previous "pending stop", *false* otherwise.
     */
    fun setStopPosition(stopPosition: Int): Boolean {
        if (musicService != null) {
            if (stopPosition.isInRange(position, playingQueue.size)) {
                var canceled = false
                if (musicService!!.stopPosition == stopPosition) {
                    musicService!!.setStopPosition(-1)
                    canceled = true
                } else {
                    musicService!!.setStopPosition(stopPosition)
                }

                val messageRes: Int =
                    if (canceled) R.string.sleep_timer_stop_after_x_canceled else R.string.sleep_timer_stop_after_x

                musicService?.showToast(
                    musicService!!.getString(messageRes, musicService!!.getSongAt(stopPosition).title)
                )

                return canceled
            }
        }
        return false
    }

    fun openEqualizerSession(internal: Boolean) {
        musicService?.getPlaybackManager()?.openAudioEffectSession(internal)
    }

    fun closeEqualizerSessions(internal: Boolean) {
        musicService?.getPlaybackManager()?.closeAudioEffectSession(internal)
    }

    fun resetEqualizer() {
        musicService?.getPlaybackManager()?.resetEqualizer()
    }

    fun updateEqualizer() {
        musicService?.getPlaybackManager()?.updateEqualizer()
    }

    fun updateBalance() {
        musicService?.getPlaybackManager()?.updateBalance()
    }

    fun updateTempo() {
        musicService?.getPlaybackManager()?.updateTempo()
    }

    fun playFromUri(uri: Uri): Boolean {
        if (musicService != null) {
            var song = Song.emptySong
            if (uri.scheme != null && uri.authority != null) {
                if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    var songId: String? = null
                    if (uri.authority == "com.android.providers.media.documents") {
                        songId = getSongIdFromMediaProvider(uri)
                    } else if (uri.authority == "media") {
                        songId = uri.lastPathSegment
                    }
                    if (songId != null) {
                        song = songRepository.song(songId.toLong())
                    }
                }
            }
            if (song === Song.emptySong) {
                var songFile: File? = null
                if (uri.authority != null && uri.authority == "com.android.externalstorage.documents") {
                    songFile =
                        File(FileUtil.externalStorageDirectory(), uri.path!!.split(":", limit = 2).toTypedArray()[1])
                }
                if (songFile == null) {
                    val path = getFilePathFromUri(musicService, uri)
                    if (path != null) songFile = File(path)
                }
                if (songFile == null && uri.path != null) {
                    songFile = File(uri.path!!)
                }
                if (songFile != null) {
                    song = songRepository.song(
                        songRepository.makeSongCursor(AudioColumns.DATA + "=?", arrayOf(songFile.absolutePath))
                    )
                }
            }
            if (song !== Song.emptySong) {
                openQueue(listOf(song))
                return true
            } else {
                Log.e(TAG, "No song found for URI: $uri")
            }
        }
        return false
    }

    private fun getFilePathFromUri(context: Context?, uri: Uri): String? {
        val column = AudioColumns.DATA
        val projection = arrayOf(column)
        try {
            context!!.contentResolver.query(uri, projection, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(column))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't get file path from uri $uri", e)
        }
        return null
    }

    private fun getSongIdFromMediaProvider(uri: Uri): String {
        return DocumentsContract.getDocumentId(uri).split(":").toTypedArray()[1]
    }

    fun bindToService(context: Context, callback: ServiceConnection): ServiceToken? {
        val contextWrapper = ContextWrapper(context)
        val intent = Intent(contextWrapper, MusicService::class.java)

        // https://issuetracker.google.com/issues/76112072#comment184
        // Workaround for ForegroundServiceDidNotStartInTimeException
        try {
            context.startService(intent)
        } catch (e: Exception) {
            ContextCompat.startForegroundService(context, intent)
        }

        val binder = ServiceBinder(callback)
        if (contextWrapper.bindService(intent, binder, Context.BIND_AUTO_CREATE)) {
            mConnectionMap[contextWrapper] = binder
            return ServiceToken(contextWrapper)
        }
        return null
    }

    fun unbindFromService(token: ServiceToken?) {
        if (token == null) {
            return
        }
        val mContextWrapper = token.mWrappedContext
        val mBinder = mConnectionMap.remove(mContextWrapper) ?: return
        mContextWrapper.unbindService(mBinder)
        if (mConnectionMap.isEmpty()) {
            musicService = null
        }
    }

    class ServiceBinder internal constructor(private val mConnection: ServiceConnection?) :
        ServiceConnection {
        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            val musicBinder = binder as MusicBinder
            musicService = musicBinder.service
            mConnection?.onServiceConnected(component, binder)
        }

        override fun onServiceDisconnected(component: ComponentName) {
            mConnection?.onServiceDisconnected(component)
            musicService = null
        }
    }

    class ServiceToken internal constructor(val mWrappedContext: ContextWrapper)
}