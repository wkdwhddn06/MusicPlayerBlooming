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

package com.mardous.booming.adapters

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.mardous.booming.R
import com.mardous.booming.database.PlaylistWithSongs
import com.mardous.booming.extensions.glide.*
import com.mardous.booming.extensions.media.*
import com.mardous.booming.helper.menu.OnClickMenu
import com.mardous.booming.interfaces.ISearchCallback
import com.mardous.booming.model.Album
import com.mardous.booming.model.Artist
import com.mardous.booming.model.Genre
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicPlayer
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class SearchAdapter(
    private val activity: AppCompatActivity,
    private val requestManager: RequestManager,
    dataSet: List<Any>,
    private val callback: ISearchCallback? = null
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    var dataSet by Delegates.observable(dataSet) { _: KProperty<*>, _: List<Any>, _: List<Any> ->
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        if (dataSet[position] is Album) return ALBUM
        if (dataSet[position] is Artist) return ARTIST
        if (dataSet[position] is Song) return SONG
        if (dataSet[position] is PlaylistWithSongs) return PLAYLIST
        return if (dataSet[position] is Genre) GENRE else HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (viewType == HEADER) {
            return ViewHolder(
                LayoutInflater.from(activity).inflate(R.layout.sub_header, parent, false), viewType
            )
        } else if (viewType == PLAYLIST) {
            return ViewHolder(
                LayoutInflater.from(activity).inflate(R.layout.item_list_single_row, parent, false), viewType
            )
        }
        return ViewHolder(LayoutInflater.from(activity).inflate(R.layout.item_list, parent, false), viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            ALBUM -> {
                val album = dataSet[position] as Album
                holder.title?.text = album.name.displayArtistName()
                holder.text?.text = album.albumInfo()
                holder.image?.let {
                    it.transitionName = album.id.toString()
                    requestManager.asDrawable()
                        .load(album.getAlbumGlideModel())
                        .transition(getDefaultGlideTransition())
                        .albumOptions(album)
                        .into(it)
                }
            }

            ARTIST -> {
                val artist = dataSet[position] as Artist
                holder.title?.text = artist.displayName()
                holder.text?.text = artist.artistInfo(activity)
                holder.image?.let {
                    it.transitionName = if (artist.isAlbumArtist) artist.name else artist.id.toString()
                    requestManager.asBitmap()
                        .load(artist.getArtistGlideModel())
                        .transition(getDefaultGlideTransition())
                        .artistOptions(artist)
                        .into(it)
                }
            }

            SONG -> {
                val song = dataSet[position] as Song
                holder.title?.text = song.title
                holder.text?.text = song.songInfo()
            }

            PLAYLIST -> {
                val playlist = dataSet[position] as PlaylistWithSongs
                holder.title?.text = playlist.playlistEntity.playlistName
            }

            GENRE -> {
                val genre = dataSet[position] as Genre
                holder.title?.text = genre.name
                holder.text?.text = genre.songCount.songsStr(activity)
            }

            else -> holder.title?.text = dataSet[position].toString()
        }
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    inner class ViewHolder(itemView: View, itemViewType: Int) :
        com.mardous.booming.adapters.base.MediaEntryViewHolder(itemView) {

        private val sharedElements: Array<Pair<View, String>>
            get() = arrayOf(image!! to image.transitionName)

        override fun onClick(view: View) {
            val item = dataSet[layoutPosition]
            when (itemViewType) {
                ALBUM -> callback?.albumClick(item as Album, sharedElements)
                ARTIST -> callback?.artistClick(item as Artist, sharedElements)
                SONG -> MusicPlayer.openQueue(listOf(item as Song))
                GENRE -> callback?.genreClick(item as Genre)
                PLAYLIST -> callback?.playlistClick(item as PlaylistWithSongs)
            }
        }

        override fun onLongClick(view: View): Boolean {
            return false
        }

        private val menuRes = when (itemViewType) {
            SONG -> R.menu.menu_item_song
            ALBUM -> R.menu.menu_item_album
            ARTIST -> R.menu.menu_item_artist
            PLAYLIST -> R.menu.menu_item_playlist
            else -> 0
        }

        init {
            itemView.setOnLongClickListener(null)

            if (itemViewType != ALBUM && itemViewType != ARTIST) {
                image?.isVisible = false
            }
        }
    }

    companion object {
        private const val HEADER = 0
        private const val ALBUM = 1
        private const val ARTIST = 2
        private const val SONG = 3
        private const val PLAYLIST = 4
        private const val GENRE = 5
    }
}