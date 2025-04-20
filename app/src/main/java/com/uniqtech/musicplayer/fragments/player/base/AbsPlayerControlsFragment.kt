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

package com.uniqtech.musicplayer.fragments.player.base

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.getShapeAppearanceModel
import com.uniqtech.musicplayer.extensions.media.durationStr
import com.uniqtech.musicplayer.fragments.player.PlayerAnimator
import com.uniqtech.musicplayer.helper.MusicProgressViewUpdateHelper
import com.uniqtech.musicplayer.model.NowPlayingAction
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.preferences.dialog.NowPlayingExtraInfoPreferenceDialog
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.util.Preferences

/**
 * @author Christians M. A. (mardous)
 */
abstract class AbsPlayerControlsFragment(@LayoutRes layoutRes: Int) : Fragment(layoutRes),
    View.OnClickListener,
    View.OnLongClickListener,
    MusicProgressViewUpdateHelper.Callback {

    private lateinit var progressViewUpdateHelper: MusicProgressViewUpdateHelper

    protected var playerFragment: AbsPlayerFragment? = null
    private var playerAnimator: PlayerAnimator? = null

    protected open val playPauseFab: FloatingActionButton? = null
    protected open val progressSlider: Slider? = null
    protected open val seekBar: SeekBar? = null
    protected open val songTotalTime: TextView? = null
    protected open val songCurrentProgress: TextView? = null
    protected open val songInfoView: TextView? = null

    private var isSeeking = false
    private var progressAnimator: ObjectAnimator? = null

    private var isShown = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        playerFragment = parentFragment as? AbsPlayerFragment
            ?: error("${javaClass.name} must be a child of ${AbsPlayerFragment::class.java.name}")
    }

    override fun onDetach() {
        super.onDetach()
        playerFragment = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this)
    }

    override fun onStart() {
        super.onStart()
        playerAnimator = onCreatePlayerAnimator()
        if (Preferences.circularPlayButton) {
            playPauseFab?.shapeAppearanceModel = requireContext().getShapeAppearanceModel(
                com.google.android.material.R.style.ShapeAppearance_Material3_Corner_Large,
                R.style.CircularShapeAppearance
            )
        }
        songTotalTime?.setOnClickListener(this)
        songInfoView?.setOnClickListener(this)
        songInfoView?.setOnLongClickListener(this)
        setUpProgressSlider()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.text -> {
                Preferences.let {
                    it.preferAlbumArtistName = !it.preferAlbumArtistName
                }
                (view as? TextView)?.text = getSongArtist(MusicPlayer.currentSong)
            }
            R.id.songTotalTime -> {
                Preferences.let {
                    it.preferRemainingTime = !it.preferRemainingTime
                }
            }
            R.id.songInfo -> {
                val playerView = this.view
                val infoString = getExtraInfoString(MusicPlayer.currentSong)
                if (playerView != null && !infoString.isNullOrEmpty()) {
                    Snackbar.make(playerView, infoString, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (view.id == R.id.songInfo) {
            NowPlayingExtraInfoPreferenceDialog().show(childFragmentManager, "NOW_PLAYING_EXTRA_INFO")
            return true
        }
        return false
    }

    override fun onUpdateProgressViews(progress: Long, total: Long) {
        if (seekBar == null) {
            progressSlider?.valueTo = total.toFloat().coerceAtLeast(1f)
            progressSlider?.value = progress.toFloat().coerceIn(progressSlider?.valueFrom, progressSlider?.valueTo)
        } else {
            seekBar?.max = total.toInt()
            if (isSeeking) {
                seekBar?.progress = progress.toInt()
            } else {
                progressAnimator = ObjectAnimator.ofInt(seekBar, "progress", progress.toInt()).apply {
                    duration = SLIDER_ANIMATION_TIME
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
        val totalDisplayTime = when {
            Preferences.preferRemainingTime -> (total - progress)
            else -> total
        }
        songTotalTime?.text = totalDisplayTime.durationStr()
        songCurrentProgress?.text = progress.durationStr()
    }

    private fun setUpProgressSlider() {
        progressSlider?.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            onProgressChange(value.toInt(), fromUser)
        })
        progressSlider?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                onStartTrackingTouch()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                onStopTrackingTouch(slider.value.toInt())
            }
        })

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChange(progress, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                onStartTrackingTouch()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onStopTrackingTouch(seekBar?.progress ?: 0)
            }
        })
    }

    private fun onProgressChange(value: Int, fromUser: Boolean) {
        if (fromUser) {
            onUpdateProgressViews(value.toLong(), MusicPlayer.songDurationMillis.toLong())
        }
    }

    private fun onStartTrackingTouch() {
        isSeeking = true
        progressViewUpdateHelper.stop()
        progressAnimator?.cancel()
    }

    private fun onStopTrackingTouch(value: Int) {
        isSeeking = false
        MusicPlayer.seekTo(value)
        progressViewUpdateHelper.start()
    }

    protected open fun onCreatePlayerAnimator(): PlayerAnimator? = null

    abstract fun onSongInfoChanged(song: Song)

    abstract fun onQueueInfoChanged(newInfo: String?)

    abstract fun onUpdatePlayPause(isPlaying: Boolean)

    abstract fun onUpdateRepeatMode(repeatMode: Int)

    abstract fun onUpdateShuffleMode(shuffleMode: Int)

    /**
     * Called to notify that the player has been expanded.
     */
    internal open fun onShow() {
        isShown = true
        playerAnimator?.start()
    }

    /**
     * Called to notify that the player has been collapsed.
     */
    internal open fun onHide() {
        isShown = false
        playerAnimator?.prepare()
    }

    override fun onResume() {
        super.onResume()
        if (isShown && playerAnimator?.isPrepared == true) {
            onShow()
        } else if (!isShown && playerAnimator?.isPrepared == false) {
            onHide()
        }
        progressViewUpdateHelper.start()
    }

    override fun onPause() {
        super.onPause()
        progressViewUpdateHelper.stop()
    }

    /**
     * Called to initialize style-UI colors.
     *
     * This method will not be called automatically by the base [AbsPlayerFragment] or its presenter.
     * Is responsibility of the player fragment to call this method manually according to its color
     * styling preferences.
     */
    abstract fun setColors(backgroundColor: Int, primaryControlColor: Int, secondaryControlColor: Int)

    protected fun setViewAction(view: View, action: NowPlayingAction) {
        view.setOnClickListener {
            playerFragment?.onQuickActionEvent(action)
        }
    }

    protected fun getSongArtist(song: Song) =
        playerFragment?.getSongArtist(song)

    protected fun isExtraInfoEnabled() =
        playerFragment?.isExtraInfoEnabled() ?: false

    protected fun getExtraInfoString(song: Song) =
        playerFragment?.getExtraInfoString(song)

    companion object {
        const val SLIDER_ANIMATION_TIME: Long = 400
    }
}