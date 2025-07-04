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

package com.uniqtech.musicplayer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.interfaces.IStorageDeviceCallback
import com.uniqtech.musicplayer.model.StorageDevice

class StorageAdapter(
    val storageList: List<StorageDevice>,
    val storageClickListener: IStorageDeviceCallback
) : RecyclerView.Adapter<StorageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_storage, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindData(storageList[position])
    }

    override fun getItemCount(): Int {
        return storageList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val title: TextView = itemView.findViewById(R.id.title)

        fun bindData(storage: StorageDevice) {
            title.text = storage.name
            icon.setImageResource(storage.iconRes)
        }

        init {
            itemView.setOnClickListener {
                storageClickListener.storageDeviceClick(storageList[bindingAdapterPosition])
            }
        }
    }
}