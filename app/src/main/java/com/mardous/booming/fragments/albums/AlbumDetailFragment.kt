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

package com.mardous.booming.fragments.albums

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
import com.mardous.booming.R
import com.mardous.booming.adapters.album.AlbumAdapter
import com.mardous.booming.adapters.song.SimpleSongAdapter
import com.mardous.booming.databinding.FragmentAlbumDetailBinding
import com.mardous.booming.extensions.*
import com.mardous.booming.extensions.glide.albumOptions
import com.mardous.booming.extensions.glide.getAlbumGlideModel
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.extensions.media.isArtistNameUnknown
import com.mardous.booming.extensions.navigation.*
import com.mardous.booming.extensions.resources.*
import com.mardous.booming.extensions.utilities.buildInfoString
import com.mardous.booming.fragments.base.AbsMainActivityFragment
import com.mardous.booming.helper.menu.onAlbumMenu
import com.mardous.booming.helper.menu.onAlbumsMenu
import com.mardous.booming.helper.menu.onSongMenu
import com.mardous.booming.helper.menu.onSongsMenu
import com.mardous.booming.http.Result
import com.mardous.booming.http.lastfm.LastFmAlbum
import com.mardous.booming.interfaces.IAlbumCallback
import com.mardous.booming.interfaces.ISongCallback
import com.mardous.booming.model.Album
import com.mardous.booming.model.Song
import com.mardous.booming.search.searchFilter
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.sort.SortOrder
import com.mardous.booming.util.sort.prepareSortOrder
import com.mardous.booming.util.sort.selectedSortOrder
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/**
 * @author Christians M. A. (mardous)
 */
class AlbumDetailFragment : AbsMainActivityFragment(R.layout.fragment_album_detail),
    ISongCallback,
    IAlbumCallback {

    private val arguments by navArgs<AlbumDetailFragmentArgs>()
    private val detailViewModel by viewModel<AlbumDetailViewModel> {
        parametersOf(arguments.albumId)
    }

    private var _binding: AlbumDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var requestManager: RequestManager
    private lateinit var simpleSongAdapter: SimpleSongAdapter

    private var albumArtistExists = false
    private var lang: String? = null
    private var biography: String? = null

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
        _binding = AlbumDetailBinding(FragmentAlbumDetailBinding.bind(view))
        setSupportActionBar(binding.toolbar, "")

        view.applyScrollableContentInsets(binding.container, addedPadding = 16.dp(resources))
        materialSharedAxis(view, prepareTransition = false)

        binding.appBarLayout.setupStatusBarForeground()
        binding.image.transitionName = arguments.albumId.toString()

        postponeEnterTransition()
        detailViewModel.getAlbumDetail().observe(viewLifecycleOwner) { album ->
            view.doOnPreDraw {
                startPostponedEnterTransition()
            }
            albumArtistExists = !album.albumArtistName.isNullOrEmpty()
            showAlbum(album)
        }

        setupRecyclerView()
        setupSongSortButton()
        binding.albumText.setOnClickListener {
            goToArtist()
        }
        binding.playAction.setOnClickListener {
            MusicPlayer.openQueue(getAlbum().songs, keepShuffleMode = false)
        }
        binding.shuffleAction.setOnClickListener {
            MusicPlayer.openQueueShuffle(getAlbum().songs)
        }
        binding.searchAction?.setOnClickListener {
            goToSearch()
        }

        binding.wiki.apply {
            setOnClickListener {
                maxLines = (if (maxLines == 4) Integer.MAX_VALUE else 4)
            }
        }

        detailViewModel.loadAlbumDetail()
    }

    private fun getAlbum(): Album = detailViewModel.getAlbum()

    private fun setupRecyclerView() {
        simpleSongAdapter = SimpleSongAdapter(
            requireActivity(), requestManager, arrayListOf(), R.layout.item_song, SortOrder.albumSongSortOrder, this
        )
        binding.songRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = DefaultItemAnimator()
            isNestedScrollingEnabled = false
            adapter = simpleSongAdapter
        }.also {
            it.destroyOnDetach()
        }
    }

    private fun setupSongSortButton() {
        binding.songSortOrder.setOnClickListener {
            createSortOrderMenu(it, R.menu.menu_album_song_sort_order, SortOrder.albumSongSortOrder) {
                detailViewModel.loadAlbumDetail()
            }
        }
        binding.similarAlbumSortOrder.setOnClickListener {
            createSortOrderMenu(it, R.menu.menu_artist_album_sort_order, SortOrder.similarAlbumSortOrder) {
                loadSimilarContent(getAlbum())
            }
        }
    }

    private fun createSortOrderMenu(view: View, sortMenuRes: Int, sortOrder: SortOrder, onChanged: () -> Unit) {
        PopupMenu(requireContext(), view).apply {
            inflate(sortMenuRes)
            menu.prepareSortOrder(sortOrder)
            setOnMenuItemClickListener { item ->
                if (item.selectedSortOrder(sortOrder)) {
                    onChanged()
                    true
                } else false
            }
            show()
        }
    }

    private fun showAlbum(album: Album) {
        if (album.songs.isEmpty()) {
            findNavController().navigateUp()
            return
        }

        binding.albumTitle.text = album.name
        val songText = plurals(R.plurals.songs, album.songCount)
        binding.songTitle.text = (if (album.songCount > 1)
            buildInfoString(songText, album.songCount.toString()) else songText)
        val artistName = if (albumArtistExists) album.albumArtistName else album.artistName
        if (album.year <= 0) {
            binding.albumText.text = artistName!!.displayArtistName()
        } else {
            binding.albumText.text = buildInfoString(artistName!!.displayArtistName(), album.year.toString())
        }
        loadAlbumCover(album)
        simpleSongAdapter.dataSet = album.songs
        loadSimilarContent(album)
        if (requireContext().isAllowedToDownloadMetadata()) {
            loadWiki(album)
        }
    }

    private fun loadSimilarContent(album: Album) {
        detailViewModel.getSimilarAlbums(album).observe(viewLifecycleOwner) {
            moreAlbums(it)
        }
    }

    private fun loadWiki(album: Album, lang: String? = Locale.getDefault().language) {
        this.biography = null
        this.lang = lang
        detailViewModel.getAlbumWiki(album, lang).observe(viewLifecycleOwner) { result ->
            if (result is Result.Success) {
                aboutAlbum(result.data)
            }
        }
    }

    private fun loadAlbumCover(album: Album) {
        requestManager.asBitmap()
            .albumOptions(album)
            .load(album.getAlbumGlideModel())
            .into(binding.image)
    }

    private fun moreAlbums(albums: List<Album>) {
        if (albums.isNotEmpty()) {
            binding.similarAlbumRecyclerView.isVisible = true
            binding.similarAlbumSortOrder.isVisible = true
            binding.similarAlbumTitle.isVisible = true
            binding.similarAlbumTitle.text = if (getAlbum().isArtistNameUnknown())
                getString(R.string.label_more_from_artist) else getString(
                R.string.label_more_from_x,
                getAlbum().displayArtistName()
            )

            val albumAdapter =
                AlbumAdapter(requireActivity(), requestManager, albums, R.layout.item_image, callback = this)
            binding.similarAlbumRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            binding.similarAlbumRecyclerView.adapter = albumAdapter
            binding.similarAlbumRecyclerView.destroyOnDetach()
        }
    }

    private fun aboutAlbum(lastFmAlbum: LastFmAlbum) {
        val albumValue = lastFmAlbum.album
        if (albumValue != null) {
            val wikiTitle = binding.wikiTitle
            val wikiView = binding.wiki
            if (!albumValue.wiki?.content.isNullOrEmpty()) {
                biography = albumValue.wiki!!.content!!
                wikiView.show()
                wikiView.setMarkdownText(biography!!)
                wikiTitle.text = getString(R.string.about_x_title, getAlbum().name)
                wikiTitle.show()
            }
        }

        // If the "lang" parameter is set and no biography is given, retry with default language
        if (biography == null && lang != null) {
            loadWiki(getAlbum(), null)
        }
    }

    override fun songMenuItemClick(
        song: Song,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        return song.onSongMenu(this, menuItem)
    }

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
    ): Boolean {
        return false
    }

    override fun albumsMenuItemClick(albums: List<Album>, menuItem: MenuItem) {
        albums.onAlbumsMenu(this, menuItem)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
//        menuInflater.inflate(R.menu.menu_album_detail, menu)
//        if (!isLandscape()) {
//            menu.removeItem(R.id.action_search)
//        }
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

            else -> getAlbum().onAlbumMenu(this, menuItem)
        }
    }

    private fun goToArtist() {
        if (albumArtistExists) {
            findNavController().navigate(R.id.nav_artist_detail, artistDetailArgs(-1, getAlbum().albumArtistName))
        } else {
            findNavController().navigate(R.id.nav_artist_detail, artistDetailArgs(getAlbum().artistId, null))
        }
    }

    private fun goToSearch() {
        findNavController().navigate(R.id.nav_search, searchArgs(getAlbum().searchFilter(requireContext())))
    }

    private fun goToPlayInfo() {
        findNavController().navigate(R.id.nav_play_info, playInfoArgs(getAlbum()))
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        detailViewModel.loadAlbumDetail()
    }

    override fun onDestroyView() {
        _binding?.songRecyclerView?.layoutManager = null
        _binding?.songRecyclerView?.adapter = null
        _binding?.similarAlbumRecyclerView?.layoutManager = null
        _binding?.similarAlbumRecyclerView?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}