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

package com.uniqtech.musicplayer.glide.palette;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.uniqtech.musicplayer.util.BoomingColorUtil;

public class BitmapPaletteTranscoder implements ResourceTranscoder<Bitmap,  BitmapPaletteWrapper> {

    @Override
  public Resource<BitmapPaletteWrapper> transcode(@NonNull Resource<Bitmap> toTranscode, @NonNull Options options) {
    Bitmap bitmap = toTranscode.get();
    BitmapPaletteWrapper bitmapPaletteWrapper = new BitmapPaletteWrapper(bitmap,
            BoomingColorUtil.generatePalette(bitmap));
    return new BitmapPaletteResource(bitmapPaletteWrapper);
  }
}
