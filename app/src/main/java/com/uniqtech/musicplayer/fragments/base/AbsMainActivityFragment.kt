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
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.navigation.navOptions
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.activities.MainActivity
import com.uniqtech.musicplayer.extensions.applyWindowInsets
import com.uniqtech.musicplayer.extensions.dip
import com.uniqtech.musicplayer.extensions.isLandscape
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * @author Christians M. A. (mardous)
 */
abstract class AbsMainActivityFragment @JvmOverloads constructor(@LayoutRes layoutRes: Int = 0) :
    AbsMusicServiceFragment(layoutRes), MenuProvider {

    val libraryViewModel: LibraryViewModel by activityViewModel()

    protected val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    protected val navOptions by lazy {
        navOptions {
            launchSingleTop = false
            anim {
                enter = R.anim.booming_fragment_open_enter
                exit = R.anim.booming_fragment_open_exit
                popEnter = R.anim.booming_fragment_close_enter
                popExit = R.anim.booming_fragment_close_exit
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.STARTED)
    }

    protected fun applyWindowInsetsFromView(view: View) {
        view.applyWindowInsets(
            left = isLandscape() && mainActivity.navigationView.isGone, right = true, bottom = true
        )
    }

    protected fun checkForMargins(view: View) {
        if (mainActivity.isBottomNavVisible) {
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dip(R.dimen.bottom_nav_height)
            }
        }
    }
}