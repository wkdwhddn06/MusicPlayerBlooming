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

package com.uniqtech.musicplayer.fragments.artists

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.album.AlbumAdapter
import com.uniqtech.musicplayer.adapters.artist.ArtistAdapter
import com.uniqtech.musicplayer.adapters.song.SimpleSongAdapter
import com.uniqtech.musicplayer.databinding.FragmentArtistDetailBinding
import com.uniqtech.musicplayer.extensions.*
import com.uniqtech.musicplayer.extensions.glide.artistOptions
import com.uniqtech.musicplayer.extensions.glide.getArtistGlideModel
import com.uniqtech.musicplayer.extensions.media.albumCountStr
import com.uniqtech.musicplayer.extensions.media.displayName
import com.uniqtech.musicplayer.extensions.media.songCountStr
import com.uniqtech.musicplayer.extensions.navigation.*
import com.uniqtech.musicplayer.extensions.resources.*
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.helper.menu.*
import com.uniqtech.musicplayer.http.Result
import com.uniqtech.musicplayer.http.lastfm.LastFmArtist
import com.uniqtech.musicplayer.interfaces.IAlbumCallback
import com.uniqtech.musicplayer.interfaces.IArtistCallback
import com.uniqtech.musicplayer.interfaces.ISongCallback
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.search.searchFilter
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.sort.SortOrder
import com.uniqtech.musicplayer.util.sort.prepareSortOrder
import com.uniqtech.musicplayer.util.sort.selectedSortOrder
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/**
 * @author Christians M. A. (mardous)
 */
class ArtistDetailFragment : AbsMainActivityFragment(R.layout.fragment_artist_detail),
    IAlbumCallback, IArtistCallback, ISongCallback {

    private val arguments by navArgs<ArtistDetailFragmentArgs>()
    private val detailViewModel by viewModel<ArtistDetailViewModel> {
        parametersOf(arguments.artistId, arguments.artistName)
    }

    private var _binding: ArtistDetailBinding? = null
    private val binding get() = _binding!!

    private var lang: String? = null
    private var biography: String? = null

    private lateinit var requestManager: RequestManager
    private lateinit var songAdapter: SimpleSongAdapter
    private lateinit var albumAdapter: AlbumAdapter

    private val isAlbumArtist: Boolean
        get() = !arguments.artistName.isNullOrEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.fragment_container
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(surfaceColor())
            setPathMotion(MaterialArcMotion())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestManager = Glide.with(this)
        _binding = ArtistDetailBinding(FragmentArtistDetailBinding.bind(view))
        setSupportActionBar(binding.toolbar, "")

        view.applyScrollableContentInsets(binding.container, addedPadding = 16.dp(resources))
        materialSharedAxis(view, prepareTransition = false)

        binding.appBarLayout.setupStatusBarForeground()
        binding.image.transitionName = if (isAlbumArtist) arguments.artistName
        else arguments.artistId.toString()

        postponeEnterTransition()
        detailViewModel.getArtistDetail().observe(viewLifecycleOwner) { result ->
            view.doOnPreDraw {
                startPostponedEnterTransition()
            }
            showArtist(result)
        }

        setupRecyclerView()
        setupSortOrder()

        binding.playAction.setOnClickListener {
            MusicPlayer.openQueue(getArtist().sortedSongs, keepShuffleMode = false)
        }
        binding.shuffleAction.setOnClickListener {
            MusicPlayer.openQueueShuffle(getArtist().songs)
        }
        binding.searchAction?.setOnClickListener {
            goToSearch()
        }

        binding.biography.apply {
            setOnClickListener {
                maxLines = (if (maxLines == 4) Integer.MAX_VALUE else 4)
            }
        }

        detailViewModel.loadArtistDetail()
    }

    private fun getArtist() = detailViewModel.getArtist()

    private fun setupRecyclerView() {
        albumAdapter =
            AlbumAdapter(requireActivity(), requestManager, arrayListOf(), R.layout.item_image, callback = this)
        binding.albumRecyclerView.apply {
            layoutManager = LinearLayoutManager(this.context, LinearLayoutManager.HORIZONTAL, false)
            itemAnimator = DefaultItemAnimator()
            adapter = albumAdapter
            destroyOnDetach()
        }
        songAdapter =
            SimpleSongAdapter(requireActivity(), requestManager, arrayListOf(), R.layout.item_song, SortOrder.artistSongSortOrder, this)
        binding.songRecyclerView.apply {
            layoutManager = LinearLayoutManager(this.context)
            itemAnimator = DefaultItemAnimator()
            adapter = songAdapter
            destroyOnDetach()
        }
    }

    private fun setupSortOrder() {
        binding.songSortOrder.setOnClickListener {
            createSortOrderMenu(it, R.menu.menu_artist_song_sort_order, SortOrder.artistSongSortOrder)
        }
        binding.albumSortOrder.setOnClickListener {
            createSortOrderMenu(it, R.menu.menu_artist_album_sort_order, SortOrder.artistAlbumSortOrder)
        }
    }

    private fun createSortOrderMenu(view: View, sortMenuRes: Int, sortOrder: SortOrder) {
        PopupMenu(requireContext(), view).apply {
            inflate(sortMenuRes)
            menu.prepareSortOrder(sortOrder)
            setOnMenuItemClickListener { item ->
                if (item.selectedSortOrder(sortOrder)) {
                    detailViewModel.loadArtistDetail()
                    true
                } else false
            }
            show()
        }
    }

    private fun showArtist(artist: Artist) {
        if (artist.songCount == 0) {
            findNavController().navigateUp()
            return
        }

        loadArtistImage(artist)
        if (requireContext().isAllowedToDownloadMetadata()) {
            loadBiography(artist.name)
        }
        binding.artistTitle.text = artist.displayName()
        binding.artistText.text = buildInfoString(
            artist.albumCountStr(requireContext()),
            artist.songCountStr(requireContext())
        )

        val songText = plurals(R.plurals.songs, artist.songCount)
        val albumText = plurals(R.plurals.albums, artist.albumCount)
        binding.songTitle.text = songText
        binding.albumTitle.text = albumText
        songAdapter.dataSet = artist.sortedSongs
        albumAdapter.dataSet = artist.sortedAlbums

        if (artist.isAlbumArtist) {
            loadSimilarArtists(artist)
        }
    }

    private fun loadBiography(name: String, lang: String? = Locale.getDefault().language) {
        this.biography = null
        this.lang = lang
        detailViewModel.getArtistBio(name, lang, null).observe(viewLifecycleOwner) { result ->
            if (result is Result.Success) {
                artistInfo(result.data)
            }
        }
    }


    private fun artistInfo(lastFmArtist: LastFmArtist?) {
        if (lastFmArtist?.artist?.bio != null) {
            val bioContent = lastFmArtist.artist.bio.content
            if (bioContent != null && bioContent.trim().isNotEmpty()) {
                biography = bioContent
                val biographyView = binding.biography
                biographyView.show()
                biographyView.setMarkdownText(bioContent)
                val biographyTitleView = binding.biographyTitle
                biographyTitleView.text = getString(R.string.about_x_title, getArtist().name)
                biographyTitleView.show()
            }
        }

        // If the "lang" parameter is set and no biography is given, retry with default language
        if (biography == null && lang != null) {
            loadBiography(getArtist().name, null)
        }
    }

    private fun loadArtistImage(artist: Artist) {
        requestManager.asBitmap()
            .load(artist.getArtistGlideModel())
            .artistOptions(artist)
            .into(binding.image)
    }

    private fun loadSimilarArtists(artist: Artist) {
        detailViewModel.getSimilarArtists(artist).observe(viewLifecycleOwner) { artists ->
            similarArtists(artists)
        }
    }

    private fun similarArtists(artists: List<Artist>) {
        if (artists.isNotEmpty()) {
            binding.similarArtistTitle.isVisible = true
            binding.similarArtistRecyclerView.apply {
                isVisible = true
                layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                adapter = ArtistAdapter(requireActivity(), requestManager, artists, R.layout.item_artist, this@ArtistDetailFragment)
                destroyOnDetach()
            }
        }
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
    ): Boolean {
        return false
    }

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
    ): Boolean = false

    override fun artistsMenuItemClick(artists: List<Artist>, menuItem: MenuItem) {
        artists.onArtistsMenu(this, menuItem)
    }

    override fun songMenuItemClick(
        song: Song,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        if (menuItem.itemId == R.id.action_go_to_artist) {
            return true
        }
        return song.onSongMenu(this, menuItem)
    }

    override fun songsMenuItemClick(songs: List<Song>, menuItem: MenuItem) {
        songs.onSongsMenu(this, menuItem)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        //menuInflater.inflate(R.menu.menu_artist_detail, menu)
        //if (!isLandscape()) {
        //    menu.removeItem(R.id.action_search)
        //}
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }

            R.id.action_search -> {
                goToSearch()
                true
            }

            R.id.action_play_info -> {
                goToPlayInfo()
                true
            }

            else -> getArtist().onArtistMenu(this, menuItem)
        }
    }

    private fun goToSearch() {
        findNavController().navigate(R.id.nav_search, searchArgs(getArtist().searchFilter(requireContext())))
    }

    private fun goToPlayInfo() {
        findNavController().navigate(R.id.nav_play_info, playInfoArgs(getArtist()))
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        detailViewModel.loadArtistDetail()
    }

    override fun onDestroyView() {
        _binding?.albumRecyclerView?.layoutManager = null
        _binding?.albumRecyclerView?.adapter = null
        _binding?.songRecyclerView?.layoutManager = null
        _binding?.songRecyclerView?.adapter = null
        _binding?.similarArtistRecyclerView?.layoutManager = null
        _binding?.similarArtistRecyclerView?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}