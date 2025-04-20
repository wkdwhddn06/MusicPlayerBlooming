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

package com.uniqtech.musicplayer.lyrics

/**
 * @author Christians M. A. (mardous)
 */
data class LrcLyrics @JvmOverloads constructor(
    val offset: Long = 0,
    val lines: MutableList<LrcEntry> = ArrayList()
) {

    val hasLines: Boolean
        get() = lines.any { it.text.isNotBlank() }

    fun getValidLines() = lines.filter { it.text.isNotBlank() }

    fun getText(): String {
        return lines.joinToString("\n") { it.getFormattedText() }
    }

    fun clear() {
        lines.clear()
    }
}