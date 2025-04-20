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

package com.uniqtech.musicplayer.fragments.artists

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
import com.uniqtech.musicplayer.adapters.artist.ArtistAdapter
import com.uniqtech.musicplayer.extensions.navigation.artistDetailArgs
import com.uniqtech.musicplayer.extensions.navigation.asFragmentExtras
import com.uniqtech.musicplayer.fragments.ReloadType
import com.uniqtech.musicplayer.fragments.base.AbsRecyclerViewCustomGridSizeFragment
import com.uniqtech.musicplayer.helper.menu.onArtistMenu
import com.uniqtech.musicplayer.helper.menu.onArtistsMenu
import com.uniqtech.musicplayer.interfaces.IArtistCallback
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.GridViewType
import com.uniqtech.musicplayer.util.Preferences
import com.uniqtech.musicplayer.util.sort.SortOrder
import com.uniqtech.musicplayer.util.sort.selectedSortOrder

class ArtistListFragment : AbsRecyclerViewCustomGridSizeFragment<ArtistAdapter, GridLayoutManager>(),
    IArtistCallback {

    override val titleRes: Int = R.string.artists_label
    override val isShuffleVisible: Boolean = true
    override val emptyMessageRes: Int
        get() = R.string.no_artists_label

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        libraryViewModel.getArtists().observe(viewLifecycleOwner) { artists ->
            adapter?.dataSet = artists
        }
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.forceReload(ReloadType.Artists)
    }

    override fun createLayoutManager(): GridLayoutManager {
        return GridLayoutManager(activity, gridSize)
    }

    override fun createAdapter(): ArtistAdapter {
        val itemLayoutRes = itemLayoutRes
        notifyLayoutResChanged(itemLayoutRes)
        val dataSet: List<Artist> = if (adapter == null) ArrayList() else adapter!!.dataSet
        return ArtistAdapter(mainActivity, Glide.with(this), dataSet, itemLayoutRes, this)
    }

    override fun artistClick(artist: Artist, sharedElements: Array<Pair<View, String>>?) {
        findNavController().navigate(
            R.id.nav_artist_detail,
            artistDetailArgs(artist),
            null,
            sharedElements.asFragmentExtras()
        )
    }

    override fun artistMenuItemClick(
        artist: Artist,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        return artist.onArtistMenu(this, menuItem)
    }

    override fun artistsMenuItemClick(artists: List<Artist>, menuItem: MenuItem) {
        artists.onArtistsMenu(this, menuItem)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateMenu(menu, inflater)
//        val sortOrderSubmenu = menu.findItem(R.id.action_sort_order)?.subMenu
//        if (sortOrderSubmenu != null) {
//            sortOrderSubmenu.clear()
//            sortOrderSubmenu.add(0, R.id.action_sort_order_az, 0, R.string.sort_order_az)
//            sortOrderSubmenu.add(0, R.id.action_sort_order_number_of_songs, 1, R.string.sort_order_number_of_songs)
//            sortOrderSubmenu.add(0, R.id.action_sort_order_number_of_albums, 2, R.string.sort_order_number_of_albums)
//            sortOrderSubmenu.add(1, R.id.action_sort_order_descending, 4, R.string.sort_order_descending)
//            sortOrderSubmenu.setGroupCheckable(0, true, true)
//            sortOrderSubmenu.prepareSortOrder(SortOrder.artistSortOrder)
//        }
//        menu.add(0, R.id.action_album_artist, 0, R.string.show_album_artists).apply {
//            isCheckable = true
//            isChecked = Preferences.onlyAlbumArtists
//        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_album_artist) {
            item.isChecked = !item.isChecked
            Preferences.onlyAlbumArtists = item.isChecked
            libraryViewModel.forceReload(ReloadType.Artists)
            return true
        }
        if (item.selectedSortOrder(SortOrder.artistSortOrder)) {
            libraryViewModel.forceReload(ReloadType.Artists)
            return true
        }
        return super.onMenuItemSelected(item)
    }

    override fun getSavedViewType(): GridViewType {
        return GridViewType.entries.firstOrNull {
            it.name == sharedPreferences.getString(VIEW_TYPE, null)
        } ?: GridViewType.Circle
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

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        libraryViewModel.forceReload(ReloadType.Artists)
    }

    override fun onPause() {
        super.onPause()
        adapter?.actionMode?.finish()
    }

    companion object {
        private const val VIEW_TYPE = "artists_view_type"
        private const val GRID_SIZE = "artists_grid_size"
    }
}