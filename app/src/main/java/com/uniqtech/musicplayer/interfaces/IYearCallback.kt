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

package com.uniqtech.musicplayer.interfaces

import android.view.MenuItem
import com.uniqtech.musicplayer.model.ReleaseYear

interface IYearCallback {
    fun yearClick(year: ReleaseYear)
    fun yearMenuItemClick(year: ReleaseYear, menuItem: MenuItem): Boolean
    fun yearsMenuItemClick(selection: List<ReleaseYear>, menuItem: MenuItem): Boolean
}