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

package com.uniqtech.musicplayer.model

import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.Px
import com.uniqtech.musicplayer.R

enum class GridViewType(@IdRes val itemId: Int, @LayoutRes val layoutRes: Int, val margin: Int = 4) {
    Normal(1, R.layout.item_grid),
    Card(1, R.layout.item_card),
    ColoredCard(1, R.layout.item_card_color),
    Circle(1, R.layout.item_grid_circle),
    Image(1, R.layout.item_image_gradient, 0);

    companion object {
        @Px
        fun getMarginForLayout(@LayoutRes layoutRes: Int): Int {
            return entries.firstOrNull { type -> type.layoutRes == layoutRes }?.margin ?: 0
        }
    }
}