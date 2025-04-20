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

package com.uniqtech.musicplayer.fragments.player.styles.fullcoverstyle

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.databinding.FragmentFullCoverPlayerBinding
import com.uniqtech.musicplayer.extensions.getOnBackPressedDispatcher
import com.uniqtech.musicplayer.extensions.glide.DEFAULT_SONG_IMAGE
import com.uniqtech.musicplayer.extensions.glide.getSongGlideModel
import com.uniqtech.musicplayer.extensions.glide.songOptions
import com.uniqtech.musicplayer.extensions.resources.getPrimaryTextColor
import com.uniqtech.musicplayer.extensions.resources.toColorStateList
import com.uniqtech.musicplayer.extensions.whichFragment
import com.uniqtech.musicplayer.fragments.player.base.AbsPlayerControlsFragment
import com.uniqtech.musicplayer.fragments.player.base.AbsPlayerFragment
import com.uniqtech.musicplayer.helper.color.MediaNotificationProcessor
import com.uniqtech.musicplayer.model.NowPlayingAction
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicPlayer

/**
 * @author Christians M. A. (mardous)
 */
class FullCoverPlayerFragment : AbsPlayerFragment(R.layout.fragment_full_cover_player),
    View.OnClickListener {

    private var _binding: FragmentFullCoverPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var controlsFragment: FullCoverPlayerControlsFragment

    private var playbackControlsColor = 0
    private var disabledPlaybackControlsColor = 0

    private var target: Target<Bitmap>? = null

    override val playerControlsFragment: AbsPlayerControlsFragment
        get() = controlsFragment

    override fun onAttach(context: Context) {
        super.onAttach(context)
        playbackControlsColor = getPrimaryTextColor(context, isDark = false)
        disabledPlaybackControlsColor = getPrimaryTextColor(context, isDark = false, isDisabled = true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFullCoverPlayerBinding.bind(view)
        setupColors()
        setupListeners()
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { v: View, insets: WindowInsetsCompat ->
            val statusBar = insets.getInsets(Type.systemBars())
            v.updatePadding(left = statusBar.left, top = statusBar.top, right = statusBar.right)
            val displayCutout = insets.getInsets(Type.displayCutout())
            v.updatePadding(left = displayCutout.left, right = displayCutout.right)
            insets
        }
    }

    private fun setupColors() {
        binding.nextSongLabel.setTextColor(disabledPlaybackControlsColor)
        binding.nextSongText.setTextColor(playbackControlsColor)
        binding.close.setColorFilter(playbackControlsColor)
    }

    private fun setupListeners() {
        binding.nextSongText.setOnClickListener(this)
        binding.nextSongAlbumArt.setOnClickListener(this)
        binding.close.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        when (view) {
            binding.nextSongText, binding.nextSongAlbumArt -> onQuickActionEvent(NowPlayingAction.OpenPlayQueue)
            binding.close -> getOnBackPressedDispatcher().onBackPressed()
        }
    }

    private fun updateNextSongInfo() {
        Glide.with(this).clear(target)
        val nextSong = MusicPlayer.getNextSong()
        if (nextSong != null) {
            _binding?.nextSongText?.text = nextSong.title
            target = _binding?.nextSongAlbumArt?.let {
                Glide.with(this)
                    .asBitmap()
                    .load(nextSong.getSongGlideModel())
                    .songOptions(nextSong)
                    .into(it)
            }
        } else {
            _binding?.nextSongText?.setText(R.string.now_playing)
            _binding?.nextSongAlbumArt?.setImageResource(DEFAULT_SONG_IMAGE)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateNextSongInfo()
    }

    override fun onPlayStateChanged() {
        super.onPlayStateChanged()
        updateNextSongInfo()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateNextSongInfo()
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        updateNextSongInfo()
    }

    override fun onMenuInflated(menu: Menu) {
        super.onMenuInflated(menu)
        menu.removeItem(R.id.action_favorite)
    }

    override fun onCreateChildFragments() {
        super.onCreateChildFragments()
        controlsFragment = whichFragment(R.id.playbackControlsFragment)
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        super.onColorChanged(color)
        binding.mask.backgroundTintList = color.backgroundColor.toColorStateList()
        controlsFragment.setColors(color.backgroundColor, color.primaryTextColor, color.secondaryTextColor)
    }

    override fun onToggleFavorite(song: Song, isFavorite: Boolean) {
        super.onToggleFavorite(song, isFavorite)
        onIsFavoriteChanged(isFavorite, true)
    }

    override fun onIsFavoriteChanged(isFavorite: Boolean, withAnimation: Boolean) {
        controlsFragment.setFavorite(isFavorite, withAnimation)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}