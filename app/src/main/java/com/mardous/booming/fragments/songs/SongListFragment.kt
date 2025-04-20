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

package com.mardous.booming.fragments.songs

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.edit
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.mardous.booming.R
import com.mardous.booming.adapters.song.SongAdapter
import com.mardous.booming.fragments.ReloadType
import com.mardous.booming.fragments.base.AbsRecyclerViewCustomGridSizeFragment
import com.mardous.booming.helper.menu.onSongMenu
import com.mardous.booming.helper.menu.onSongsMenu
import com.mardous.booming.interfaces.ISongCallback
import com.mardous.booming.model.GridViewType
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.sort.SortOrder
import com.mardous.booming.util.sort.prepareSortOrder
import com.mardous.booming.util.sort.selectedSortOrder

/**
 * @author Christians M. A. (mardous)
 */
class SongListFragment : AbsRecyclerViewCustomGridSizeFragment<SongAdapter, GridLayoutManager>(), ISongCallback {

    override val titleRes: Int = R.string.songs_label
    override val isShuffleVisible: Boolean = true
    override val emptyMessageRes: Int
        get() = R.string.no_songs_label

    override val defaultGridSize: Int
        get() = if (isLandscape) resources.getInteger(R.integer.default_list_columns_land)
        else resources.getInteger(R.integer.default_list_columns)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        libraryViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            adapter?.dataSet = songs
        }
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.forceReload(ReloadType.Songs)
    }

    override fun onPause() {
        super.onPause()
        adapter?.actionMode?.finish()
    }

    override fun createLayoutManager(): GridLayoutManager {
        return GridLayoutManager(activity, gridSize)
    }

    override fun createAdapter(): SongAdapter {
        notifyLayoutResChanged(itemLayoutRes)
        val dataSet = adapter?.dataSet ?: ArrayList()
        return SongAdapter(mainActivity, Glide.with(this), dataSet, itemLayoutRes, SortOrder.songSortOrder, this)
    }

    override fun songMenuItemClick(
        song: Song,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        return song.onSongMenu(this, menuItem, sharedElements)
    }

    override fun songsMenuItemClick(songs: List<Song>, menuItem: MenuItem) {
        songs.onSongsMenu(this, menuItem)
    }

    override fun getSavedViewType(): GridViewType {
        return GridViewType.entries.firstOrNull {
            it.name == sharedPreferences.getString(VIEW_TYPE, null)
        } ?: GridViewType.Normal
    }

    override fun saveViewType(viewType: GridViewType) {
        sharedPreferences.edit { putString(VIEW_TYPE, viewType.name) }
    }

    override fun getSavedGridSize(): Int {
        return sharedPreferences.getInt(GRID_SIZE, defaultGridSize)
    }

    override fun saveGridSize(newGridSize: Int) {
        sharedPreferences.edit { putInt(GRID_SIZE, newGridSize) }
    }

    override fun onGridSizeChanged(isLand: Boolean, gridColumns: Int) {
        layoutManager?.spanCount = gridColumns
        adapter?.notifyDataSetChanged()
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateMenu(menu, inflater)
        val sortOrderSubmenu = menu.findItem(R.id.action_sort_order)?.subMenu
        if (sortOrderSubmenu != null) {
            sortOrderSubmenu.clear()
            sortOrderSubmenu.add(0, R.id.action_sort_order_az, 0, R.string.sort_order_az)
            sortOrderSubmenu.add(0, R.id.action_sort_order_artist, 1, R.string.sort_order_artist)
            sortOrderSubmenu.add(0, R.id.action_sort_order_album, 2, R.string.sort_order_album)
            sortOrderSubmenu.add(0, R.id.action_sort_order_duration, 3, R.string.sort_order_duration)
            sortOrderSubmenu.add(0, R.id.action_sort_order_year, 4, R.string.sort_order_year)
            sortOrderSubmenu.add(0, R.id.action_sort_order_date_added, 5, R.string.sort_order_date_added)
            sortOrderSubmenu.add(1, R.id.action_sort_order_descending, 7, R.string.sort_order_descending)
            sortOrderSubmenu.setGroupCheckable(0, true, true)
            sortOrderSubmenu.prepareSortOrder(SortOrder.songSortOrder)
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.selectedSortOrder(SortOrder.songSortOrder)) {
            libraryViewModel.forceReload(ReloadType.Songs)
            return true
        }
        return super.onMenuItemSelected(item)
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        libraryViewModel.forceReload(ReloadType.Songs)
    }

    companion object {
        private const val VIEW_TYPE = "songs_view_type"
        private const val GRID_SIZE = "songs_grid_size"
    }
}