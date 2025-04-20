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

package com.mardous.booming.adapters.song

import android.annotation.SuppressLint
import android.view.*
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.RequestManager
import com.mardous.booming.R
import com.mardous.booming.activities.base.AbsSlidingMusicPanelActivity
import com.mardous.booming.adapters.base.AbsMultiSelectAdapter
import com.mardous.booming.adapters.base.MediaEntryViewHolder
import com.mardous.booming.adapters.extension.isActivated
import com.mardous.booming.adapters.extension.isValidPosition
import com.mardous.booming.adapters.extension.setColors
import com.mardous.booming.extensions.glide.asBitmapPalette
import com.mardous.booming.extensions.glide.getDefaultGlideTransition
import com.mardous.booming.extensions.glide.getSongGlideModel
import com.mardous.booming.extensions.glide.songOptions
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.extensions.media.sectionName
import com.mardous.booming.extensions.media.songInfo
import com.mardous.booming.extensions.utilities.buildInfoString
import com.mardous.booming.glide.BoomingColoredTarget
import com.mardous.booming.helper.color.MediaNotificationProcessor
import com.mardous.booming.helper.menu.OnClickMenu
import com.mardous.booming.interfaces.ISongCallback
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.Preferences
import com.mardous.booming.util.sort.SortKeys
import com.mardous.booming.util.sort.SortOrder
import me.zhanghai.android.fastscroll.PopupTextProvider
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

@SuppressLint("NotifyDataSetChanged")
@Suppress("LeakingThis")
open class SongAdapter(
    protected val activity: FragmentActivity,
    protected val requestManager: RequestManager?,
    dataSet: List<Song>,
    @LayoutRes protected val itemLayoutRes: Int = R.layout.item_list,
    private val sortOrder: SortOrder? = null,
    protected val callback: ISongCallback? = null,
) : AbsMultiSelectAdapter<SongAdapter.ViewHolder, Song>(activity, R.menu.menu_media_selection), PopupTextProvider {

    var dataSet by Delegates.observable(dataSet) { _: KProperty<*>, _: List<Song>, _: List<Song> ->
        notifyDataSetChanged()
    }

    protected open fun createViewHolder(view: View, viewType: Int): ViewHolder {
        return ViewHolder(view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(activity).inflate(itemLayoutRes, parent, false)
        return createViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song: Song = dataSet[position]
        val isChecked = isChecked(song)
        holder.isActivated = isChecked
        holder.title?.text = getSongTitle(song)
        holder.text?.text = getSongText(song)
        // Check if imageContainer exists, so we can have a smooth transition without
        // CardView clipping, if it doesn't exist in current layout set transition name to image instead.
        if (holder.imageContainer != null) {
            holder.imageContainer.transitionName = song.id.toString()
        } else {
            holder.image?.transitionName = song.id.toString()
        }
        loadAlbumCover(song, holder)
    }

    protected open fun loadAlbumCover(song: Song, holder: ViewHolder) {
        if (requestManager != null && holder.image != null) {
            requestManager.asBitmapPalette()
                .load(song.getSongGlideModel())
                .transition(getDefaultGlideTransition())
                .songOptions(song)
                .into(object : BoomingColoredTarget(holder.image) {
                    override fun onColorReady(colors: MediaNotificationProcessor) {
                        holder.setColors(colors)
                    }
                })
        }
    }

    private fun getSongTitle(song: Song): String {
        return song.title
    }

    protected open fun getSongText(song: Song): String? {
        if (sortOrder?.value == SortKeys.YEAR) {
            if (song.year > 0) {
                return buildInfoString(song.displayArtistName(), song.year.toString())
            }
            return song.displayArtistName()
        } else if (sortOrder?.value == SortKeys.ALBUM) {
            return buildInfoString(song.displayArtistName(), song.albumName)
        }
        return song.songInfo()
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].id
    }

    override fun getIdentifier(position: Int): Song? {
        return dataSet[position]
    }

    override fun getName(item: Song): String? {
        return item.title
    }

    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Song>) {
        callback?.songsMenuItemClick(selection, menuItem)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val song: Song = dataSet[position]
        return when (sortOrder?.value) {
            SortKeys.ALBUM -> song.albumName.sectionName()
            SortKeys.ARTIST -> song.displayArtistName().sectionName()
            SortKeys.AZ -> song.title.sectionName()
            SortKeys.YEAR -> ""
            else -> song.title.sectionName()
        }
    }

    open inner class ViewHolder(view: View) : MediaEntryViewHolder(view) {
        protected open val song: Song
            get() = dataSet[layoutPosition]

        @get:MenuRes
        protected open val songMenuRes: Int
            get() = R.menu.menu_item_song

        protected val sharedElements: Array<Pair<View, String>>?
            get() = if (image != null && image.isVisible) arrayOf(image to image.transitionName) else null

        @CallSuper
        protected open fun onPrepareSongMenu(menu: Menu) {
        }

        protected open fun onSongMenuItemClick(item: MenuItem): Boolean {
            return callback?.songMenuItemClick(song, item, sharedElements) ?: false
        }

        protected open fun onExpandNowPlayingPanel() {
            if (Preferences.openOnPlay) {
                (activity as? AbsSlidingMusicPanelActivity)?.expandPanel()
            }
        }

        override fun onClick(view: View) {
            if (!isValidPosition)
                return

            if (isInQuickSelectMode) {
                toggleChecked(layoutPosition)
            } else {
                MusicPlayer.openQueue(dataSet, layoutPosition)
                onExpandNowPlayingPanel()
            }
        }

        override fun onLongClick(view: View): Boolean {
            return false
        }

        init {

        }
    }

    init {
        setHasStableIds(true)
    }

}