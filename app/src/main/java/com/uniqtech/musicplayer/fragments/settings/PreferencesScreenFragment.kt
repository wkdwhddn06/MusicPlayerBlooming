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

package com.uniqtech.musicplayer.fragments.settings

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.uniqtech.musicplayer.BuildConfig
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.Space
import com.uniqtech.musicplayer.extensions.applyBottomWindowInsets
import com.uniqtech.musicplayer.extensions.dip
import com.uniqtech.musicplayer.extensions.hasS
import com.uniqtech.musicplayer.extensions.utilities.toEnum
import com.uniqtech.musicplayer.preferences.dialog.*

open class PreferencesScreenFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val dialogFragment: DialogFragment? = when (preference) {
            is NowPlayingExtraInfoPreference -> NowPlayingExtraInfoPreferenceDialog()
            is CategoriesPreference -> CategoriesPreferenceDialog()
            is NowPlayingScreenPreference -> NowPlayingScreenPreferenceDialog()
            is ActionOnCoverPreference -> ActionOnCoverPreferenceDialog.newInstance(preference.key, preference.title!!)
            is PreAmpPreference -> PreAmpPreferenceDialog()
            else -> null
        }

        if (dialogFragment != null) {
            dialogFragment.show(childFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(ColorDrawable(Color.TRANSPARENT))
        if (hasS()) {
            listView.overScrollMode = View.OVER_SCROLL_NEVER
        }

        listView.applyBottomWindowInsets(addedSpace = Space.bottom(dip(R.dimen.mini_player_height)))

        findPreference<Preference>("about")?.summary =
            getString(R.string.about_summary, BuildConfig.VERSION_NAME)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val settingsScreen = preference.key.toEnum<SettingsScreen>()
        if (settingsScreen != null) {
            findNavController().navigate(settingsScreen.navAction, bundleOf(EXTRA_SCREEN to settingsScreen))
        } else if (preference.key == "about") {
            findNavController().navigate(R.id.action_to_about)
        }
        return true
    }

    protected fun restartActivity() {
        activity?.recreate()
    }

    companion object {
        private const val EXTRA_SCREEN = "extra_screen"
    }
}