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

package com.uniqtech.musicplayer.fragments.years

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.extension.isNullOrEmpty
import com.uniqtech.musicplayer.adapters.song.SongAdapter
import com.uniqtech.musicplayer.databinding.FragmentDetailListBinding
import com.uniqtech.musicplayer.extensions.materialSharedAxis
import com.uniqtech.musicplayer.extensions.media.songCountStr
import com.uniqtech.musicplayer.extensions.media.songsDurationStr
import com.uniqtech.musicplayer.extensions.navigation.searchArgs
import com.uniqtech.musicplayer.extensions.setSupportActionBar
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.helper.menu.onSongMenu
import com.uniqtech.musicplayer.helper.menu.onSongsMenu
import com.uniqtech.musicplayer.interfaces.ISongCallback
import com.uniqtech.musicplayer.model.ReleaseYear
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.search.searchFilter
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.sort.SortOrder
import com.uniqtech.musicplayer.util.sort.prepareSortOrder
import com.uniqtech.musicplayer.util.sort.selectedSortOrder
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class YearDetailFragment : AbsMainActivityFragment(R.layout.fragment_detail_list), ISongCallback {

    private val arguments by navArgs<YearDetailFragmentArgs>()
    private val detailViewModel: YearDetailViewModel by viewModel {
        parametersOf(arguments.extraYear)
    }

    private var _binding: FragmentDetailListBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter
    private lateinit var requestManager: RequestManager

    private val year: ReleaseYear
        get() = detailViewModel.getYear().value ?: ReleaseYear.Empty

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailListBinding.bind(view)
        requestManager = Glide.with(this)

        materialSharedAxis(view)
        setSupportActionBar(binding.toolbar)

        setupButtons()
        setupRecyclerView()
        detailViewModel.getYear().observe(viewLifecycleOwner) { year ->
            binding.collapsingAppBarLayout.title = year.name
            binding.title.text = year.name
            songs(year.songs)
        }
    }

    private fun setupButtons() {
        binding.playAction.setOnClickListener {
            MusicPlayer.openQueue(songAdapter.dataSet, keepShuffleMode = false)
        }
        binding.shuffleAction.setOnClickListener {
            MusicPlayer.openQueueShuffle(songAdapter.dataSet)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(requireActivity(), requestManager, ArrayList(), R.layout.item_list, callback = this)
        binding.recyclerView.apply {
            itemAnimator = DefaultItemAnimator()
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
        songAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                checkIsEmpty()
            }
        })
    }

    fun songs(songs: List<Song>) {
        binding.progressIndicator.hide()
        binding.subtitle.text = buildInfoString(songs.songCountStr(requireContext()), songs.songsDurationStr())
        songAdapter.dataSet = songs
    }

    private fun checkIsEmpty() {
        if (songAdapter.isNullOrEmpty) {
            findNavController().navigateUp()
        }
    }

    override fun songMenuItemClick(
        song: Song,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean = song.onSongMenu(this, menuItem)

    override fun songsMenuItemClick(songs: List<Song>, menuItem: MenuItem) {
        songs.onSongsMenu(this, menuItem)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_genre_detail, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.prepareSortOrder(SortOrder.yearSongSortOrder)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when {
            item.selectedSortOrder(SortOrder.yearSongSortOrder) -> {
                detailViewModel.loadDetail()
                true
            }
            item.itemId == R.id.action_search -> {
                findNavController().navigate(R.id.nav_search, searchArgs(year.searchFilter(requireContext())))
                true
            }
            else -> songAdapter.dataSet.onSongsMenu(this, item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
