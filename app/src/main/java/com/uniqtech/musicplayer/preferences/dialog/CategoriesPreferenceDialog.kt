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

package com.uniqtech.musicplayer.preferences.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.preference.CategoryInfoAdapter
import com.uniqtech.musicplayer.databinding.DialogRecyclerViewBinding
import com.uniqtech.musicplayer.extensions.create
import com.uniqtech.musicplayer.extensions.utilities.toMutableListIfRequired
import com.uniqtech.musicplayer.model.CategoryInfo
import com.uniqtech.musicplayer.util.LIBRARY_CATEGORIES
import com.uniqtech.musicplayer.util.Preferences

class CategoriesPreferenceDialog : DialogFragment() {

    private lateinit var adapter: CategoryInfoAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogRecyclerViewBinding.inflate(layoutInflater)

        var categoryInfos = Preferences.libraryCategories
        if (savedInstanceState != null && savedInstanceState.containsKey(LIBRARY_CATEGORIES)) {
            categoryInfos = BundleCompat.getParcelableArrayList(
                savedInstanceState, LIBRARY_CATEGORIES, CategoryInfo::class.java
            )!!
        }

        adapter = CategoryInfoAdapter(requireContext(), categoryInfos.toMutableList())

        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.adapter = adapter
        adapter.attachToRecyclerView(binding.recyclerView)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.library_categories_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                updateCategories(adapter.items)
            }
            .setNeutralButton(R.string.reset_action, null)
            .create { dialog ->
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    adapter.items = Preferences.getDefaultLibraryCategoryInfos()
                        .toMutableListIfRequired()
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(LIBRARY_CATEGORIES, ArrayList(adapter.items))
    }

    private fun updateCategories(categories: List<CategoryInfo>) {
        if (categories.any { category -> category.visible }) {
            Preferences.libraryCategories = categories
        }
    }
}