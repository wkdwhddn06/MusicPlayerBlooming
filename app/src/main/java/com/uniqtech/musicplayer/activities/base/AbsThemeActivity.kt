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

package com.uniqtech.musicplayer.activities.base

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.createAppTheme
import com.uniqtech.musicplayer.extensions.hasQ
import com.uniqtech.musicplayer.extensions.resources.isColorLight
import com.uniqtech.musicplayer.extensions.resources.surfaceColor
import com.uniqtech.musicplayer.util.Preferences

/**
 * @author Christians M. A. (mardous)
 */
abstract class AbsThemeActivity : AppCompatActivity() {

    private var windowInsetsController: WindowInsetsControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        updateTheme()
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT))
        super.onCreate(savedInstanceState)
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (hasQ()) {
            window.isNavigationBarContrastEnforced = false
            window.decorView.isForceDarkAllowed = false
        }
        ViewGroupCompat.installCompatInsetsDispatch(window.decorView)
    }

    private fun updateTheme() {
        val appTheme = createAppTheme()
        setTheme(appTheme.themeRes)
        if (appTheme.hasSeedColor) {
            DynamicColors.applyToActivityIfAvailable(this,
                DynamicColorsOptions.Builder()
                    .setContentBasedSource(appTheme.seedColor)
                    .setOnAppliedCallback {
                        if (appTheme.isBlackTheme) {
                            setTheme(R.style.BlackThemeOverlay)
                        }
                    }
                    .build()
            )
        }
        if (Preferences.isCustomFont) {
            setTheme(R.style.ManropeThemeOverlay)
        }
    }

    protected open fun postRecreate() {
        // hack to prevent java.lang.RuntimeException: Performing pause of activity that is not resumed
        // makes sure recreate() is called right after and not in onResume()
        Handler(Looper.getMainLooper()).post { recreate() }
    }

    fun setLightStatusBar(lightStatusBar: Boolean = surfaceColor().isColorLight) {
        windowInsetsController?.isAppearanceLightStatusBars = lightStatusBar
    }

    fun setLightNavigationBar(lightNavigationBar: Boolean = surfaceColor().isColorLight) {
        windowInsetsController?.isAppearanceLightNavigationBars = lightNavigationBar
    }

    override fun onDestroy() {
        super.onDestroy()
        windowInsetsController = null
    }
}