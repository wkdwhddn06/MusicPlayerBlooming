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

package com.uniqtech.musicplayer.audio

import android.content.Context
import android.media.AudioDeviceInfo

/**
 * @author Christians M. A. (mardous)
 */
class AudioDevice(
    val code: Int,
    val type: AudioDeviceType,
    private val productName: CharSequence?,
    private val isProduct: Boolean = type.isProduct
) {

    fun getDeviceName(context: Context): CharSequence {
        return if (isProduct && !productName.isNullOrEmpty()) productName else context.getString(type.nameRes)
    }

    companion object {
        /**
         * Constant describing an unknown audio device.
         */
        val UnknownDevice = AudioDevice(AudioDeviceInfo.TYPE_UNKNOWN, AudioDeviceType.Unknown, null)
    }
}