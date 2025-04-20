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

package com.uniqtech.musicplayer.service.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.createNotificationChannel
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicService
import com.uniqtech.musicplayer.util.NotificationExtraText
import com.uniqtech.musicplayer.util.NotificationPriority
import com.uniqtech.musicplayer.util.Preferences

abstract class PlayingNotification(protected val context: Context) :
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID) {

    abstract fun update(song: Song, isPlaying: Boolean, onUpdate: () -> Unit)

    @Synchronized
    protected fun getExtraTextString(song: Song): String? {
        if (context is MusicService) {
            return when (Preferences.notificationExtraTextLine) {
                NotificationExtraText.ALBUM_NAME -> song.albumName
                NotificationExtraText.ALBUM_AND_YEAR -> buildInfoString(song.albumName, song.year.toString())
                NotificationExtraText.ALBUM_ARTIST_NAME -> song.albumArtistName
                NotificationExtraText.NEXT_SONG -> context.getQueueInfo(context)
                else -> song.albumName
            }
        }
        return null
    }

    @get:Synchronized
    protected val notificationPriority: Int
        get() = when (Preferences.notificationPriority) {
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            NotificationPriority.MAXIMUM -> NotificationCompat.PRIORITY_MAX
            else -> NotificationCompat.PRIORITY_MAX
        }

    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "playing_notification"

        fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
            context.createNotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.playing_notification_name),
                context.getString(R.string.playing_notification_description),
                notificationManager
            )
        }
    }
}