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

package com.uniqtech.musicplayer.fragments.queue

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnPreDraw
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.activities.MainActivity
import com.uniqtech.musicplayer.adapters.song.PlayingQueueSongAdapter
import com.uniqtech.musicplayer.databinding.FragmentQueueBinding
import com.uniqtech.musicplayer.dialogs.playlists.CreatePlaylistDialog
import com.uniqtech.musicplayer.extensions.applyBottomWindowInsets
import com.uniqtech.musicplayer.extensions.applyScrollableContentInsets
import com.uniqtech.musicplayer.extensions.media.songCountStr
import com.uniqtech.musicplayer.extensions.resources.createFastScroller
import com.uniqtech.musicplayer.extensions.resources.inflateMenu
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.fragments.base.AbsMusicServiceFragment
import com.uniqtech.musicplayer.helper.menu.onSongMenu
import com.uniqtech.musicplayer.model.QueueQuickAction
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.Preferences
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * @author Christians M. A. (mardous)
 */
class PlayingQueueFragment : AbsMusicServiceFragment(R.layout.fragment_queue),
    Toolbar.OnMenuItemClickListener,
    View.OnClickListener,
    PlayingQueueSongAdapter.Callback {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private val toolbar get() = binding.appBarLayout.toolbar

    private var playingQueueAdapter: PlayingQueueSongAdapter? = null
    private var dragDropManager: RecyclerViewDragDropManager? = null
    private var wrappedAdapter: RecyclerView.Adapter<*>? = null
    private var linearLayoutManager: LinearLayoutManager? = null

    private var lastRemovedSong: Song? = null
    private var snackbar: Snackbar? = null

    private val allQuickActions by lazy { QueueQuickAction.entries }
    private var selectedQuickAction: QueueQuickAction by Delegates.observable(Preferences.queueQuickAction) { _: KProperty<*>, old: QueueQuickAction?, new: QueueQuickAction ->
        Preferences.queueQuickAction = new
        updateQuickAction(old, new)
    }

    private val queueInfo: CharSequence
        get() = buildInfoString(
            playingQueue.songCountStr(requireContext()),
            MusicPlayer.getQueueDurationInfo()
        )

    private val playingQueue: List<Song>
        get() = MusicPlayer.playingQueue

    private val queuePosition: Int
        get() = MusicPlayer.position

    private val mainActivity: MainActivity
        get() = activity as MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentQueueBinding.bind(view)
        binding.quickActionButton.setOnClickListener(this@PlayingQueueFragment)
        binding.quickActionButton.setOnLongClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setItems(allQuickActions.map { getString(it.titleRes) }.toTypedArray()) { _, position: Int ->
                    selectedQuickAction = allQuickActions[position]
                }
                .show()
            true
        }

        enterTransition = Fade()
        exitTransition = Fade()
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        applyWindowInsets(view)

        playingQueueAdapter = PlayingQueueSongAdapter(
            requireActivity(),
            Glide.with(this),
            arrayListOf(),
            this,
            MusicPlayer.position
        ).also { adapter ->
            dragDropManager = RecyclerViewDragDropManager().also { manager ->
                wrappedAdapter = manager.createWrappedAdapter(adapter)
            }
        }

        linearLayoutManager = LinearLayoutManager(requireContext())

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = wrappedAdapter
        binding.recyclerView.itemAnimator = RefactoredDefaultItemAnimator()

        dragDropManager!!.attachRecyclerView(_binding!!.recyclerView)
        linearLayoutManager!!.scrollToPositionWithOffset(queuePosition + 1, 0)

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) _binding?.quickActionButton?.shrink()
                else _binding?.quickActionButton?.extend()
            }
        })
        binding.recyclerView.createFastScroller()

        toolbar.isTitleCentered = false
        toolbar.setNavigationIcon(R.drawable.ic_back_24dp)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        toolbar.setTitle(R.string.playing_queue_label)
        toolbar.inflateMenu(R.menu.menu_playing_queue, this@PlayingQueueFragment)

        updateQuickAction(null, selectedQuickAction)
    }

    private fun applyWindowInsets(view: View) {
        view.applyScrollableContentInsets(binding.recyclerView)
        binding.quickActionButton.applyBottomWindowInsets()
    }

    override fun onClick(view: View) {
        if (view == binding.quickActionButton) {
            val menuItem = toolbar.menu.findItem(selectedQuickAction.menuItemId)
            if (menuItem != null) {
                onMenuItemClick(menuItem)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_playing_queue -> {
                CreatePlaylistDialog.create(playingQueue)
                    .show(childFragmentManager, "CREATE_PLAYLIST")
                true
            }

            R.id.action_clear_playing_queue -> {
                MusicPlayer.clearQueue()
                true
            }

            R.id.action_shuffle_queue -> {
                MusicPlayer.shuffleQueue()
                true
            }

            R.id.action_move_to_current_track -> {
                resetToCurrentPosition()
                true
            }

            else -> false
        }
    }

    override fun onRemoveSong(song: Song, fromPosition: Int) {
        lastRemovedSong = song
        if (snackbar?.isShown == true) {
            snackbar?.dismiss()
        }
        snackbar = Snackbar.make(
            binding.root.context,
            binding.root,
            getString(R.string.x_removed_from_playing_queue, lastRemovedSong!!.title),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.undo_action) {
                MusicPlayer.enqueue(song, fromPosition)
            }
            .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    snackbar.removeCallback(this)
                }

                override fun onShown(snackbar: Snackbar) {
                    MusicPlayer.removeFromQueue(fromPosition)
                }
            }).also { newSnackbar ->
                newSnackbar.show()
            }
    }

    override fun songMenuItemClick(
        song: Song,
        menuItem: MenuItem,
        sharedElements: Array<Pair<View, String>>?
    ): Boolean {
        return song.onSongMenu(this, menuItem)
    }

    override fun songsMenuItemClick(songs: List<Song>, menuItem: MenuItem) {}

    private fun resetToCurrentPosition() {
        binding.recyclerView.stopScroll()
        linearLayoutManager!!.scrollToPositionWithOffset(queuePosition, 0)
    }

    private fun updateQuickAction(oldAction: QueueQuickAction?, newAction: QueueQuickAction) {
        if (oldAction != null) {
            toolbar.menu.findItem(oldAction.menuItemId).isVisible = true
        }
        toolbar.menu.findItem(newAction.menuItemId).isVisible = false
        binding.quickActionButton.setText(newAction.titleRes)
        binding.quickActionButton.setIconResource(newAction.iconRes)
    }

    private fun updateQueue() {
        playingQueueAdapter?.swapDataSet(playingQueue, queuePosition)
        toolbar.subtitle = queueInfo
    }

    private fun updateQueuePosition() {
        playingQueueAdapter?.current = queuePosition
        toolbar.subtitle = queueInfo
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateQueue()
        resetToCurrentPosition()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateQueuePosition()
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        if (MusicPlayer.playingQueue.isEmpty()) {
            findNavController().navigateUp()
            return
        }
        updateQueue()
    }

    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        updateQueue()
    }

    override fun onPause() {
        dragDropManager?.cancelDrag()
        super.onPause()
    }

    override fun onDestroyView() {
        dragDropManager?.release()
        dragDropManager = null

        WrapperAdapterUtils.releaseAll(wrappedAdapter)
        wrappedAdapter = null
        playingQueueAdapter = null

        binding.recyclerView.clearOnScrollListeners()
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.adapter = null
        binding.recyclerView.layoutManager = null

        linearLayoutManager = null

        super.onDestroyView()
        if (MusicPlayer.playingQueue.isNotEmpty())
            mainActivity.expandPanel()
    }
}