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

package com.uniqtech.musicplayer.fragments.playlists

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.extension.isNullOrEmpty
import com.uniqtech.musicplayer.adapters.song.PlaylistSongAdapter
import com.uniqtech.musicplayer.database.PlaylistWithSongs
import com.uniqtech.musicplayer.database.toSongEntity
import com.uniqtech.musicplayer.database.toSongs
import com.uniqtech.musicplayer.database.toSongsEntity
import com.uniqtech.musicplayer.databinding.FragmentDetailListBinding
import com.uniqtech.musicplayer.dialogs.playlists.RemoveFromPlaylistDialog
import com.uniqtech.musicplayer.extensions.applyScrollableContentInsets
import com.uniqtech.musicplayer.extensions.materialSharedAxis
import com.uniqtech.musicplayer.extensions.media.isFavorites
import com.uniqtech.musicplayer.extensions.media.playlistInfo
import com.uniqtech.musicplayer.extensions.navigation.searchArgs
import com.uniqtech.musicplayer.extensions.resources.createFastScroller
import com.uniqtech.musicplayer.extensions.resources.surfaceColor
import com.uniqtech.musicplayer.extensions.setSupportActionBar
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.helper.menu.onPlaylistMenu
import com.uniqtech.musicplayer.helper.menu.onSongMenu
import com.uniqtech.musicplayer.helper.menu.onSongsMenu
import com.uniqtech.musicplayer.interfaces.ISongCallback
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.search.searchFilter
import com.uniqtech.musicplayer.service.MusicPlayer
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Christians M. A. (mardous)
 */
class PlaylistDetailFragment : AbsMainActivityFragment(R.layout.fragment_detail_list),
    ISongCallback {

    private val arguments by navArgs<PlaylistDetailFragmentArgs>()
    private val viewModel by viewModel<PlaylistDetailViewModel> {
        parametersOf(arguments.playlistId)
    }

    private var _binding: FragmentDetailListBinding? = null
    private val binding get() = _binding!!

    private var playlist: PlaylistWithSongs = PlaylistWithSongs.Empty

    private lateinit var requestManager: RequestManager

    private var playlistSongAdapter: PlaylistSongAdapter? = null
    private var wrappedAdapter: RecyclerView.Adapter<*>? = null
    private var recyclerViewDragDropManager: RecyclerViewDragDropManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform(requireContext(), true).apply {
            drawingViewId = R.id.fragment_container
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(surfaceColor())
            setPathMotion(MaterialArcMotion())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailListBinding.bind(view)
        requestManager = Glide.with(this)

        setupButtons()
        setupRecyclerView()

        materialSharedAxis(view)
        view.applyScrollableContentInsets(binding.recyclerView)
        setSupportActionBar(binding.toolbar)
        //binding.collapsingAppBarLayout.setupStatusBarScrim(requireContext())

        viewModel.getPlaylist().observe(viewLifecycleOwner) { playlistWithSongs ->
            playlist = playlistWithSongs
            binding.title.text = playlist.playlistEntity.playlistName
            binding.subtitle.text = playlist.songs.toSongs().playlistInfo(requireContext())
            binding.collapsingAppBarLayout.title = playlist.playlistEntity.playlistName
        }
        viewModel.getSongs().observe(viewLifecycleOwner) {
            binding.progressIndicator.hide()
            playlistSongAdapter?.dataSet = it.toSongs()
        }
        viewModel.playlistExists().observe(viewLifecycleOwner) {
            if (!it) {
                findNavController().navigateUp()
            }
        }
    }

    private fun checkIsEmpty() {
        binding.empty.isVisible = playlistSongAdapter?.isNullOrEmpty == true
    }

    private fun setupButtons() {
        binding.playAction.setOnClickListener {
            MusicPlayer.openQueue(playlistSongAdapter!!.dataSet, keepShuffleMode = false)
        }
        binding.shuffleAction.setOnClickListener {
            MusicPlayer.openQueueShuffle(playlistSongAdapter!!.dataSet)
        }
    }

    private fun setupRecyclerView() {
        playlistSongAdapter = PlaylistSongAdapter(
            mainActivity,
            requestManager,
            emptyList(),
            R.layout.item_list_draggable,
            this
        )
        recyclerViewDragDropManager = RecyclerViewDragDropManager().also { dragDropManager ->
            wrappedAdapter = dragDropManager.createWrappedAdapter(playlistSongAdapter!!)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = wrappedAdapter
        binding.recyclerView.itemAnimator = RefactoredDefaultItemAnimator()
        binding.recyclerView.createFastScroller()
        recyclerViewDragDropManager?.attachRecyclerView(binding.recyclerView)
        playlistSongAdapter!!.registerAdapterDataObserver(adapterDataObserver)
    }

    private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            checkIsEmpty()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_playlist_detail, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        playlist.let {
            if (it.playlistEntity.isFavorites(requireContext())) {
                menu.removeItem(R.id.action_rename_playlist)
                menu.removeItem(R.id.action_delete_playlist)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }

            R.id.action_search -> {
                findNavController().navigate(
                    R.id.nav_search,
                    searchArgs(playlist.playlistEntity.searchFilter(requireContext()))
                )
                true
            }

            else -> playlist.onPlaylistMenu(this, menuItem)
        }
    }

    override fun songMenuItemClick(
        song: Song,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        return when (menuItem.itemId) {
            R.id.action_remove_from_playlist -> {
                RemoveFromPlaylistDialog.create(song.toSongEntity(playlist.playlistEntity.playListId))
                    .show(childFragmentManager, "REMOVE_FROM_PLAYLIST")
                true
            }

            else -> song.onSongMenu(this, menuItem)
        }
    }

    override fun songsMenuItemClick(songs: List<Song>, menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.action_remove_from_playlist -> {
                RemoveFromPlaylistDialog.create(songs.toSongsEntity(playlist.playlistEntity))
                    .show(childFragmentManager, "REMOVE_FROM_PLAYLIST")
            }

            else -> songs.onSongsMenu(this, menuItem)
        }
    }

    override fun onPause() {
        recyclerViewDragDropManager?.cancelDrag()
        playlistSongAdapter?.saveSongs(playlist.playlistEntity)
        super.onPause()
    }

    override fun onDestroyView() {
        playlistSongAdapter?.unregisterAdapterDataObserver(adapterDataObserver)

        recyclerViewDragDropManager?.release()
        recyclerViewDragDropManager = null

        binding.recyclerView.itemAnimator = null
        binding.recyclerView.adapter = null

        WrapperAdapterUtils.releaseAll(wrappedAdapter)
        wrappedAdapter = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "PlaylistDetail"
    }
}