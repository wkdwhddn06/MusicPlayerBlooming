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

package com.uniqtech.musicplayer.helper

import java.util.Locale

/**
 * Simple thread safe stop watch.
 *
 * @author Karim Abou Zeid (kabouzeid)
 */
class StopWatch {
    /**
     * The time the stop watch was last started.
     */
    private var startTime: Long = 0

    /**
     * The time elapsed before the current [.startTime].
     */
    private var previousElapsedTime: Long = 0

    /**
     * Whether the stop watch is currently running or not.
     */
    private var isRunning = false

    /**
     * Starts or continues the stop watch.
     *
     * @see .pause
     * @see .reset
     */
    fun start() {
        synchronized(this) {
            startTime = System.currentTimeMillis()
            isRunning = true
        }
    }

    /**
     * Pauses the stop watch. It can be continued later from [.start].
     *
     * @see .start
     * @see .reset
     */
    fun pause() {
        synchronized(this) {
            previousElapsedTime += System.currentTimeMillis() - startTime
            isRunning = false
        }
    }

    /**
     * Stops and resets the stop watch to zero milliseconds.
     *
     * @see .start
     * @see .pause
     */
    fun reset() {
        synchronized(this) {
            startTime = 0
            previousElapsedTime = 0
            isRunning = false
        }
    }

    /**
     * @return the total elapsed time in milliseconds
     */
    val elapsedTime: Long
        get() {
            synchronized(this) {
                var currentElapsedTime: Long = 0
                if (isRunning) {
                    currentElapsedTime = System.currentTimeMillis() - startTime
                }
                return previousElapsedTime + currentElapsedTime
            }
        }

    override fun toString(): String {
        return String.format(Locale.getDefault(), "%d millis, is running: %s", elapsedTime, isRunning.toString())
    }
}