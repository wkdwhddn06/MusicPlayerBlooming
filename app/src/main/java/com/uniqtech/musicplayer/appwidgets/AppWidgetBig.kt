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

package com.uniqtech.musicplayer.appwidgets

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.activities.MainActivity
import com.uniqtech.musicplayer.appwidgets.base.BaseAppWidget
import com.uniqtech.musicplayer.extensions.getScreenSize
import com.uniqtech.musicplayer.extensions.getTintedDrawable
import com.uniqtech.musicplayer.extensions.glide.getDefaultGlideTransition
import com.uniqtech.musicplayer.extensions.glide.getSongGlideModel
import com.uniqtech.musicplayer.extensions.glide.songOptions
import com.uniqtech.musicplayer.extensions.resources.getPrimaryTextColor
import com.uniqtech.musicplayer.extensions.resources.toBitmap
import com.uniqtech.musicplayer.glide.BoomingSimpleTarget
import com.uniqtech.musicplayer.service.MusicService
import com.uniqtech.musicplayer.service.constants.ServiceAction

class AppWidgetBig : BaseAppWidget() {
    // for cancellation
    private var target: Target<Bitmap>? = null

    /**
     * Initialize given widgets to default state, where we launch Music on
     * default click and hide actions if service not running.
     */
    override fun defaultAppWidget(context: Context, appWidgetIds: IntArray) {
        val appWidgetView = RemoteViews(context.packageName, R.layout.app_widget_big)
        val primaryTextColor = getPrimaryTextColor(context, isDark = false)

        appWidgetView.setViewVisibility(R.id.media_titles, View.INVISIBLE)
        appWidgetView.setImageViewResource(R.id.image, R.drawable.default_audio_art)
        appWidgetView.setImageViewBitmap(
            R.id.button_next,
            context.getTintedDrawable(R.drawable.ic_next_24dp, primaryTextColor)!!.toBitmap()
        )
        appWidgetView.setImageViewBitmap(
            R.id.button_prev,
            context.getTintedDrawable(R.drawable.ic_previous_24dp, primaryTextColor)!!.toBitmap()
        )
        appWidgetView.setImageViewBitmap(
            R.id.button_toggle_play_pause,
            context.getTintedDrawable(R.drawable.ic_play_32dp, primaryTextColor)!!.toBitmap()
        )

        linkButtons(context, appWidgetView)
        pushUpdate(context, appWidgetIds, appWidgetView)
    }

    /**
     * Update all active widget instances by pushing changes
     */
    override fun performUpdate(service: MusicService, appWidgetIds: IntArray?) {
        val appWidgetView = RemoteViews(service.packageName, R.layout.app_widget_big)
        val primaryTextColor = getPrimaryTextColor(service, isDark = false)

        val isPlaying = service.isPlaying
        val song = service.getCurrentSong()

        // Set the titles and artwork
        if (song.title.isEmpty() && song.artistName.isEmpty()) {
            appWidgetView.setViewVisibility(R.id.media_titles, View.INVISIBLE)
        } else {
            appWidgetView.setViewVisibility(R.id.media_titles, View.VISIBLE)
            appWidgetView.setTextViewText(R.id.title, song.title)
            appWidgetView.setTextViewText(R.id.text, getSongArtistAndAlbum(song))
        }

        // Set correct drawable for pause state
        val playPauseRes = if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_32dp
        appWidgetView.setImageViewBitmap(
            R.id.button_toggle_play_pause,
            service.getTintedDrawable(playPauseRes, primaryTextColor)!!.toBitmap()
        )

        // Set prev/next button drawables
        appWidgetView.setImageViewBitmap(
            R.id.button_next,
            service.getTintedDrawable(R.drawable.ic_next_24dp, primaryTextColor)!!.toBitmap()
        )
        appWidgetView.setImageViewBitmap(
            R.id.button_prev,
            service.getTintedDrawable(R.drawable.ic_previous_24dp, primaryTextColor)!!.toBitmap()
        )

        // Link actions buttons to intents
        linkButtons(service, appWidgetView)

        // Load the album cover async and push the update on completion
        val p = service.getScreenSize()
        val widgetImageSize = p.x.coerceAtMost(p.y)

        val appContext = service.applicationContext
        service.runOnUiThread {
            if (target != null) {
                Glide.with(appContext).clear(target)
            }
            target = Glide.with(appContext)
                .asBitmap()
                .load(song.getSongGlideModel())
                .transition(getDefaultGlideTransition())
                .songOptions(song)
                .into(object : BoomingSimpleTarget<Bitmap>(widgetImageSize, widgetImageSize) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        update(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        update(null)
                    }

                    private fun update(bitmap: Bitmap?) {
                        if (bitmap == null) {
                            appWidgetView.setImageViewResource(R.id.image, R.drawable.default_audio_art)
                        } else {
                            appWidgetView.setImageViewBitmap(R.id.image, bitmap)
                        }
                        pushUpdate(appContext, appWidgetIds, appWidgetView)
                    }
                })
        }
    }

    /**
     * Link up various button actions using [PendingIntent].
     */
    private fun linkButtons(context: Context, views: RemoteViews) {
        val serviceName = ComponentName(context, MusicService::class.java)

        // Home
        val action = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        var pendingIntent: PendingIntent? = PendingIntent.getActivity(context, 0, action, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.clickable_area, pendingIntent)

        // Previous track
        pendingIntent = buildPendingIntent(context, ServiceAction.ACTION_PREVIOUS, serviceName)
        views.setOnClickPendingIntent(R.id.button_prev, pendingIntent)

        // Play and pause
        pendingIntent = buildPendingIntent(context, ServiceAction.ACTION_TOGGLE_PAUSE, serviceName)
        views.setOnClickPendingIntent(R.id.button_toggle_play_pause, pendingIntent)

        // Next track
        pendingIntent = buildPendingIntent(context, ServiceAction.ACTION_NEXT, serviceName)
        views.setOnClickPendingIntent(R.id.button_next, pendingIntent)
    }

    companion object {
        const val NAME = "app_widget_big"
        private var mInstance: AppWidgetBig? = null

        val instance: AppWidgetBig
            @Synchronized get() {
                if (mInstance == null) {
                    mInstance = AppWidgetBig()
                }
                return mInstance!!
            }
    }
}