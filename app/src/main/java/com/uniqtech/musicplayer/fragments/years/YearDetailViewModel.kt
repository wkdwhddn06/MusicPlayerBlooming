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

package com.uniqtech.musicplayer.fragments.years

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniqtech.musicplayer.model.ReleaseYear
import com.uniqtech.musicplayer.repository.Repository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class YearDetailViewModel(private val repository: Repository, private val year: Int) : ViewModel() {

    private val _year = MutableLiveData<ReleaseYear>()
    fun getYear(): LiveData<ReleaseYear> = _year

    init {
        loadDetail()
    }

    fun loadDetail() = viewModelScope.launch(IO) {
        _year.postValue(repository.yearById(year))
    }
}