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

package com.mardous.booming.adapters.artist

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.isGone
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.RequestManager
import com.mardous.booming.R
import com.mardous.booming.adapters.base.AbsMultiSelectAdapter
import com.mardous.booming.adapters.extension.isActivated
import com.mardous.booming.adapters.extension.isValidPosition
import com.mardous.booming.adapters.extension.setColors
import com.mardous.booming.extensions.glide.artistOptions
import com.mardous.booming.extensions.glide.asBitmapPalette
import com.mardous.booming.extensions.glide.getArtistGlideModel
import com.mardous.booming.extensions.glide.getDefaultGlideTransition
import com.mardous.booming.extensions.media.artistInfo
import com.mardous.booming.extensions.media.displayName
import com.mardous.booming.extensions.media.sectionName
import com.mardous.booming.glide.BoomingColoredTarget
import com.mardous.booming.helper.color.MediaNotificationProcessor
import com.mardous.booming.helper.menu.OnClickMenu
import com.mardous.booming.interfaces.IArtistCallback
import com.mardous.booming.model.Artist
import com.mardous.booming.util.Preferences
import me.zhanghai.android.fastscroll.PopupTextProvider
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

open class ArtistAdapter(
    protected val activity: FragmentActivity,
    protected val requestManager: RequestManager,
    dataSet: List<Artist>,
    @LayoutRes protected val itemLayoutRes: Int,
    protected val callback: IArtistCallback? = null,
) : AbsMultiSelectAdapter<ArtistAdapter.ViewHolder, Artist>(activity, R.menu.menu_media_selection),
    PopupTextProvider {

    private var albumArtistsOnly = false

    var dataSet by Delegates.observable(dataSet) { _: KProperty<*>, _: List<Artist>, _: List<Artist> ->
        albumArtistsOnly = Preferences.onlyAlbumArtists
        notifyDataSetChanged()
    }

    protected open fun createArtistHolder(view: View, viewType: Int): ViewHolder {
        return ViewHolder(view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(activity).inflate(itemLayoutRes, parent, false)
        return createArtistHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist: Artist = dataSet[position]
        val isChecked = isChecked(artist)
        holder.isActivated = isChecked
        holder.title?.text = getArtistTitle(artist)
        holder.text?.text = getArtistText(artist)
        val transitionName = if (albumArtistsOnly) artist.name else artist.id.toString()
        if (holder.imageContainer != null) {
            holder.imageContainer.transitionName = transitionName
        } else {
            holder.image?.transitionName = transitionName
        }
        loadArtistImage(artist, holder)
    }

    protected open fun loadArtistImage(artist: Artist, holder: ViewHolder) {
        if (holder.image != null) {
            requestManager.asBitmapPalette()
                .load(artist.getArtistGlideModel())
                .transition(getDefaultGlideTransition())
                .artistOptions(artist)
                .into(object : BoomingColoredTarget(holder.image) {
                    override fun onColorReady(colors: MediaNotificationProcessor) {
                        holder.setColors(colors)
                    }
                })
        }
    }

    private fun getArtistTitle(artist: Artist): String {
        return artist.displayName()
    }

    private fun getArtistText(artist: Artist): String {
        return artist.artistInfo(activity)
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].id
    }

    override fun getIdentifier(position: Int): Artist? {
        return dataSet[position]
    }

    override fun getName(item: Artist): String? {
        return item.displayName()
    }

    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Artist>) {
        callback?.artistsMenuItemClick(selection, menuItem)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        return dataSet[position].displayName().sectionName()
    }

    open inner class ViewHolder(itemView: View) : com.mardous.booming.adapters.base.MediaEntryViewHolder(itemView) {

        protected val artist: Artist
            get() = dataSet[layoutPosition]

        protected val sharedElements: Array<Pair<View, String>>?
            get() = if (imageContainer != null) {
                arrayOf(imageContainer to imageContainer.transitionName)
            } else if (image != null) {
                arrayOf(image to image.transitionName)
            } else {
                null
            }

        protected open fun onArtistMenuItemClick(item: MenuItem): Boolean {
            return callback?.artistMenuItemClick(artist, item, sharedElements) ?: false
        }

        override fun onClick(view: View) {
            if (!isValidPosition)
                return

            if (isInQuickSelectMode) {
                toggleChecked(layoutPosition)
            } else {
                callback?.artistClick(artist, sharedElements)
            }
        }

        override fun onLongClick(view: View): Boolean {
            //return isValidPosition && toggleChecked(layoutPosition)
            return false
        }
    }

    init {
        setHasStableIds(true)
    }
}