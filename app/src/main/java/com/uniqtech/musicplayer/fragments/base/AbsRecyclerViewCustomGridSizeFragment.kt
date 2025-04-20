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

package com.uniqtech.musicplayer.fragments.base

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.dip
import com.uniqtech.musicplayer.extensions.isLandscape
import com.uniqtech.musicplayer.model.GridViewType

abstract class AbsRecyclerViewCustomGridSizeFragment<Adt : RecyclerView.Adapter<*>, LM : RecyclerView.LayoutManager> :
    AbsRecyclerViewFragment<Adt, LM>() {

    protected var gridSize: Int
        get() = getSavedGridSize()
        set(newGridSize) {
            val oldLayoutRes = itemLayoutRes
            saveGridSize(newGridSize)
            // only recreate the adapter and layout manager if the layout currentLayoutRes has changed
            if (oldLayoutRes != itemLayoutRes) {
                invalidateLayoutManager()
                invalidateAdapter()
            } else {
                onGridSizeChanged(isLandscape, newGridSize)
            }
        }

    protected var viewType: GridViewType
        get() = getSavedViewType()
        set(newViewType) {
            saveViewType(newViewType)
            invalidateAdapter()
        }

    protected val isGridMode: Boolean
        get() = gridSize > maxGridSizeForList

    protected open val maxGridSize: Int
        get() = if (isLandscape) {
            resources.getInteger(R.integer.max_columns_land)
        } else resources.getInteger(R.integer.max_columns)

    protected open val maxGridSizeForList: Int
        get() = if (isLandscape) {
            resources.getInteger(R.integer.default_list_columns_land)
        } else resources.getInteger(R.integer.default_list_columns)

    protected open val defaultGridSize: Int
        get() = if (isLandscape) resources.getInteger(R.integer.default_grid_columns_land)
        else resources.getInteger(R.integer.default_grid_columns)

    private var currentLayoutRes = 0

    @get:LayoutRes
    protected open val itemLayoutRes: Int
        get() = if (isGridMode) {
            viewType.layoutRes
        } else R.layout.item_list

    protected val isLandscape: Boolean
        get() = resources.isLandscape

    protected fun notifyLayoutResChanged(@LayoutRes res: Int) {
        currentLayoutRes = res
        applyRecyclerViewPaddingForLayoutRes(recyclerView, currentLayoutRes)
    }

    private fun applyRecyclerViewPaddingForLayoutRes(recyclerView: RecyclerView, @LayoutRes itemLayoutRes: Int) {
        val padding = GridViewType.getMarginForLayout(itemLayoutRes)
        recyclerView.setPadding(padding, padding, padding, padding + dip(R.dimen.mini_player_height))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyRecyclerViewPaddingForLayoutRes(recyclerView, currentLayoutRes)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val selectedViewType = GridViewType.entries.firstOrNull { type -> type.itemId == item.itemId }
        if (selectedViewType != null) {
            item.isChecked = !item.isChecked
            this.viewType = selectedViewType
            return true
        }
        if (handleGridSizeMenuItem(item)) {
            return true
        }
        return super.onMenuItemSelected(item)
    }

    protected abstract fun getSavedViewType(): GridViewType
    protected abstract fun saveViewType(viewType: GridViewType)
    protected abstract fun getSavedGridSize(): Int
    protected abstract fun saveGridSize(newGridSize: Int)
    protected abstract fun onGridSizeChanged(isLand: Boolean, gridColumns: Int)

    private fun setUpGridSizeMenu(gridSizeMenu: Menu) {

    }

    private fun handleGridSizeMenuItem(item: MenuItem): Boolean {
        return false
    }
}