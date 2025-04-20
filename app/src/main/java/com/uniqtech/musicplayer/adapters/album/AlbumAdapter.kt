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

package com.uniqtech.musicplayer.adapters.album

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.RequestManager
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.base.AbsMultiSelectAdapter
import com.uniqtech.musicplayer.adapters.extension.isActivated
import com.uniqtech.musicplayer.adapters.extension.setColors
import com.uniqtech.musicplayer.extensions.glide.albumOptions
import com.uniqtech.musicplayer.extensions.glide.asBitmapPalette
import com.uniqtech.musicplayer.extensions.glide.getAlbumGlideModel
import com.uniqtech.musicplayer.extensions.glide.getDefaultGlideTransition
import com.uniqtech.musicplayer.extensions.media.albumInfo
import com.uniqtech.musicplayer.extensions.media.displayArtistName
import com.uniqtech.musicplayer.extensions.media.sectionName
import com.uniqtech.musicplayer.extensions.media.songCountStr
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.glide.BoomingColoredTarget
import com.uniqtech.musicplayer.helper.color.MediaNotificationProcessor
import com.uniqtech.musicplayer.interfaces.IAlbumCallback
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.util.sort.SortKeys
import com.uniqtech.musicplayer.util.sort.SortOrder
import me.zhanghai.android.fastscroll.PopupTextProvider
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

open class AlbumAdapter(
    protected val activity: FragmentActivity,
    protected val requestManager: RequestManager,
    dataSet: List<Album>,
    @LayoutRes
    protected val itemLayoutRes: Int,
    protected val sortOrder: SortOrder? = null,
    protected val callback: IAlbumCallback? = null,
) : AbsMultiSelectAdapter<AlbumAdapter.ViewHolder, Album>(activity, R.menu.menu_media_selection),
    PopupTextProvider {

    var dataSet by Delegates.observable(dataSet) { _: KProperty<*>, _: List<Album>, _: List<Album> ->
        notifyDataSetChanged()
    }

    protected open fun createViewHolder(view: View, viewType: Int): ViewHolder {
        return ViewHolder(view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
        return createViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album: Album = dataSet[position]
        val isChecked = isChecked(album)
        holder.isActivated = isChecked
        holder.title?.text = getAlbumTitle(album)
        holder.text?.text = getAlbumText(album)
        // Check if imageContainer exists, so we can have a smooth transition without
        // CardView clipping, if it doesn't exist in current layout set transition name to image instead.
        if (holder.imageContainer != null) {
            holder.imageContainer.transitionName = album.id.toString()
        } else {
            holder.image?.transitionName = album.id.toString()
        }
        loadAlbumCover(album, holder)
    }

    protected open fun loadAlbumCover(album: Album, holder: ViewHolder) {
        if (holder.image != null) {
            requestManager.asBitmapPalette()
                .load(album.getAlbumGlideModel())
                .transition(getDefaultGlideTransition())
                .albumOptions(album)
                .into(object : BoomingColoredTarget(holder.image) {
                    override fun onColorReady(colors: MediaNotificationProcessor) {
                        holder.setColors(colors)
                    }
                })
        }
    }

    private fun getAlbumTitle(album: Album): String {
        return album.name
    }

    protected open fun getAlbumText(album: Album): String? {
        if (sortOrder?.value == SortKeys.NUMBER_OF_SONGS) {
            return buildInfoString(album.displayArtistName(), album.songCountStr(activity))
        }
        return album.albumInfo()
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].id
    }

    override fun getIdentifier(position: Int): Album? {
        return dataSet[position]
    }

    override fun getName(item: Album): String? {
        return item.name
    }

    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Album>) {
        callback?.albumsMenuItemClick(selection, menuItem)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val album: Album = dataSet[position]
        return when (sortOrder?.value) {
            SortKeys.ARTIST -> album.displayArtistName().sectionName()
            SortKeys.AZ -> album.name.sectionName()
            else -> album.name.sectionName()
        }
    }

    open inner class ViewHolder(itemView: View) : com.uniqtech.musicplayer.adapters.base.MediaEntryViewHolder(itemView) {
        protected open val album: Album
            get() = dataSet[layoutPosition]

        protected val sharedElements: Array<Pair<View, String>>?
            get() = if (imageContainer != null) {
                arrayOf(imageContainer to imageContainer.transitionName)
            } else if (image != null) {
                arrayOf(image to image.transitionName)
            } else {
                null
            }

        override fun onClick(view: View) {
            if (isInQuickSelectMode) {
                toggleChecked(layoutPosition)
            } else {
                callback?.albumClick(album, sharedElements)
            }
        }

        override fun onLongClick(view: View): Boolean {
            //toggleChecked(layoutPosition)
            return false
        }
    }

    init {
        setHasStableIds(true)
    }
}