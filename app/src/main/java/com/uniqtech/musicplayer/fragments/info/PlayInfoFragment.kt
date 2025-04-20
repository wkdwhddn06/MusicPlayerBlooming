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

package com.uniqtech.musicplayer.fragments.info

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.core.view.updateMargins
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.divider.MaterialDivider
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.databinding.FragmentPlayInfoBinding
import com.uniqtech.musicplayer.extensions.*
import com.uniqtech.musicplayer.extensions.glide.albumOptions
import com.uniqtech.musicplayer.extensions.glide.artistOptions
import com.uniqtech.musicplayer.extensions.glide.getAlbumGlideModel
import com.uniqtech.musicplayer.extensions.glide.getArtistGlideModel
import com.uniqtech.musicplayer.extensions.media.displayName
import com.uniqtech.musicplayer.extensions.media.timesStr
import com.uniqtech.musicplayer.extensions.resources.show
import com.uniqtech.musicplayer.extensions.utilities.dateStr
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.model.Album
import com.uniqtech.musicplayer.model.Artist
import com.uniqtech.musicplayer.model.Song
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * @author Christians M. A. (mardous)
 */
class PlayInfoFragment : AbsMainActivityFragment(R.layout.fragment_play_info) {

    private val args: PlayInfoFragmentArgs by navArgs()
    private val viewModel: InfoViewModel by viewModel()

    private var _binding: FragmentPlayInfoBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayInfoBinding.bind(view)

        materialSharedAxis(view)
        setSupportActionBar(binding.toolbar)
        view.applyScrollableContentInsets(binding.scrollView, addedPadding = dip(R.dimen.info_view_margin_horizontal))

        if (args.isArtist) {
            viewModel.loadArtist(args.id, args.name).observe(viewLifecycleOwner) { artist ->
                if (artist == Artist.empty) {
                    getOnBackPressedDispatcher().onBackPressed()
                } else {
                    binding.itemName.text = artist.displayName()

                    Glide.with(this)
                        .asBitmap()
                        .load(artist.getArtistGlideModel())
                        .artistOptions(artist)
                        .into(binding.albumCover)

                    loadInfo(artist.songs)
                }
            }
        } else {
            viewModel.loadAlbum(args.id).observe(viewLifecycleOwner) { album ->
                if (album == Album.empty) {
                    getOnBackPressedDispatcher().onBackPressed()
                } else {
                    binding.itemName.text = album.name

                    Glide.with(this)
                        .asBitmap()
                        .load(album.getAlbumGlideModel())
                        .albumOptions(album)
                        .into(binding.albumCover)

                    loadInfo(album.songs)
                }
            }
        }
    }

    private fun loadInfo(songs: List<Song>) {
        viewModel.playInfo(songs).observe(viewLifecycleOwner) {
            binding.progressIndicator.hide()

            binding.playCount.setText(it.playCount.timesStr(requireContext()))
            binding.skipCount.setText(it.skipCount.timesStr(requireContext()))
            binding.lastPlayDate.setText(requireContext().dateStr(it.lastPlayDate))

            if (it.mostPlayedTracks.isNotEmpty()) {
                binding.mostPlayedTracks.show()
                binding.container.addDivider(requireContext())

                val context = requireContext()
                for (song in it.mostPlayedTracks) {
                    val playInfo = when {
                        song.playCount > 1 -> context.getString(
                            R.string.song_stat_label, song.playCount, context.dateStr(song.timePlayed)
                        )
                        song.playCount == 1 -> context.getString(
                            R.string.song_stat_one_time_label, context.dateStr(song.timePlayed)
                        )
                        else -> context.getString(R.string.song_stat_never_label)
                    }
                    binding.container.addInfo(layoutInflater, song.title, playInfo, vertical = true)
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
        when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }
            else -> false
        }

    private fun ViewGroup.addDivider(
        context: Context,
        marginTop: Float = 16f,
        marginBottom: Float = 16f
    ) {
        addView(MaterialDivider(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                updateMargins(
                    left = resources.getDimensionPixelSize(R.dimen.info_view_margin_horizontal),
                    top = marginTop.dp(context),
                    right = resources.getDimensionPixelSize(R.dimen.info_view_margin_horizontal),
                    bottom = marginBottom.dp(context)
                )
            }
        })
    }

    private fun ViewGroup.addInfo(
        inflater: LayoutInflater,
        title: CharSequence,
        info: CharSequence?,
        vertical: Boolean = false
    ): Boolean {
        if (info.isNullOrEmpty()) {
            return false
        }
        val infoView = if (vertical) {
            inflater.inflate(R.layout.info_item_vertical, this, false)
        } else {
            inflater.inflate(R.layout.info_item_horizontal, this, false)
        }
        infoView.findViewById<TextView>(R.id.title).text = title
        infoView.findViewById<TextView>(R.id.text).text = info
        addView(infoView)
        return true
    }
}