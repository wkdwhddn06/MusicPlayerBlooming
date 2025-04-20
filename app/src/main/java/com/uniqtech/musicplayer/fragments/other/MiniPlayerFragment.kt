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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.databinding.FragmentMiniPlayerBinding
import com.uniqtech.musicplayer.extensions.glide.getDefaultGlideTransition
import com.uniqtech.musicplayer.extensions.glide.getSongGlideModel
import com.uniqtech.musicplayer.extensions.glide.songOptions
import com.uniqtech.musicplayer.extensions.isTablet
import com.uniqtech.musicplayer.extensions.media.displayArtistName
import com.uniqtech.musicplayer.extensions.resources.show
import com.uniqtech.musicplayer.extensions.resources.textColorPrimary
import com.uniqtech.musicplayer.extensions.resources.textColorSecondary
import com.uniqtech.musicplayer.extensions.resources.toForegroundColorSpan
import com.uniqtech.musicplayer.extensions.utilities.DEFAULT_INFO_DELIMITER
import com.uniqtech.musicplayer.fragments.base.AbsMusicServiceFragment
import com.uniqtech.musicplayer.helper.MusicProgressViewUpdateHelper
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.Preferences
import kotlin.math.abs

class MiniPlayerFragment : AbsMusicServiceFragment(R.layout.fragment_mini_player),
    View.OnClickListener,
    MusicProgressViewUpdateHelper.Callback {

    private var _binding: FragmentMiniPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var progressViewUpdateHelper: MusicProgressViewUpdateHelper

    private lateinit var primaryColorSpan: ForegroundColorSpan
    private lateinit var secondaryColorSpan: ForegroundColorSpan

    private var target: Target<Bitmap>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMiniPlayerBinding.bind(view)
        primaryColorSpan = textColorPrimary().toForegroundColorSpan()
        secondaryColorSpan = textColorSecondary().toForegroundColorSpan()
        setUpButtons()
        view.setOnTouchListener(FlingPlayBackController(view.context))
    }

    private fun setUpButtons() {
        setupExtraControls()
        binding.actionNext.setOnClickListener(this)
        binding.actionPrevious.setOnClickListener(this)
        binding.actionPlayPause.setOnClickListener(this)
    }

    fun setupExtraControls() {
        if (resources.isTablet) {
            binding.actionNext.show()
            binding.actionPrevious.show()
        } else {
            binding.actionNext.isVisible = Preferences.extraControls
            binding.actionPrevious.isVisible = Preferences.extraControls
        }
    }

    override fun onClick(view: View) {
        when (view) {
            binding.actionPlayPause -> MusicPlayer.togglePlayPause()
            binding.actionNext -> MusicPlayer.playNextSong()
            binding.actionPrevious -> MusicPlayer.playPreviousSong()
        }
    }

    override fun onResume() {
        super.onResume()
        progressViewUpdateHelper.start()
    }

    override fun onPause() {
        super.onPause()
        progressViewUpdateHelper.stop()
    }

    override fun onDestroyView() {
        Glide.with(this).clear(target)
        super.onDestroyView()
        _binding = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateCurrentSong()
        updatePlayPause()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateCurrentSong()
    }

    override fun onPlayStateChanged() {
        super.onPlayStateChanged()
        updatePlayPause()
    }

    override fun onUpdateProgressViews(progress: Long, total: Long) {
        binding.progressBar.max = total.toInt()
        binding.progressBar.setProgressCompat(progress.toInt(), true)
    }

    private fun updateCurrentSong() {
        val song = MusicPlayer.currentSong

        val builder = SpannableStringBuilder()
        val title = SpannableString(song.title).apply {
            setSpan(primaryColorSpan, 0, length, 0)
        }
        val text = SpannableString(song.displayArtistName()).apply {
            setSpan(secondaryColorSpan, 0, length, 0)
        }

        //binding.songInfo.isSelected = true
        binding.songInfo.text = builder.append(title).append(DEFAULT_INFO_DELIMITER).append(text)

        target = Glide.with(this@MiniPlayerFragment)
            .asBitmap()
            .load(song.getSongGlideModel())
            .transition(getDefaultGlideTransition())
            .songOptions(song)
            .into(binding.image)
    }

    private fun updatePlayPause() {
        if (MusicPlayer.isPlaying) {
            binding.actionPlayPause.setIconResource(R.drawable.ic_pause_24dp)
        } else {
            binding.actionPlayPause.setIconResource(R.drawable.ic_play_24dp)
        }
    }

    class FlingPlayBackController(context: Context) : View.OnTouchListener {
        private var flingPlayBackController =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (abs(velocityX) > abs(velocityY)) {
                        if (velocityX < 0) {
                            MusicPlayer.playNextSong()
                            return true
                        } else if (velocityX > 0) {
                            MusicPlayer.playPreviousSong()
                            return true
                        }
                    }
                    return false
                }
            })

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return flingPlayBackController.onTouchEvent(event)
        }
    }

}