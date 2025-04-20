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

package com.uniqtech.musicplayer.fragments.player.styles.gradientstyle

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.databinding.FragmentGradientPlayerBinding
import com.uniqtech.musicplayer.extensions.resources.darkenColor
import com.uniqtech.musicplayer.extensions.resources.toColorStateList
import com.uniqtech.musicplayer.extensions.whichFragment
import com.uniqtech.musicplayer.fragments.player.base.AbsPlayerControlsFragment
import com.uniqtech.musicplayer.fragments.player.base.AbsPlayerFragment
import com.uniqtech.musicplayer.helper.color.MediaNotificationProcessor
import com.uniqtech.musicplayer.model.NowPlayingAction
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicPlayer

class GradientPlayerFragment : AbsPlayerFragment(R.layout.fragment_gradient_player), View.OnClickListener {

    private var _binding: FragmentGradientPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var controlsFragment: GradientPlayerControlsFragment
    private var lastColor: Int = Color.TRANSPARENT

    override val playerControlsFragment: AbsPlayerControlsFragment
        get() = controlsFragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGradientPlayerBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.darkColorBackground) { v: View, insets: WindowInsetsCompat ->
            val navigationBar = insets.getInsets(Type.systemBars())
            v.updatePadding(bottom = navigationBar.bottom)
            val displayCutout = insets.getInsets(Type.displayCutout())
            v.updatePadding(left = displayCutout.left, right = displayCutout.right)
            insets
        }
        setupListeners()
    }

    private fun setupListeners() {
        binding.nextSongLabel.setOnClickListener(this)
        binding.volumeIcon.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v) {
            binding.nextSongLabel -> onQuickActionEvent(NowPlayingAction.OpenPlayQueue)
            binding.volumeIcon -> onQuickActionEvent(NowPlayingAction.SoundSettings)
        }
    }

    private fun updateNextSong() {
        val nextSong = MusicPlayer.getNextSong()
        if (nextSong != null) {
            binding.nextSongLabel.text = nextSong.title
        } else {
            binding.nextSongLabel.setText(R.string.now_playing)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateNextSong()
    }

    override fun onPlayStateChanged() {
        super.onPlayStateChanged()
        updateNextSong()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateNextSong()
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        updateNextSong()
    }

    override fun onToggleFavorite(song: Song, isFavorite: Boolean) {
        super.onToggleFavorite(song, isFavorite)
        onIsFavoriteChanged(isFavorite, true)
    }

    override fun onIsFavoriteChanged(isFavorite: Boolean, withAnimation: Boolean) {
        controlsFragment.setFavorite(isFavorite, withAnimation)
    }

    override fun onMenuInflated(menu: Menu) {
        super.onMenuInflated(menu)
        menu.removeItem(R.id.action_sound_settings)
        menu.removeItem(R.id.action_favorite)
    }

    override fun onCreateChildFragments() {
        super.onCreateChildFragments()
        controlsFragment = whichFragment(R.id.playbackControlsFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        super.onColorChanged(color)
        if (_binding == null) return
        this.lastColor = color.backgroundColor
        binding.colorBackground.setBackgroundColor(lastColor)
        binding.darkColorBackground.setBackgroundColor(lastColor.darkenColor)
        binding.mask.backgroundTintList = lastColor.toColorStateList()

        controlsFragment.setColors(color.backgroundColor, color.primaryTextColor, color.secondaryTextColor)
        binding.volumeIcon.setColorFilter(color.primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.nextSongLabel.setTextColor(color.primaryTextColor)
        TextViewCompat.setCompoundDrawableTintList(binding.nextSongLabel, color.primaryTextColor.toColorStateList())
    }

    override fun onLyricsVisibilityChange(animatorSet: AnimatorSet, lyricsVisible: Boolean) {
        if (lyricsVisible) {
            animatorSet.play(ObjectAnimator.ofFloat(binding.mask, View.ALPHA, 0f))
        } else {
            animatorSet.play(ObjectAnimator.ofFloat(binding.mask, View.ALPHA, 1f))
        }
    }
}