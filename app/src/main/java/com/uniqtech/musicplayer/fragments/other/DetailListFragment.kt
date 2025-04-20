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

package com.uniqtech.musicplayer.fragments.other

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.android.material.snackbar.Snackbar
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.album.AlbumAdapter
import com.uniqtech.musicplayer.adapters.artist.ArtistAdapter
import com.uniqtech.musicplayer.adapters.song.SongAdapter
import com.uniqtech.musicplayer.database.toSongs
import com.uniqtech.musicplayer.databinding.FragmentDetailListBinding
import com.uniqtech.musicplayer.extensions.*
import com.uniqtech.musicplayer.extensions.media.playlistInfo
import com.uniqtech.musicplayer.extensions.navigation.albumDetailArgs
import com.uniqtech.musicplayer.extensions.navigation.artistDetailArgs
import com.uniqtech.musicplayer.extensions.navigation.asFragmentExtras
import com.uniqtech.musicplayer.extensions.navigation.searchArgs
import com.uniqtech.musicplayer.extensions.resources.hide
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.helper.menu.onAlbumsMenu
import com.uniqtech.musicplayer.helper.menu.onArtistsMenu
import com.uniqtech.musicplayer.helper.menu.onSongMenu
import com.uniqtech.musicplayer.helper.menu.onSongsMenu
import com.uniqtech.musicplayer.interfaces.IAlbumCallback
import com.uniqtech.musicplayer.interfaces.IArtistCallback
import com.uniqtech.musicplayer.interfaces.ISongCallback
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.ContentType
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.search.lastAddedSearchFilter
import com.uniqtech.musicplayer.search.searchFilter
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.Preferences
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailListFragment : AbsMainActivityFragment(R.layout.fragment_detail_list), ISongCallback, IArtistCallback,
    IAlbumCallback {

    private val args by navArgs<DetailListFragmentArgs>()

    private var _binding: FragmentDetailListBinding? = null
    private val binding get() = _binding!!

    private lateinit var contentType: ContentType
    private lateinit var requestManager: RequestManager

    private var songList: List<Song> = arrayListOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentType = args.type
        requestManager = Glide.with(this)
        _binding = FragmentDetailListBinding.bind(view)

        mainActivity.setSupportActionBar(binding.toolbar)
        binding.progressIndicator.hide()

        setupButtons()
        loadContent()
        binding.toolbar.setTitle(contentType.titleRes)
        binding.title.setText(contentType.titleRes)

        materialSharedAxis(view)
        view.applyScrollableContentInsets(binding.recyclerView)
    }

    private fun setupButtons() {
        if (contentType == ContentType.Favorites || contentType == ContentType.History ||
            contentType == ContentType.TopTracks || contentType == ContentType.RecentSongs ||
            contentType == ContentType.NotRecentlyPlayed
        ) {
            binding.shuffleAction.setOnClickListener { MusicPlayer.openQueueShuffle(songList) }
        } else {
            binding.shuffleAction.hide()
        }
        binding.playAction.setOnClickListener { MusicPlayer.openQueue(songList, keepShuffleMode = false) }
    }

    private fun songs(songs: List<Song>, emptyMessageRes: Int = 0) {
        this.songList = songs
        binding.subtitle.text = when (contentType) {
            ContentType.RecentSongs -> buildInfoString(
                Preferences.getLastAddedCutoff(requireContext()).description,
                songs.playlistInfo(requireContext())
            )

            ContentType.History -> buildInfoString(
                Preferences.getHistoryCutoff(requireContext()).description,
                songs.playlistInfo(requireContext())
            )

            ContentType.TopTracks,
            ContentType.Favorites,
            ContentType.NotRecentlyPlayed -> songs.playlistInfo(requireContext())

            else -> null
        }
        if (songs.isEmpty() && emptyMessageRes == 0) {
            findNavController().navigateUp()
        } else {
            binding.empty.isVisible = songs.isEmpty()
        }
    }

    private fun loadContent() {
        when (contentType) {
            ContentType.TopArtists -> loadArtists(ContentType.TopArtists)
            ContentType.RecentArtists -> loadArtists(ContentType.RecentArtists)
            ContentType.TopAlbums -> loadAlbums(ContentType.TopAlbums)
            ContentType.RecentAlbums -> loadAlbums(ContentType.RecentAlbums)
            ContentType.TopTracks -> topPlayed()
            ContentType.History -> loadHistory()
            ContentType.RecentSongs -> lastAddedSongs()
            ContentType.Favorites -> loadFavorite()
            ContentType.NotRecentlyPlayed -> loadNotRecentlyPlayed()
        }
    }

    private fun lastAddedSongs() {
        val songAdapter = songAdapter()
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = linearLayoutManager()
        }
        libraryViewModel.lastAddedSongs().observe(viewLifecycleOwner) { songs ->
            songAdapter.dataSet = songs
            songs(songs, R.string.playlist_empty_text)
        }
    }

    private fun topPlayed() {
        val songAdapter = songAdapter()
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = linearLayoutManager()
        }
        libraryViewModel.topTracks().observe(viewLifecycleOwner) { songs ->
            songAdapter.dataSet = songs
            songs(songs, R.string.playlist_empty_text)
        }
    }

    private fun loadHistory() {
        val songAdapter = songAdapter()
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = linearLayoutManager()
        }
        libraryViewModel.observableHistorySongs().observe(viewLifecycleOwner) { songs ->
            songAdapter.dataSet = songs
            songs(songs, R.string.playlist_empty_text)
        }
    }

    private fun loadNotRecentlyPlayed() {
        val songAdapter = songAdapter()
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = linearLayoutManager()
        }
        libraryViewModel.notRecentlyPlayedSongs().observe(viewLifecycleOwner) { songs ->
            songAdapter.dataSet = songs
            songs(songs)
        }
    }

    private fun loadFavorite() {
        val songAdapter = songAdapter()
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = linearLayoutManager()
        }
        libraryViewModel.favorites().observe(viewLifecycleOwner) { songEntities ->
            val songs = songEntities.toSongs()
            songAdapter.dataSet = songs
            songs(songs)
        }
    }

    private fun loadArtists(type: ContentType) {
        val artistAdapter = artistAdapter()
        val padding = dip(R.dimen.grid_item_margin)
        binding.recyclerView.apply {
            adapter = artistAdapter
            layoutManager = gridLayoutManager()
            updatePadding(left = padding, right = padding)
        }
        libraryViewModel.artists(type).observe(viewLifecycleOwner) { artists ->
            artistAdapter.dataSet = artists
            songs(artists.flatMap { it.songs })
            // Subtitle won't set automatically for albums and artists
            binding.subtitle.text = plurals(R.plurals.x_artists, artists.size)
        }
    }

    private fun loadAlbums(type: ContentType) {
        val albumAdapter = albumAdapter()
        val padding = dip(R.dimen.grid_item_margin)
        binding.recyclerView.apply {
            adapter = albumAdapter
            layoutManager = gridLayoutManager()
            updatePadding(left = padding, right = padding)
        }
        libraryViewModel.albums(type).observe(viewLifecycleOwner) { albums ->
            albumAdapter.dataSet = albums
            songs(albums.flatMap { it.songs })
            // Subtitle won't set automatically for albums and artists
            binding.subtitle.text = plurals(R.plurals.x_albums, albums.size)
        }
    }

    private fun songAdapter(songs: List<Song> = listOf()): SongAdapter =
        SongAdapter(requireActivity(), requestManager, songs, R.layout.item_list, callback = this)

    private fun artistAdapter(artists: List<Artist> = listOf()): ArtistAdapter =
        ArtistAdapter(requireActivity(), requestManager, artists, R.layout.item_grid_circle_single_row, this)

    private fun albumAdapter(albums: List<Album> = listOf()): AlbumAdapter =
        AlbumAdapter(requireActivity(), requestManager, albums, R.layout.item_grid, callback = this)

    private fun linearLayoutManager(): LinearLayoutManager =
        LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

    private fun gridLayoutManager(): GridLayoutManager =
        GridLayoutManager(requireContext(), gridCount(), GridLayoutManager.VERTICAL, false)

    private fun gridCount(): Int {
        if (resources.isTablet) {
            return if (resources.isLandscape) 6 else 4
        }
        return if (resources.isLandscape) 4 else 2
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        loadContent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_list_detail, menu)
        if (!contentType.isSearchableContent) {
            menu.removeItem(R.id.action_search)
        }
        menu.findItem(R.id.action_clear_history).isVisible = contentType.isHistoryContent
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }

            R.id.action_search -> {
                if (contentType.isFavoriteContent) {
                    lifecycleScope.launch(IO) {
                        val favorites = libraryViewModel.favoritePlaylist()
                        withContext(Main) {
                            findNavController().navigate(
                                R.id.nav_search,
                                searchArgs(favorites.searchFilter(requireContext()))
                            )
                        }
                    }
                } else if (contentType.isRecentContent) {
                    findNavController().navigate(
                        R.id.nav_search,
                        searchArgs(lastAddedSearchFilter(requireContext()))
                    )
                }
                true
            }

            R.id.action_clear_history -> {
                libraryViewModel.clearHistory()
                val snackBar = Snackbar.make(binding.root, getString(R.string.history_cleared), Snackbar.LENGTH_LONG)
                val snackBarView = snackBar.view
                snackBarView.translationY = -(resources.getDimension(R.dimen.mini_player_height))
                snackBar.show()
                true
            }

            else -> songList.onSongsMenu(this, menuItem)
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
        album: Album, menuItem: MenuItem, sharedElements: Array<Pair<View, String>>?
    ): Boolean = false

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
        artist: Artist, menuItem: MenuItem, sharedElements: Array<Pair<View, String>>?
    ): Boolean = false

    override fun artistsMenuItemClick(artists: List<Artist>, menuItem: MenuItem) {
        artists.onArtistsMenu(this, menuItem)
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
}
