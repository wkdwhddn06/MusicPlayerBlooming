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

package com.uniqtech.musicplayer.fragments.genres

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.GenreAdapter
import com.uniqtech.musicplayer.extensions.navigation.genreDetailArgs
import com.uniqtech.musicplayer.fragments.ReloadType
import com.uniqtech.musicplayer.fragments.base.AbsRecyclerViewCustomGridSizeFragment
import com.uniqtech.musicplayer.interfaces.IGenreCallback
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.model.GridViewType
import com.uniqtech.musicplayer.util.sort.SortOrder
import com.uniqtech.musicplayer.util.sort.selectedSortOrder

class GenresListFragment : AbsRecyclerViewCustomGridSizeFragment<GenreAdapter, GridLayoutManager>(),
    IGenreCallback {

    override val titleRes: Int = R.string.genres_label
    override val isShuffleVisible: Boolean = false
    override val emptyMessageRes: Int = R.string.no_genres_label

    override val maxGridSize: Int
        get() = if (isLandscape) resources.getInteger(R.integer.max_genre_columns_land)
        else resources.getInteger(R.integer.max_genre_columns)

    override val itemLayoutRes: Int
        get() = if (isGridMode) R.layout.item_grid_gradient
        else R.layout.item_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        libraryViewModel.getGenres().observe(viewLifecycleOwner) { genres ->
            adapter?.dataSet = genres
        }
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.forceReload(ReloadType.Genres)
    }

    override fun createLayoutManager(): GridLayoutManager {
        return GridLayoutManager(requireActivity(), gridSize)
    }

    override fun createAdapter(): GenreAdapter {
        notifyLayoutResChanged(itemLayoutRes)
        val dataSet = adapter?.dataSet ?: ArrayList()
        return GenreAdapter(mainActivity, Glide.with(this), dataSet, itemLayoutRes, lifecycleScope, this)
    }

    override fun genreClick(genre: Genre) {
        findNavController().navigate(R.id.nav_genre_detail, genreDetailArgs(genre))
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateMenu(menu, inflater)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.selectedSortOrder(SortOrder.genreSortOrder)) {
            libraryViewModel.forceReload(ReloadType.Genres)
            return true
        }
        return super.onMenuItemSelected(item)
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        libraryViewModel.forceReload(ReloadType.Genres)
    }

    override fun getSavedViewType(): GridViewType {
        // this value is actually ignored by the implementation
        return GridViewType.Normal
    }

    override fun saveViewType(viewType: GridViewType) {}

    override fun getSavedGridSize(): Int {
        return sharedPreferences.getInt(GENRES_GRID_SIZE_KEY, defaultGridSize)
    }

    override fun saveGridSize(newGridSize: Int) {
        sharedPreferences.edit {
            putInt(GENRES_GRID_SIZE_KEY, newGridSize)
        }
    }

    override fun onGridSizeChanged(isLand: Boolean, gridColumns: Int) {
        layoutManager?.spanCount = gridColumns
        adapter?.notifyDataSetChanged()
    }

    companion object {
        private const val GENRES_GRID_SIZE_KEY = "genres_grid_size"
    }
}