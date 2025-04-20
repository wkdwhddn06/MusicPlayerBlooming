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

package com.uniqtech.musicplayer.service.queue

import android.content.SharedPreferences
import androidx.core.content.edit
import com.uniqtech.musicplayer.helper.ShuffleHelper
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.providers.databases.PlaybackQueueStore
import com.uniqtech.musicplayer.service.MusicService
import com.uniqtech.musicplayer.service.constants.ServiceEvent
import com.uniqtech.musicplayer.service.playback.Playback
import com.uniqtech.musicplayer.util.SAVED_POSITION_IN_TRACK
import com.uniqtech.musicplayer.util.SAVED_QUEUE_POSITION
import com.uniqtech.musicplayer.util.SAVED_REPEAT_MODE
import com.uniqtech.musicplayer.util.SAVED_SHUFFLE_MODE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author Christians M. A. (mardous)
 */
class SmartPlayingQueue(
    private val musicService: MusicService,
    private val sharedPreferences: SharedPreferences,
    private val coroutineScope: CoroutineScope,
    private var isSequentialQueue: Boolean
) {

    var shuffleMode = Playback.ShuffleMode.OFF
    var repeatMode = Playback.ShuffleMode.OFF
    var stopPosition = -1
    var nextPosition = -1
    var position = -1

    var playingQueue: MutableList<QueueSong> = ArrayList()
        private set
    private var originalPlayingQueue: MutableList<QueueSong> = ArrayList()
    private var queuesRestored = false

    private val lastUpcomingPosition: Int
        get() = playingQueue.indexOfLast { it.isUpcoming }

    val currentSong: Song
        get() = getSongAt(position)

    val isFirstTrack: Boolean
        get() = position == 0

    val isLastTrack: Boolean
        get() = position == playingQueue.lastIndex

    fun setSequentialMode(isSequentialQueue: Boolean) {
        this.isSequentialQueue = isSequentialQueue
        if (!isSequentialQueue) {
            removeAllRanges()
        }
    }

    private fun Song.toQueueSong(upcoming: Boolean = false) = QueueSong(this, upcoming)

    private fun List<Song>.toQueueSongs(upcoming: Boolean = false) =
        map { QueueSong(it, upcoming) }

    fun open(queue: List<Song>, startPosition: Int, onCompleted: (position: Int) -> Unit) {
        if (queue.isNotEmpty() && startPosition >= 0 && startPosition < queue.size) {
            // it is important to copy the playing queue here first as we might add/remove songs later
            this.originalPlayingQueue = ArrayList(queue.toQueueSongs())
            this.playingQueue = ArrayList(originalPlayingQueue)
            var position = startPosition
            if (shuffleMode == Playback.ShuffleMode.ON) {
                ShuffleHelper.makeShuffleList(playingQueue, startPosition)
                position = 0
            }
            onCompleted(position)
        }
    }

    fun getSongAt(position: Int): Song {
        return if (position >= 0 && position < playingQueue.size) {
            playingQueue[position]
        } else Song.emptySong
    }

    fun getDuration(position: Int) = playingQueue.drop(position).sumOf { it.duration }

    fun getNextPosition(force: Boolean): Int {
        var position = this.position + 1
        when (repeatMode) {
            Playback.RepeatMode.ALL -> if (isLastTrack) {
                position = 0
            }

            Playback.RepeatMode.CURRENT -> if (force) {
                if (isLastTrack) {
                    position = 0
                }
            } else {
                position -= 1
            }

            Playback.RepeatMode.OFF -> if (isLastTrack) {
                position -= 1
            }

            else -> if (isLastTrack) {
                position -= 1
            }
        }
        return position
    }

    fun getPreviousPosition(force: Boolean): Int {
        var newPosition = this.position - 1
        when (repeatMode) {
            Playback.RepeatMode.ALL -> if (newPosition < 0) {
                newPosition = playingQueue.size - 1
            }

            Playback.RepeatMode.CURRENT -> if (force) {
                if (newPosition < 0) {
                    newPosition = playingQueue.size - 1
                }
            } else {
                newPosition = this.position
            }

            Playback.RepeatMode.OFF -> if (newPosition < 0) {
                newPosition = 0
            }

            else -> if (newPosition < 0) {
                newPosition = 0
            }
        }
        return newPosition
    }

    fun playNext(song: Song) {
        if (!isSequentialQueue) {
            addSong(position + 1, song)
        } else {
            val queueSong = song.toQueueSong(true)
            if (position == playingQueue.lastIndex) {
                playingQueue.add(queueSong)
                originalPlayingQueue.add(queueSong)
            } else for (i in (position + 1) until playingQueue.size) {
                val item = playingQueue[i]
                if (item.isUpcoming) {
                    if (i == playingQueue.lastIndex) {
                        playingQueue.add(queueSong)
                        originalPlayingQueue.add(queueSong)
                        break
                    }
                } else {
                    playingQueue.add(i, queueSong)
                    originalPlayingQueue.add(i, queueSong)
                    break
                }
            }
        }
    }

    fun playNext(songs: List<Song>) {
        if (!isSequentialQueue) {
            addSongs(position + 1, songs)
        } else {
            val queueSong = songs.toQueueSongs(true)
            if (position == playingQueue.lastIndex) {
                playingQueue.addAll(queueSong)
                originalPlayingQueue.addAll(queueSong)
            } else for (i in (position + 1) until playingQueue.size) {
                val item = playingQueue[i]
                if (item.isUpcoming) {
                    if (i == playingQueue.lastIndex) {
                        playingQueue.addAll(queueSong)
                        originalPlayingQueue.addAll(queueSong)
                        break
                    }
                } else {
                    playingQueue.addAll(i, queueSong)
                    originalPlayingQueue.addAll(i, queueSong)
                    break
                }
            }
        }
    }

    fun addSong(position: Int, song: Song) {
        val isUpcomingRange = isInUpcomingRange(position)
        val queueSong = song.toQueueSong(isUpcomingRange)
        playingQueue.add(position, queueSong)
        originalPlayingQueue.add(position, queueSong)
    }

    fun addSong(song: Song) {
        val queueSong = song.toQueueSong()
        playingQueue.add(queueSong)
        originalPlayingQueue.add(queueSong)
    }

    fun addSongs(position: Int, songs: List<Song>) {
        val isUpcomingRange = isInUpcomingRange(position)
        val queueSongs = songs.toQueueSongs(isUpcomingRange)
        playingQueue.addAll(position, queueSongs)
        originalPlayingQueue.addAll(position, queueSongs)
    }

    fun addSongs(songs: List<Song>) {
        val queueSongs = songs.toQueueSongs()
        playingQueue.addAll(queueSongs)
        originalPlayingQueue.addAll(queueSongs)
    }

    fun moveSong(from: Int, to: Int) {
        if (from == to)
            return

        val lastUpcomingIndex = lastUpcomingPosition
        val currPosition = this.position
        val songToMove = playingQueue.removeAt(from)
        songToMove.isUpcoming = isInUpcomingRange(to)
        playingQueue.add(to, songToMove)
        if (shuffleMode == Playback.RepeatMode.OFF) {
            val tmpSong = originalPlayingQueue.removeAt(from)
            originalPlayingQueue.add(to, tmpSong)
        }
        when {
            currPosition in to until from -> position = currPosition + 1
            currPosition in (from + 1)..to -> position = currPosition - 1
            from == currPosition -> {
                this.position = to
                if (to < currPosition) {
                    realignUpcomingRange(lastIndex = lastUpcomingIndex)
                } else if (to >= lastUpcomingIndex) {
                    removeAllRanges()
                }
            }
        }
    }

    fun removeSong(position: Int) {
        if (shuffleMode == Playback.ShuffleMode.OFF) {
            playingQueue.removeAt(position)
            originalPlayingQueue.removeAt(position)
        } else {
            originalPlayingQueue.remove(playingQueue.removeAt(position))
        }
        rePosition(position)
    }

    fun removeSong(song: Song) {
        removeSongImpl(song)
    }

    fun removeSongs(songs: List<Song>) {
        for (song in songs) {
            removeSongImpl(song)
        }
    }

    private fun removeSongImpl(song: Song) {
        val deletePosition = playingQueue.indexOf(song)
        if (deletePosition != -1) {
            playingQueue.removeAt(deletePosition)
            rePosition(deletePosition)
        }

        val originalDeletePosition = originalPlayingQueue.indexOf(song)
        if (originalDeletePosition != -1) {
            originalPlayingQueue.removeAt(originalDeletePosition)
            rePosition(originalDeletePosition)
        }
    }

    private fun rePosition(deletedPosition: Int) {
        val currentPosition = this.position
        if (deletedPosition < currentPosition) {
            position = currentPosition - 1
        } else if (deletedPosition == currentPosition) {
            if (playingQueue.size > deletedPosition) {
                musicService.setPosition(position)
            } else {
                musicService.setPosition(position - 1)
            }
        }
    }

    private fun isInUpcomingRange(index: Int, firstIndex: Int = position, lastIndex: Int = lastUpcomingPosition): Boolean {
        if (!isSequentialQueue)
            return false

        if (lastIndex == -1) return false
        return index in (firstIndex + 1)..lastIndex
    }

    /**
     * Keeps the status of the upcoming tracks consistent. Basically it removes the
     * value from those tracks that are out of range (which starts from the position
     * immediately following the current one, and ends at the last track that has
     * been added).
     *
     * It is valid to specify a different range depending on the need of the situation.
     */
    private fun realignUpcomingRange(firstIndex: Int = position, lastIndex: Int = lastUpcomingPosition) {
        if (!isSequentialQueue)
            return

        if (lastIndex == -1) return // there is nothing to realign
        if (lastIndex == firstIndex) {
            playingQueue[firstIndex].isUpcoming = false
            return
        }
        for ((i, song) in playingQueue.withIndex()) {
            song.isUpcoming = !(i <= firstIndex || i > lastIndex)
        }
    }

    /**
     * Remove all upcoming tracks. However, this does not actually remove the items
     * from the list, it just removes the [QueueSong.isUpcoming] value from them.
     */
    private fun removeAllRanges() {
        if (!isSequentialQueue)
            return

        playingQueue.forEach { it.isUpcoming = false }
    }

    fun setPositionTo(newPosition: Int) {
        val oldPosition = this.position
        val lastUpcomingPosition = this.lastUpcomingPosition
        this.position = newPosition
        if (newPosition < oldPosition || isInUpcomingRange(newPosition, firstIndex = oldPosition, lastIndex = lastUpcomingPosition)) {
            // First we check if the new position is further back than the
            // current one or further forward but within the allowed range.
            // If so, it will only be necessary to realign the upcoming tracks.
            realignUpcomingRange(firstIndex = newPosition, lastIndex = lastUpcomingPosition)
        } else if (newPosition > lastUpcomingPosition) {
            // In case the new position has exceeded the allowed range,
            // we discard it completely.
            removeAllRanges()
        }
    }

    fun setPositionToNext() {
        setPositionTo(nextPosition)
    }

    fun shuffleQueue(onCompleted: () -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val currentPosition = position
            val endPosition = playingQueue.lastIndex
            if (currentPosition == endPosition)
                return@launch

            // Check that there are at least two tracks to make a shuffle list
            if (endPosition - currentPosition >= 2) {
                playingQueue.subList(currentPosition + 1, endPosition + 1).shuffle()
                removeAllRanges()
            }

            withContext(Dispatchers.Main) {
                onCompleted()
            }
        }
    }

    fun clearQueue() {
        playingQueue.clear()
        originalPlayingQueue.clear()
    }

    fun setRepeatMode(mode: Int, onCompleted: () -> Unit) {
        this.repeatMode = mode

        sharedPreferences.edit {
            putInt(SAVED_REPEAT_MODE, repeatMode)
        }

        when (mode) {
            Playback.RepeatMode.OFF,
            Playback.RepeatMode.CURRENT,
            Playback.RepeatMode.ALL -> {
                onCompleted()
            }
        }
    }

    fun setShuffleMode(mode: Int, onCompleted: () -> Unit) {
        this.shuffleMode = mode

        sharedPreferences.edit {
            putInt(SAVED_SHUFFLE_MODE, shuffleMode)
        }

        when (mode) {
            Playback.ShuffleMode.ON -> {
                ShuffleHelper.makeShuffleList(playingQueue, position)
                position = 0
            }

            Playback.ShuffleMode.OFF -> {
                val currentSongId = currentSong.id
                playingQueue = ArrayList(originalPlayingQueue)
                var newPosition = 0
                for (song in playingQueue) {
                    if (song.id == currentSongId) {
                        newPosition = playingQueue.indexOf(song)
                    }
                }
                position = newPosition
            }
        }
        removeAllRanges()
        onCompleted()
    }

    internal fun saveQueues() {
        coroutineScope.launch(Dispatchers.IO) {
            PlaybackQueueStore.getInstance(musicService)
                .saveQueues(playingQueue, originalPlayingQueue)
        }
    }

    internal fun restoreState(onRestored: (positionInTrack: Int) -> Unit) {
        this.repeatMode = sharedPreferences.getInt(SAVED_REPEAT_MODE, Playback.RepeatMode.OFF)
        this.shuffleMode = sharedPreferences.getInt(SAVED_SHUFFLE_MODE, Playback.ShuffleMode.OFF)
        musicService.handleAndSendChangeInternal(ServiceEvent.REPEAT_MODE_CHANGED)
        musicService.handleAndSendChangeInternal(ServiceEvent.SHUFFLE_MODE_CHANGED)
        coroutineScope.launch {
            restoreQueuesAndPositionIfNecessary(onRestored)
        }
    }

    internal suspend fun restoreQueuesAndPositionIfNecessary(onRestored: (positionInTrack: Int) -> Unit) {
        if (!queuesRestored && playingQueue.isEmpty()) {
            withContext(Dispatchers.IO) {
                val restoredQueue = PlaybackQueueStore.getInstance(musicService).savedPlayingQueue
                val restoredOriginalQueue =
                    PlaybackQueueStore.getInstance(musicService).savedOriginalPlayingQueue
                val restoredPosition = sharedPreferences.getInt(SAVED_QUEUE_POSITION, -1)
                val restoredPositionInTrack = sharedPreferences.getInt(SAVED_POSITION_IN_TRACK, -1)
                if (restoredQueue.isNotEmpty() && restoredQueue.size == restoredOriginalQueue.size && restoredPosition != -1) {
                    originalPlayingQueue = ArrayList(restoredOriginalQueue.toQueueSongs())
                    playingQueue = ArrayList(restoredQueue.toQueueSongs())
                    position = restoredPosition
                    onRestored(restoredPositionInTrack)
                }
            }
            queuesRestored = true
        }
    }
}