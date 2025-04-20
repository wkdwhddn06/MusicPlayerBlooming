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

package com.uniqtech.musicplayer.fragments.albums

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.album.AlbumAdapter
import com.uniqtech.musicplayer.extensions.navigation.albumDetailArgs
import com.uniqtech.musicplayer.extensions.navigation.asFragmentExtras
import com.uniqtech.musicplayer.fragments.ReloadType
import com.uniqtech.musicplayer.fragments.base.AbsRecyclerViewCustomGridSizeFragment
import com.uniqtech.musicplayer.helper.menu.onAlbumMenu
import com.uniqtech.musicplayer.helper.menu.onAlbumsMenu
import com.uniqtech.musicplayer.interfaces.IAlbumCallback
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.GridViewType
import com.uniqtech.musicplayer.util.sort.SortOrder
import com.uniqtech.musicplayer.util.sort.selectedSortOrder

class AlbumListFragment : AbsRecyclerViewCustomGridSizeFragment<AlbumAdapter, GridLayoutManager>(),
    IAlbumCallback {

    override val titleRes: Int = R.string.albums_label
    override val isShuffleVisible: Boolean = true
    override val emptyMessageRes: Int
        get() = R.string.no_albums_label

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        libraryViewModel.getAlbums().observe(viewLifecycleOwner) { albums ->
            adapter?.dataSet = albums
        }
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.forceReload(ReloadType.Albums)
    }

    override fun onPause() {
        super.onPause()
        adapter?.actionMode?.finish()
    }

    override fun createLayoutManager(): GridLayoutManager {
        return GridLayoutManager(activity, gridSize)
    }

    override fun createAdapter(): AlbumAdapter {
        val itemLayoutRes = itemLayoutRes
        notifyLayoutResChanged(itemLayoutRes)
        val dataSet: List<Album> = adapter?.dataSet ?: ArrayList()
        return AlbumAdapter(
            mainActivity,
            Glide.with(this),
            dataSet,
            itemLayoutRes,
            SortOrder.albumSortOrder,
            this
        )
    }

    override fun albumClick(album: Album, sharedElements: Array<Pair<View, String>>?) {
        findNavController().navigate(
            R.id.nav_album_detail,
            albumDetailArgs(album.id),
            null,
            sharedElements.asFragmentExtras()
        )
    }

    override fun albumMenuItemClick(
        album: Album,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        return album.onAlbumMenu(this, menuItem)
    }

    override fun albumsMenuItemClick(albums: List<Album>, menuItem: MenuItem) {
        albums.onAlbumsMenu(this, menuItem)
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
//        val sortOrderSubmenu = menu.findItem(R.id.action_sort_order)?.subMenu
//        if (sortOrderSubmenu != null) {
//            sortOrderSubmenu.clear()
//            sortOrderSubmenu.add(0, R.id.action_sort_order_az, 0, R.string.sort_order_az)
//            sortOrderSubmenu.add(0, R.id.action_sort_order_artist, 1, R.string.sort_order_artist)
//            sortOrderSubmenu.add(0, R.id.action_sort_order_year, 2, R.string.sort_order_year)
//            sortOrderSubmenu.add(0, R.id.action_sort_order_number_of_songs, 3, R.string.sort_order_number_of_songs)
//            sortOrderSubmenu.add(1, R.id.action_sort_order_descending, 5, R.string.sort_order_descending)
//            sortOrderSubmenu.setGroupCheckable(0, true, true)
//            sortOrderSubmenu.prepareSortOrder(SortOrder.albumSortOrder)
//        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.selectedSortOrder(SortOrder.albumSortOrder)) {
            libraryViewModel.forceReload(ReloadType.Albums)
            return true
        }
        return super.onMenuItemSelected(item)
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        libraryViewModel.forceReload(ReloadType.Albums)
    }

    companion object {
        private const val VIEW_TYPE = "albums_view_type"
        private const val GRID_SIZE = "albums_grid_size"
    }
}