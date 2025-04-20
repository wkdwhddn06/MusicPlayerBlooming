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

import android.view.View
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemState
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemViewHolder
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.draggable.annotation.DraggableItemStateFlags
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.database.PlaylistEntity
import com.uniqtech.musicplayer.database.toSongsEntity
import com.uniqtech.musicplayer.extensions.resources.hitTest
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import com.uniqtech.musicplayer.interfaces.ISongCallback
import com.uniqtech.musicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlaylistSongAdapter(
    activity: FragmentActivity,
    requestManager: RequestManager,
    dataSet: List<Song>,
    @LayoutRes
    itemLayoutRes: Int,
    callback: ISongCallback? = null
) : SongAdapter(activity, requestManager, dataSet, itemLayoutRes, null, callback),
    DraggableItemAdapter<PlaylistSongAdapter.ViewHolder> {

    private val libraryViewModel: LibraryViewModel by activity.viewModel()
    private val mutableDataSet: MutableList<Song>
        get() = dataSet as MutableList

    override fun createViewHolder(view: View, viewType: Int): SongAdapter.ViewHolder {
        return ViewHolder(view)
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].id
    }

    override fun onCheckCanStartDrag(holder: ViewHolder, position: Int, x: Int, y: Int): Boolean {
        if (isInQuickSelectMode) {
            return false
        }
        return (holder.dragView?.hitTest(x, y) ?: false)
    }

    override fun onGetItemDraggableRange(holder: ViewHolder, position: Int): ItemDraggableRange? {
        return null
    }

    override fun onMoveItem(fromPosition: Int, toPosition: Int) {
        mutableDataSet.add(toPosition, mutableDataSet.removeAt(fromPosition))
    }

    override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean {
        return true
    }

    override fun onItemDragStarted(position: Int) {
        notifyDataSetChanged()
    }

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        notifyDataSetChanged()
    }

    fun saveSongs(playlistEntity: PlaylistEntity) {
        if (playlistEntity == PlaylistEntity.Empty)
            return

        activity.lifecycleScope.launch(Dispatchers.IO) {
            libraryViewModel.insertSongs(dataSet.toSongsEntity(playlistEntity))
        }
    }

    interface OnMoveItemListener {
        fun onMoveItem(fromPosition: Int, toPosition: Int)

        fun onMoveItemFinished()
    }

    inner class ViewHolder internal constructor(itemView: View) : SongAdapter.ViewHolder(itemView),
        DraggableItemViewHolder {

        private val mDragItemState: DraggableItemState

        override val songMenuRes: Int
            get() = R.menu.menu_item_playlist_song

        override fun getDragState(): DraggableItemState {
            return mDragItemState
        }

        @DraggableItemStateFlags
        override fun getDragStateFlags(): Int {
            return mDragItemState.flags
        }

        override fun setDragStateFlags(flags: Int) {
            mDragItemState.flags = flags
        }

        init {
            dragView?.isVisible = true
            mDragItemState = DraggableItemState()
        }
    }

    init {
        this.menuRes = R.menu.menu_playlist_songs_selection
    }
}