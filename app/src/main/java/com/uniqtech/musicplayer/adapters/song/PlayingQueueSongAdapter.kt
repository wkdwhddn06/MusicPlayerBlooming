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

package com.uniqtech.musicplayer.adapters.song

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.RequestManager
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemState
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemViewHolder
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.resources.hitTest
import com.uniqtech.musicplayer.extensions.utilities.isInRange
import com.uniqtech.musicplayer.interfaces.ISongCallback
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicPlayer
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class PlayingQueueSongAdapter(
    activity: FragmentActivity,
    requestManager: RequestManager,
    dataSet: List<Song>,
    callback: Callback? = null,
    current: Int
) : SongAdapter(activity, requestManager, dataSet, R.layout.item_list_draggable, callback = callback),
    DraggableItemAdapter<PlayingQueueSongAdapter.ViewHolder> {

    interface Callback : ISongCallback {
        fun onRemoveSong(song: Song, fromPosition: Int)
    }

    var current: Int by Delegates.observable(current) { _: KProperty<*>, _: Int, _: Int ->
        notifyDataSetChanged()
    }

    override fun createViewHolder(view: View, viewType: Int): SongAdapter.ViewHolder {
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongAdapter.ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        if (holder.itemViewType == HISTORY || holder.itemViewType == CURRENT) {
            setAlpha(holder, 0.5f)
        }
    }

    fun swapDataSet(dataSet: List<Song>, position: Int) {
        this.current = position
        this.dataSet = dataSet
    }

    private fun setAlpha(holder: SongAdapter.ViewHolder, alpha: Float) {
        holder.image?.alpha = alpha
        holder.title?.alpha = alpha
        holder.text?.alpha = alpha
        holder.paletteColorContainer?.alpha = alpha
        holder.dragView?.alpha = alpha
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        return ""
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].id
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position < current -> HISTORY
            position > current -> UP_NEXT
            else -> CURRENT
        }
    }

    override fun onCheckCanStartDrag(holder: ViewHolder, position: Int, x: Int, y: Int): Boolean {
        return holder.dragView?.hitTest(x, y) ?: false
    }

    override fun onGetItemDraggableRange(holder: ViewHolder, position: Int): ItemDraggableRange? {
        return null
    }

    override fun onMoveItem(from: Int, to: Int) {
        MusicPlayer.moveSong(from, to)
    }

    override fun onCheckCanDrop(p1: Int, p2: Int): Boolean {
        return true
    }

    override fun onItemDragStarted(position: Int) {
        notifyDataSetChanged()
    }

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        notifyDataSetChanged()
    }

    companion object {
        private const val HISTORY = 0
        private const val CURRENT = 1
        private const val UP_NEXT = 2
    }

    inner class ViewHolder internal constructor(itemView: View) : SongAdapter.ViewHolder(itemView),
        DraggableItemViewHolder {

        private val mDraggableItemState = DraggableItemState()

        override fun onClick(view: View) {
            val songPosition = layoutPosition
            if (songPosition.isInRange(0, MusicPlayer.playingQueue.size)) {
                if (songPosition != current) {
                    MusicPlayer.playSongAt(songPosition)
                }
            }
        }

        override fun onLongClick(view: View): Boolean {
            return false
        }

        override fun onPrepareSongMenu(menu: Menu) {
            super.onPrepareSongMenu(menu)
            menu.findItem(R.id.action_put_after_current_track)?.let { menuItem ->
                menuItem.isEnabled = layoutPosition > current + 1
            }
            menu.findItem(R.id.action_stop_after_track)?.let { menuItem ->
                menuItem.isEnabled = layoutPosition >= current
            }
        }

        override fun onSongMenuItemClick(item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_remove_from_playing_queue -> {
                    if (callback is Callback) {
                        callback.onRemoveSong(song, layoutPosition)
                    } else {
                        MusicPlayer.removeFromQueue(layoutPosition)
                    }
                    true
                }

                R.id.action_stop_after_track -> {
                    MusicPlayer.setStopPosition(layoutPosition)
                    true
                }

                R.id.action_put_after_current_track -> {
                    MusicPlayer.moveToNextPosition(layoutPosition)
                    true
                }

                else -> super.onSongMenuItemClick(item)
            }
        }

        override fun getDragState(): DraggableItemState {
            return mDraggableItemState
        }

        override fun getDragStateFlags(): Int {
            return mDraggableItemState.flags
        }

        override fun setDragStateFlags(flags: Int) {
            mDraggableItemState.flags = flags
        }

        override val songMenuRes: Int
            get() = R.menu.menu_item_playing_queue_song

        init {
            dragView?.visibility = View.VISIBLE
        }
    }

}