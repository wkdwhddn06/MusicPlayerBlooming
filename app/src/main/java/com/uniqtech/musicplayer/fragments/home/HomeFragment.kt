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

package com.uniqtech.musicplayer.fragments.home

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.HomeAdapter
import com.uniqtech.musicplayer.adapters.album.AlbumAdapter
import com.uniqtech.musicplayer.adapters.artist.ArtistAdapter
import com.uniqtech.musicplayer.adapters.extension.isNullOrEmpty
import com.uniqtech.musicplayer.adapters.song.SongAdapter
import com.uniqtech.musicplayer.databinding.FragmentHomeBinding
import com.uniqtech.musicplayer.extensions.dp
import com.uniqtech.musicplayer.extensions.navigation.*
import com.uniqtech.musicplayer.extensions.resources.addPaddingRelative
import com.uniqtech.musicplayer.extensions.resources.destroyOnDetach
import com.uniqtech.musicplayer.extensions.resources.primaryColor
import com.uniqtech.musicplayer.extensions.resources.setupStatusBarForeground
import com.uniqtech.musicplayer.extensions.setSupportActionBar
import com.uniqtech.musicplayer.extensions.toHtml
import com.uniqtech.musicplayer.extensions.topLevelTransition
import com.uniqtech.musicplayer.fragments.ReloadType
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.helper.menu.*
import com.uniqtech.musicplayer.interfaces.*
import com.uniqtech.musicplayer.model.*
import com.uniqtech.musicplayer.mvvm.SuggestedResult

/**
 * @author Christians M. A. (mardous)
 */
class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home),
    View.OnClickListener,
    ISongCallback,
    IAlbumCallback,
    IArtistCallback,
    IHomeCallback,
    IScrollHelper {

    private var _binding: HomeBinding? = null
    private val binding get() = _binding!!

    private var homeAdapter: HomeAdapter? = null
    private lateinit var requestManager: RequestManager

    private val currentContent: SuggestedResult
        get() = libraryViewModel.getSuggestions().value ?: SuggestedResult.Idle

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestManager = Glide.with(this)
        val homeBinding = FragmentHomeBinding.bind(view)
        _binding = HomeBinding(homeBinding)
        binding.appBarLayout.setupStatusBarForeground()
        setSupportActionBar(binding.toolbar)
        topLevelTransition(view)

        setupTitle()
        setupListeners()
        checkForMargins()

        homeAdapter = HomeAdapter(requireActivity(), arrayListOf(), this).also {
            it.registerAdapterDataObserver(adapterDataObserver)
        }
        binding.toolbar.setOnClickListener {
            findNavController().navigate(R.id.nav_search, null, navOptions)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = homeAdapter
            addPaddingRelative(bottom = 8.dp(resources))
            destroyOnDetach()
        }
        libraryViewModel.getSuggestions().apply {
            observe(viewLifecycleOwner) { result ->
                if (result.isLoading && homeAdapter.isNullOrEmpty) {
                    binding.progressIndicator.show()
                } else {
                    binding.progressIndicator.hide()
                }
                homeAdapter?.dataSet = result.data
            }
        }.also { liveData ->
            if (liveData.value == SuggestedResult.Idle) {
                libraryViewModel.forceReload(ReloadType.Suggestions)
            }
        }

        applyWindowInsetsFromView(view)
    }

    private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            checkIsEmpty()
        }
    }

    private fun setupTitle() {
        binding.appBarLayout.toolbar.setNavigationOnClickListener {
            findNavController().navigate(R.id.nav_search, null, navOptions)
        }
        val hexColor = String.format("#%06X", 0xFFFFFF and primaryColor())
        val appName = "Booming <font color=$hexColor>Music</font>".toHtml()
        binding.appBarLayout.title = appName
    }

    private fun setupListeners() {
        binding.myTopTracks.setOnClickListener(this)
        binding.lastAdded.setOnClickListener(this)
        binding.history.setOnClickListener(this)
        binding.shuffleButton.setOnClickListener(this)
    }

    private fun checkIsEmpty() {
        binding.empty.isVisible = !currentContent.isLoading && homeAdapter.isNullOrEmpty
    }

    private fun checkForMargins() {
        checkForMargins(binding.recyclerView)
    }

    override fun onClick(view: View) {
        when (view) {
            binding.myTopTracks -> {
                findNavController().navigate(R.id.nav_detail_list, detailArgs(ContentType.TopTracks))
            }

            binding.lastAdded -> {
                findNavController().navigate(R.id.nav_detail_list, detailArgs(ContentType.RecentSongs))
            }

            binding.history -> {
                findNavController().navigate(R.id.nav_detail_list, detailArgs(ContentType.History))
            }

            binding.shuffleButton -> libraryViewModel.shuffleAll()
        }
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
    }

    override fun onPause() {
        super.onPause()
        binding.recyclerView.stopScroll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        homeAdapter?.unregisterAdapterDataObserver(adapterDataObserver)
        binding.recyclerView.adapter = null
        binding.recyclerView.layoutManager = null
        homeAdapter = null
        _binding = null
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        libraryViewModel.forceReload(ReloadType.Suggestions)
    }

    override fun onFavoritesStoreChanged() {
        super.onFavoritesStoreChanged()
        libraryViewModel.forceReload(ReloadType.Suggestions)
    }

    @Suppress("UNCHECKED_CAST")
    override fun createSuggestionAdapter(suggestion: Suggestion): RecyclerView.Adapter<*> {
        return when (suggestion.type) {
            ContentType.TopArtists,
            ContentType.RecentArtists -> ArtistAdapter(
                mainActivity,
                requestManager,
                (suggestion.items as List<Artist>),
                R.layout.item_artist,
                this
            )

            ContentType.TopAlbums,
            ContentType.RecentAlbums -> AlbumAdapter(
                mainActivity,
                requestManager,
                (suggestion.items as List<Album>),
                R.layout.item_album,
                callback = this
            )

            ContentType.Favorites,
            ContentType.NotRecentlyPlayed -> SongAdapter(
                mainActivity,
                requestManager,
                (suggestion.items as List<Song>),
                R.layout.item_image,
                callback = this
            )

            else -> throw IllegalArgumentException("Unexpected suggestion type: ${suggestion.type}")
        }
    }

    override fun suggestionClick(suggestion: Suggestion) {
        when (suggestion.type) {
            ContentType.Favorites -> {
                libraryViewModel.favoritePlaylistAsync().observe(viewLifecycleOwner) {
                    findNavController().navigate(R.id.nav_playlist_detail, playlistDetailArgs(it.playListId))
                }
            }

            else -> {
                findNavController().navigate(R.id.nav_detail_list, detailArgs(suggestion.type))
            }
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

    override fun albumClick(album: Album, sharedElements: Array<Pair<View, String>>?) {
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
        sharedElements: Array<Pair<View, String>>?
    ): Boolean = album.onAlbumMenu(this, menuItem)

    override fun albumsMenuItemClick(albums: List<Album>, menuItem: MenuItem) {
        albums.onAlbumsMenu(this, menuItem)
    }

    override fun artistClick(artist: Artist, sharedElements: Array<Pair<View, String>>?) {
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
        sharedElements: Array<Pair<View, String>>?
    ): Boolean = artist.onArtistMenu(this, menuItem)

    override fun artistsMenuItemClick(artists: List<Artist>, menuItem: MenuItem) {
        artists.onArtistsMenu(this, menuItem)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }
}