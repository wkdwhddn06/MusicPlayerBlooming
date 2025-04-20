/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
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
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.activities.MainActivity
import com.uniqtech.musicplayer.appwidgets.base.BaseAppWidget
import com.uniqtech.musicplayer.extensions.glide.getSongGlideModel
import com.uniqtech.musicplayer.extensions.glide.songOptions
import com.uniqtech.musicplayer.extensions.media.displayArtistName
import com.uniqtech.musicplayer.extensions.resources.getDrawableCompat
import com.uniqtech.musicplayer.extensions.toHtml
import com.uniqtech.musicplayer.service.MusicService
import com.uniqtech.musicplayer.service.constants.ServiceAction

class AppWidgetSmall : BaseAppWidget() {
    private var target: Target<Bitmap>? = null // for cancellation

    /**
     * Initialize given widgets to default state, where we launch Music on default click and hide
     * actions if service not running.
     */
    override fun defaultAppWidget(context: Context, appWidgetIds: IntArray) {
        val appWidgetView = RemoteViews(context.packageName, R.layout.app_widget_small)

        val innerRadius = getInnerRadius(context)
        val imageSize = getImageSize(context)
        appWidgetView.setImageViewBitmap(
            R.id.image,
            createRoundedBitmap(
                context.getDrawableCompat(R.drawable.default_audio_art),
                imageSize, imageSize, innerRadius, innerRadius, innerRadius, innerRadius
            )
        )

        linkButtons(context, appWidgetView)
        pushUpdate(context, appWidgetIds, appWidgetView)
    }

    /**
     * Update all active widget instances by pushing changes
     */
    override fun performUpdate(service: MusicService, appWidgetIds: IntArray?) {
        val appWidgetView = RemoteViews(service.packageName, R.layout.app_widget_small)

        val isPlaying = service.isPlaying
        val song = service.getCurrentSong()

        // Set the titles and artwork
        if (song.title.isEmpty() && song.artistName.isEmpty()) {
            appWidgetView.setViewVisibility(R.id.media_titles, View.INVISIBLE)
        } else {
            appWidgetView.setViewVisibility(R.id.media_titles, View.VISIBLE)
            appWidgetView.setTextViewText(R.id.title, "<b>${song.title}</b> - ${song.displayArtistName()}".toHtml())
        }

        // Set correct drawable for pause state
        val playPauseRes = if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
        appWidgetView.setImageViewResource(R.id.button_toggle_play_pause, playPauseRes)
        // Set prev/next button drawables
        appWidgetView.setImageViewResource(R.id.button_next, R.drawable.ic_next_24dp)
        appWidgetView.setImageViewResource(R.id.button_prev, R.drawable.ic_previous_24dp)

        // Link actions buttons to intents
        linkButtons(service, appWidgetView)

        // Load the album cover async and push the update on completion
        val imageSize = getImageSize(service)
        service.runOnUiThread {
            if (target != null) {
                Glide.with(service).clear(target)
            }
            target = Glide.with(service)
                .asBitmap()
                .songOptions(song)
                .load(song.getSongGlideModel())
                .centerCrop()
                .into(object : CustomTarget<Bitmap>(imageSize, imageSize) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        update(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        update(null)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}

                    private fun update(bitmap: Bitmap?) {
                        val image = getAlbumArtDrawable(service, bitmap)
                        val innerRadius = getInnerRadius(service)
                        val roundedBitmap = createRoundedBitmap(
                            image,
                            imageSize,
                            imageSize,
                            innerRadius,
                            innerRadius,
                            innerRadius,
                            innerRadius
                        )
                        appWidgetView.setImageViewBitmap(R.id.image, roundedBitmap)

                        pushUpdate(service, appWidgetIds, appWidgetView)
                    }
                })
        }
    }

    /**
     * Link up various button actions using [PendingIntent].
     */
    private fun linkButtons(context: Context, views: RemoteViews) {
        val action = Intent(context, MainActivity::class.java)
        val serviceName = ComponentName(context, MusicService::class.java)

        // Home
        action.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        var pendingIntent = PendingIntent.getActivity(context, 0, action, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.image, pendingIntent)
        views.setOnClickPendingIntent(R.id.media_titles, pendingIntent)

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

    private fun getImageSize(context: Context): Int {
        if (imageSize == 0) {
            imageSize = context.resources.getDimensionPixelSize(R.dimen.app_widget_small_image_size)
        }
        return imageSize
    }

    companion object {

        const val NAME = "app_widget_small"

        private var imageSize = 0
        private var mInstance: AppWidgetSmall? = null

        val instance: AppWidgetSmall
            @Synchronized get() {
                if (mInstance == null) {
                    mInstance = AppWidgetSmall()
                }
                return mInstance!!
            }
    }
}
