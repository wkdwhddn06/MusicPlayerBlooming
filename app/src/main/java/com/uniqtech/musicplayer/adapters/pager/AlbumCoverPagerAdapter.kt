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

package com.uniqtech.musicplayer.adapters.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.pager.AlbumCoverPagerAdapter.AlbumCoverFragment.ColorReceiver
import com.uniqtech.musicplayer.extensions.EXTRA_SONG
import com.uniqtech.musicplayer.extensions.glide.asBitmapPalette
import com.uniqtech.musicplayer.extensions.glide.getSongGlideModel
import com.uniqtech.musicplayer.extensions.glide.songOptions
import com.uniqtech.musicplayer.extensions.withArgs
import com.uniqtech.musicplayer.glide.BoomingColoredTarget
import com.uniqtech.musicplayer.helper.color.MediaNotificationProcessor
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.util.Preferences

class AlbumCoverPagerAdapter(fm: FragmentManager, private val dataSet: List<Song>) :
    CustomFragmentStatePagerAdapter(fm) {

    private var currentPaletteReceiver: ColorReceiver? = null
    private var currentColorReceiverPosition = -1

    override fun getItem(position: Int): Fragment {
        return AlbumCoverFragment.newInstance(dataSet[position])
    }

    override fun getCount(): Int {
        return dataSet.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val o = super.instantiateItem(container, position)
        if (currentPaletteReceiver != null && currentColorReceiverPosition == position) {
            receiveColor(currentPaletteReceiver!!, currentColorReceiverPosition)
        }
        return o
    }

    /**
     * Only the latest passed [AlbumCoverFragment.ColorReceiver] is guaranteed to receive a response
     */
    fun receiveColor(paletteReceiver: ColorReceiver, @ColorInt position: Int) {
        val fragment = getFragment(position) as AlbumCoverFragment?
        if (fragment != null) {
            currentPaletteReceiver = null
            currentColorReceiverPosition = -1
            fragment.receivePalette(paletteReceiver, position)
        } else {
            currentPaletteReceiver = paletteReceiver
            currentColorReceiverPosition = position
        }
    }

    class AlbumCoverFragment : Fragment() {

        private var isColorReady = false
        private lateinit var color: MediaNotificationProcessor
        private lateinit var song: Song
        private var colorReceiver: ColorReceiver? = null
        private var request = 0

        private var target: Target<*>? = null
        private var albumCover: ImageView? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            song = BundleCompat.getParcelable(requireArguments(), EXTRA_SONG, Song::class.java)!!
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            var layoutRes = Preferences.nowPlayingScreen.albumCoverLayoutRes
            if (layoutRes == null) {
                layoutRes = R.layout.fragment_album_cover
            }
            return inflater.inflate(layoutRes, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            albumCover = view.findViewById(R.id.player_image)
            forceSquareAlbumCover(false)
            loadAlbumCover()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            Glide.with(this).clear(target)
            colorReceiver = null
        }

        private fun loadAlbumCover() {
            Glide.with(this).clear(target)

            if (albumCover != null) {
                target = Glide.with(this)
                    .asBitmapPalette()
                    .load(song.getSongGlideModel())
                    .songOptions(song)
                    .dontAnimate()
                    .into(object : BoomingColoredTarget(albumCover!!) {
                        override fun onColorReady(colors: MediaNotificationProcessor) {
                            setPalette(colors)
                        }
                    })
            }
        }

        private fun forceSquareAlbumCover(forceSquareAlbumCover: Boolean) {
            albumCover?.scaleType =
                if (forceSquareAlbumCover) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        }

        private fun setPalette(color: MediaNotificationProcessor) {
            this.color = color
            isColorReady = true
            if (colorReceiver != null) {
                colorReceiver!!.onColorReady(color, request)
                colorReceiver = null
            }
        }

        fun receivePalette(paletteReceiver: ColorReceiver, request: Int) {
            if (isColorReady) {
                paletteReceiver.onColorReady(color, request)
            } else {
                this.colorReceiver = paletteReceiver
                this.request = request
            }
        }

        interface ColorReceiver {
            fun onColorReady(color: MediaNotificationProcessor, request: Int)
        }

        companion object {
            fun newInstance(song: Song) = AlbumCoverFragment().withArgs {
                putParcelable(EXTRA_SONG, song)
            }
        }
    }
}