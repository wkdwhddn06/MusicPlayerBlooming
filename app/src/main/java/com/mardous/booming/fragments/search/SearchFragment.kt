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

package com.mardous.booming.fragments.search

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.mardous.booming.R
import com.mardous.booming.adapters.SearchAdapter
import com.mardous.booming.adapters.extension.isEmpty
import com.mardous.booming.database.PlaylistWithSongs
import com.mardous.booming.databinding.FragmentSearchBinding
import com.mardous.booming.extensions.*
import com.mardous.booming.extensions.navigation.*
import com.mardous.booming.extensions.resources.reactionToKey
import com.mardous.booming.extensions.resources.setupStatusBarForeground
import com.mardous.booming.fragments.base.AbsMainActivityFragment
import com.mardous.booming.helper.menu.onAlbumMenu
import com.mardous.booming.helper.menu.onArtistMenu
import com.mardous.booming.helper.menu.onPlaylistMenu
import com.mardous.booming.helper.menu.onSongMenu
import com.mardous.booming.interfaces.ISearchCallback
import com.mardous.booming.model.Album
import com.mardous.booming.model.Artist
import com.mardous.booming.model.Genre
import com.mardous.booming.model.Song
import com.mardous.booming.search.SearchFilter
import com.mardous.booming.search.SearchQuery
import kotlinx.coroutines.Job
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

/**
 * @author Christians M. A. (mardous)
 */
class SearchFragment : AbsMainActivityFragment(R.layout.fragment_search),
    View.OnClickListener, ChipGroup.OnCheckedStateChangeListener, ISearchCallback {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModel<SearchViewModel>()

    private lateinit var searchAdapter: SearchAdapter
    private lateinit var voiceSearchLauncher: ActivityResultLauncher<Intent>

    private var job: Job? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        materialSharedAxis(view)
        _binding = FragmentSearchBinding.bind(view)
        setSupportActionBar(binding.toolbar)
        setupRecyclerView()

        view.applyScrollableContentInsets(binding.recyclerView)

        viewModel.getSearchFilter().observe(viewLifecycleOwner) { searchFilter ->
            if (searchFilter != null) {
                val compatibleModes = searchFilter.getCompatibleModes().map { it.chipId }

                binding.chipGroup.children.map { it as Chip }
                    .filter { !compatibleModes.contains(it.id) }
                    .forEach { it.isVisible = false }
                binding.chipGroup.isSelectionRequired = true
                binding.chipGroup.check(compatibleModes.first())

                binding.searchView.hint = searchFilter.getName()
                binding.filterScrollView.isVisible = searchFilter.getCompatibleModes().size > 1
            }
        }
        viewModel.getSearchResult().observe(viewLifecycleOwner) { result ->
            searchAdapter.dataSet = result
        }

        // Observe playlists-related changes
        libraryViewModel.getPlaylists().observe(viewLifecycleOwner) {
            viewModel.submitLatest()
        }

        binding.appBar.setupStatusBarForeground()
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.clearText.setOnClickListener(this)
        binding.chipGroup.setOnCheckedStateChangeListener(this@SearchFragment)
        binding.searchView.reactionToKey(KeyEvent.KEYCODE_ENTER) {
            hideSoftKeyboard()
        }
        binding.searchView.doAfterTextChanged {
            search(it?.toString())
        }

        voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                val result = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val newQuery = result?.get(0)
                binding.searchView.setText(newQuery, TextView.BufferType.EDITABLE)
            }
        }

        if (savedInstanceState == null) {
            binding.searchView.setText(arguments?.getString(QUERY))
            viewModel.useSearchFilter(arguments?.let {
                BundleCompat.getParcelable(it, FILTER, SearchFilter::class.java)
            })
        }
    }

    private val adapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            super.onChanged()
            val isEmpty = searchAdapter.isEmpty
            if (!isEmpty) {
                binding.recyclerView.scheduleLayoutAnimation()
            }
            binding.empty.isVisible = isEmpty
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(mainActivity, Glide.with(this), emptyList(), this).apply {
            registerAdapterDataObserver(adapterDataObserver)
        }
        binding.recyclerView.apply {
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_fall_down)
                .apply { animation.duration = resources.getInteger(R.integer.short_anim_time).toLong() }
            layoutManager = LinearLayoutManager(activity)
            adapter = searchAdapter
            setOnTouchListener { _: View?, _: MotionEvent? ->
                hideSoftKeyboard()
                false
            }
        }
    }

    override fun onClick(view: View) {
        when (view) {
            binding.clearText -> binding.searchView.text?.clear()
        }
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        val searchMode = when {
            checkedIds.isNotEmpty() -> SearchQuery.FilterMode.entries.firstOrNull { it.chipId == checkedIds.single() }
            else -> null
        }
        viewModel.useFilterMode(searchMode)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }

            else -> false
        }
    }

    override fun songMenuItemClick(song: Song, menuItem: MenuItem): Boolean {
        return song.onSongMenu(this, menuItem)
    }

    override fun albumClick(album: Album, sharedElements: Array<Pair<View, String>>) {
        findNavController().navigate(
            R.id.nav_album_detail,
            albumDetailArgs(album.id),
            null,
            sharedElements.asFragmentExtras()
        )
    }

    override fun albumMenuItemClick(
        album: Album,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>
    ): Boolean {
        return album.onAlbumMenu(this, menuItem)
    }

    override fun artistClick(artist: Artist, sharedElements: Array<Pair<View, String>>) {
        findNavController().navigate(
            R.id.nav_artist_detail,
            artistDetailArgs(artist),
            null,
            sharedElements.asFragmentExtras()
        )
    }

    override fun artistMenuItemClick(
        artist: Artist,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>
    ): Boolean {
        return artist.onArtistMenu(this, menuItem)
    }

    override fun playlistClick(playlist: PlaylistWithSongs) {
        findNavController().navigate(R.id.nav_playlist_detail, playlistDetailArgs(playlist.playlistEntity.playListId))
    }

    override fun playlistMenuItemClick(playlist: PlaylistWithSongs, menuItem: MenuItem): Boolean {
        return playlist.onPlaylistMenu(this, menuItem)
    }

    override fun genreClick(genre: Genre) {
        findNavController().navigate(R.id.nav_genre_detail, genreDetailArgs(genre))
    }

    private fun search(query: String?) {
        if (query == null) return
        TransitionManager.beginDelayedTransition(binding.appBar)
        binding.clearText.isVisible = query.isNotEmpty()
        job?.cancel()
        job = viewModel.submit(query)
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_hint))
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            showToast(R.string.speech_not_supported)
        }
    }

    private fun hideSoftKeyboard() {
        activity?.hideSoftKeyboard()
        binding.searchView.clearFocus()
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        viewModel.submitLatest()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchAdapter.unregisterAdapterDataObserver(adapterDataObserver)
        binding.searchView.setOnKeyListener(null)
        _binding = null
    }

    companion object {
        private const val FILTER = "filter"
        private const val QUERY = "query"
    }
}