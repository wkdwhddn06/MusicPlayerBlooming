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

@file:SuppressLint("DiscouragedApi", "InternalInsetResource")

package com.uniqtech.musicplayer.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.ShapeAppearanceModel
import com.uniqtech.musicplayer.extensions.files.readString
import com.uniqtech.musicplayer.extensions.resources.getDrawableCompat
import com.uniqtech.musicplayer.extensions.resources.getTinted
import com.uniqtech.musicplayer.model.theme.AppTheme
import com.uniqtech.musicplayer.util.AutoDownloadMetadataPolicy
import com.uniqtech.musicplayer.util.Preferences

val Context.fileProviderAuthority: String
    get() = "$packageName.provider"

fun Float.dp(context: Context) = dp(context.resources)
fun Int.dp(context: Context) = dp(context.resources)

fun Float.dp(resources: Resources): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
fun Int.dp(resources: Resources): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

private val Resources.isNightMode: Boolean
    get() = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * Indicates if the device is landscaped.
 *
 * Currently, the app *does not support* landscape mode, but
 * it might do it in the future.
 */
val Resources.isLandscape: Boolean
    get() = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Indicates if the app is running on a **Android Auto** environemnt.
 */
val Resources.isCarMode: Boolean
    get() = configuration.uiMode == Configuration.UI_MODE_TYPE_CAR

val Resources.isTablet: Boolean
    get() = configuration.smallestScreenWidthDp >= 600

val Resources.isScreenLarge: Boolean
    get() = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_LARGE

val Context.isNightMode: Boolean
    get() = resources.isNightMode

fun Fragment.dip(id: Int) = resources.getDimensionPixelSize(id)

fun Context.dip(id: Int) = resources.getDimensionPixelSize(id)

fun Context.openUrl(url: String) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {}
}

fun Context.webSearch(vararg keys: String?) {
    val stringBuilder = StringBuilder()
    for (key in keys) {
        stringBuilder.append(key)
        stringBuilder.append(" ")
    }
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, stringBuilder.trim().toString())
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.isOnline(requestOnlyWifi: Boolean): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = cm.getNetworkCapabilities(cm.activeNetwork)
    if (networkCapabilities != null) {
        return if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            true
        } else networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !requestOnlyWifi
    }
    return false
}

fun Context.isAllowedToDownloadMetadata() = Preferences.autoDownloadMetadataPolicy.let { policy ->
    policy != AutoDownloadMetadataPolicy.NEVER && isOnline(AutoDownloadMetadataPolicy.ONLY_WIFI == policy)
}

fun Context.onUI(action: () -> Unit) {
    if (this is Activity) {
        runOnUiThread { action() }
    } else {
        Handler(Looper.getMainLooper()).post(action)
    }
}

fun Context.showToast(textId: Int, duration: Int = Toast.LENGTH_SHORT) {
    if (textId == 0)
        return

    showToast(getString(textId), duration)
}

fun Context.showToast(text: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (text.isNullOrEmpty())
        return

    if (Looper.myLooper() != Looper.getMainLooper()) {
        onUI { Toast.makeText(this, text, duration).show() }
    } else {
        Toast.makeText(this, text, duration).show()
    }
}

fun Context.createNotificationChannel(
    channelId: String,
    channelName: String,
    channelDescription: String?,
    notificationManager: NotificationManager = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
): NotificationChannel {
    var notificationChannel = notificationManager.getNotificationChannel(channelId)
    if (notificationChannel == null) {
        notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
            description = channelDescription
            enableLights(false)
            enableVibration(false)
        }.also {
            notificationManager.createNotificationChannel(it)
        }
    }
    return notificationChannel
}

fun Context.readStringFromAsset(assetName: String): String? =
    runCatching { assets.open(assetName).readString() }.getOrNull()

fun Context.getTintedDrawable(@DrawableRes resId: Int, @ColorInt color: Int): Drawable? =
    getDrawableCompat(resId).getTinted(color)

@Suppress("DEPRECATION")
fun Context.getScreenSize(): Point {
    if (hasR()) {
        val windowMetrics = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
        val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
        )
        val bounds = windowMetrics.bounds
        val insetsHeight = insets.top + insets.bottom
        val insetsWidth = insets.left + insets.right
        return Point(bounds.width() - insetsWidth, bounds.height() - insetsHeight)
    }
    return (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.let { display ->
        Point().also { point ->
            display.getSize(point)
        }
    }
}

fun Context.getShapeAppearanceModel(shapeAppearanceId: Int, shapeAppearanceOverlayId: Int = 0) =
    ShapeAppearanceModel.builder(this, shapeAppearanceId, shapeAppearanceOverlayId).build()

fun Context.getNavigationBarHeight(): Int {
    var result = 0
    val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    if (resourceId > 0) {
        result = dip(resourceId)
    }
    return result
}

fun Context.plurals(@PluralsRes resId: Int, quantity: Int): String {
    return try {
        resources.getQuantityString(resId, quantity, quantity)
    } catch (e: Resources.NotFoundException) {
        e.printStackTrace()
        quantity.toString()
    }
}

/**
 * Try to resolve the given color attribute against *this* [Context]'s theme.
 */
@ColorInt
fun Context.resolveColor(@AttrRes colorAttr: Int, @ColorInt fallback: Int = Color.TRANSPARENT) =
    MaterialColors.getColor(this, colorAttr, fallback)

fun Context.createAppTheme() = AppTheme.createAppTheme(this)