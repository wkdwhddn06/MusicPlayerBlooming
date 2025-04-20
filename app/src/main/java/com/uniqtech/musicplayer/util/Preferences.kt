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

package com.uniqtech.musicplayer.util

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.viewpager.widget.ViewPager
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationBarView.LabelVisibility
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.appContext
import com.uniqtech.musicplayer.extensions.files.getCanonicalPathSafe
import com.uniqtech.musicplayer.extensions.hasQ
import com.uniqtech.musicplayer.extensions.hasS
import com.uniqtech.musicplayer.extensions.utilities.*
import com.uniqtech.musicplayer.model.*
import com.uniqtech.musicplayer.model.theme.AppTheme
import com.uniqtech.musicplayer.model.theme.NowPlayingScreen
import com.uniqtech.musicplayer.transform.*
import com.uniqtech.musicplayer.views.TopAppBarLayout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * @author Christians M. A. (mardous)
 */
object Preferences : KoinComponent {

    private val preferences: SharedPreferences by inject()

    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getGeneralTheme(isBlackMode: Boolean): String {
        return if (isBlackMode) {
            GeneralTheme.BLACK
        } else {
            preferences.requireString(GENERAL_THEME, GeneralTheme.AUTO)
        }
    }

    var generalTheme: String
        get() = getGeneralTheme(preferences.getBoolean(BLACK_THEME, false))
        set(value) = preferences.edit { putString(GENERAL_THEME, value) }

    fun getThemeMode(themeName: String) = when (themeName) {
        GeneralTheme.LIGHT -> AppTheme.Mode.Light
        GeneralTheme.DARK -> AppTheme.Mode.Dark
        GeneralTheme.BLACK -> AppTheme.Mode.Black
        else -> AppTheme.Mode.FollowSystem
    }

    fun getDayNightMode(themeName: String = Preferences.generalTheme) = when (themeName) {
        GeneralTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        GeneralTheme.DARK,
        GeneralTheme.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> if (hasQ()) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        }
    }

    val materialYou: Boolean
        get() = preferences.getBoolean(MATERIAL_YOU, hasS())

    val isCustomFont: Boolean
        get() = preferences.getBoolean(USE_CUSTOM_FONT, true)

    val appBarMode: TopAppBarLayout.AppBarMode
        get() = when (preferences.requireString(APPBAR_MODE, AppBarMode.COMPACT)) {
            AppBarMode.COMPACT -> TopAppBarLayout.AppBarMode.SIMPLE
            AppBarMode.EXPANDED -> TopAppBarLayout.AppBarMode.COLLAPSING
            else -> TopAppBarLayout.AppBarMode.SIMPLE
        }

    var libraryCategories: List<CategoryInfo>
        get() = preferences.nullString(LIBRARY_CATEGORIES).deserialize(
            getDefaultLibraryCategoryInfos()
        )
        set(categories) = preferences.edit { putString(LIBRARY_CATEGORIES, categories.serialize()) }

    fun getDefaultLibraryCategoryInfos() =
        CategoryInfo.Category.entries.mapIndexed { index, category ->
            val included = listOf(
                CategoryInfo.Category.Songs,
                CategoryInfo.Category.Albums,
                CategoryInfo.Category.Artists,
            )
            val isVisible = included.contains(category)
            CategoryInfo(category, isVisible)
        }

    val isRememberLastPage: Boolean
        get() = preferences.getBoolean(REMEMBER_LAST_PAGE, true)

    var lastPage: Int
        get() = preferences.getInt(LAST_PAGE, 0)
        set(value) = preferences.edit { putInt(LAST_PAGE, value) }

    @LabelVisibility
    val bottomTitlesMode: Int
        get() = when (preferences.nullString(TAB_TITLES_MODE)) {
            BottomTitlesMode.SELECTED -> NavigationBarView.LABEL_VISIBILITY_SELECTED
            BottomTitlesMode.LABELED -> NavigationBarView.LABEL_VISIBILITY_LABELED
            BottomTitlesMode.UNLABELED -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
            else -> NavigationBarView.LABEL_VISIBILITY_AUTO
        }

    var queueQuickAction: QueueQuickAction
        get() = preferences.enumValue(QUEUE_QUICK_ACTION, QueueQuickAction.Save)
        set(value) = preferences.edit { putString(QUEUE_QUICK_ACTION, value.name) }

    var nowPlayingScreen: NowPlayingScreen
        get() = preferences.enumValue(NOW_PLAYING_SCREEN, NowPlayingScreen.Default)
        set(value) = preferences.edit { putString(NOW_PLAYING_SCREEN, value.name) }

    val openOnPlay: Boolean
        get() = preferences.getBoolean(OPEN_ON_PLAY, false)

    val extraControls: Boolean
        get() = preferences.getBoolean(ADD_EXTRA_CONTROLS, false)

    val swipeDownToDismiss: Boolean
        get() = preferences.getBoolean(SWIPE_TO_DISMISS, false)

    var showLyricsOnCover: Boolean
        get() = preferences.getBoolean(LYRICS_ON_COVER, true)
        set(value) = preferences.edit { putBoolean(LYRICS_ON_COVER, value) }

    val allowCoverSwiping: Boolean
        get() = preferences.getBoolean(LEFT_RIGHT_SWIPING, true)

    val isCarousalEffect: Boolean
        get() = preferences.getBoolean(CAROUSAL_EFFECT, true)

    val coverSwipingEffect: ViewPager.PageTransformer?
        get() = when (preferences.nullString(COVER_SWIPING_EFFECT)) {
            CoverSwipingEffect.CASCADING -> CascadingPageTransformer()
            CoverSwipingEffect.DEPTH -> DepthTransformation()
            CoverSwipingEffect.HINGE -> HingeTransformation()
            CoverSwipingEffect.HORIZONTAL_FLIP -> HorizontalFlipTransformation()
            CoverSwipingEffect.VERTICAL_FLIP -> VerticalFlipTransformation()
            CoverSwipingEffect.STACK -> VerticalStackTransformer()
            CoverSwipingEffect.ZOOM_OUT -> ZoomOutPageTransformer()
            else -> null
        }

    val coverDoubleTapAction: NowPlayingAction
        get() = preferences.enumValue(COVER_DOUBLE_TAP_ACTION, NowPlayingAction.WebSearch)

    val coverLongPressAction: NowPlayingAction
        get() = preferences.enumValue(COVER_LONG_PRESS_ACTION, NowPlayingAction.SaveAlbumCover)

    val animateControls: Boolean
        get() = preferences.getBoolean(ANIMATE_PLAYER_CONTROL, true)

    val circularPlayButton: Boolean
        get() = preferences.getBoolean(CIRCLE_PLAY_BUTTON, false)

    val displayAlbumTitle
        get() = preferences.getBoolean(DISPLAY_ALBUM_TITLE, true)

    val displayExtraInfo: Boolean
        get() = preferences.getBoolean(DISPLAY_EXTRA_INFO, false)

    var nowPlayingExtraInfoList: List<NowPlayingInfo>
        get() = preferences.nullString(EXTRA_INFO).deserialize(getDefaultNowPlayingInfo())
        set(value) = preferences.edit { putString(EXTRA_INFO, value.serialize()) }

    fun getDefaultNowPlayingInfo(): List<NowPlayingInfo> =
        NowPlayingInfo.Info.entries.map { tag ->
            NowPlayingInfo(tag, tag == NowPlayingInfo.Info.Format || tag == NowPlayingInfo.Info.Bitrate || tag == NowPlayingInfo.Info.SampleRate)
        }

    var preferRemainingTime: Boolean
        get() = preferences.getBoolean(PREFER_REMAINING_TIME, false)
        set(value) = preferences.edit { putBoolean(PREFER_REMAINING_TIME, value) }

    var preferAlbumArtistName: Boolean
        get() = preferences.getBoolean(PREFER_ALBUM_ARTIST_NAME, false)
        set(value) = preferences.edit { putBoolean(PREFER_ALBUM_ARTIST_NAME, value) }

    val gaplessPlayback: Boolean
        get() = preferences.getBoolean(GAPLESS_PLAYBACK, false)

    val autoPlayOnSkip: Boolean
        get() = preferences.getBoolean(AUTO_PLAY_ON_SKIP, true)

    val rewindWithBack: Boolean
        get() = preferences.getBoolean(REWIND_WITH_BACK, true)

    val fastForward: Boolean
        get() = preferences.getBoolean(FAST_FORWARD, true)

    val fastBackward: Boolean
        get() = preferences.getBoolean(FAST_BACKWARD, true)

    val replayGainSourceMode: Byte
        get() = when (preferences.getString(REPLAYGAIN_SOURCE_MODE, "")) {
            ReplayGainSourceMode.TRACK -> ReplayGainSourceMode.MODE_TRACK
            ReplayGainSourceMode.ALBUM -> ReplayGainSourceMode.MODE_ALBUM
            else -> ReplayGainSourceMode.MODE_NONE
        }

    fun getReplayGainValue(withTag: Boolean): Float = when {
        withTag -> preferences.getFloat(REPLAYGAIN_PREAMP_WITH_TAG, 0f)
        else -> preferences.getFloat(REPLAYGAIN_PREAMP_WITHOUT_TAG, 0f)
    }

    val audioDucking: Boolean
        get() = preferences.getBoolean(AUDIO_DUCKING, true)

    val pauseOnTransientFocusLoss: Boolean
        get() = preferences.getBoolean(PAUSE_ON_TRANSIENT_FOCUS_LOSS, true)

    val ignoreAudioFocus: Boolean
        get() = preferences.getBoolean(IGNORE_AUDIO_FOCUS, false)

    val queueNextSequentially: Boolean
        get() = preferences.requireString(QUEUE_NEXT_MODE, "1") == "1"

    val rememberShuffleMode: Boolean
        get() = preferences.getBoolean(REMEMBER_SHUFFLE_MODE, true)

    val albumShuffleMode: String
        get() = preferences.requireString(ALBUM_SHUFFLE_MODE, SelectedShuffleMode.SHUFFLE_SONGS)

    val artistShuffleMode: String
        get() = preferences.requireString(ARTIST_SHUFFLE_MODE, SelectedShuffleMode.SHUFFLE_SONGS)

    fun isResumeOnConnect(bluetooth: Boolean) = when {
        bluetooth -> preferences.getBoolean(RESUME_ON_BLUETOOTH_CONNECT, false)
        else -> preferences.getBoolean(RESUME_ON_CONNECT, false)
    }

    fun isPauseOnDisconnect(bluetooth: Boolean) = when {
        bluetooth -> preferences.getBoolean(PAUSE_ON_BLUETOOTH_DISCONNECT, false)
        else -> preferences.getBoolean(PAUSE_ON_DISCONNECT, false)
    }

    val autoDownloadMetadataPolicy: String
        get() = preferences.requireString(AUTO_DOWNLOAD_METADATA_POLICY, appStr(R.string.default_metadata_policy))

    val ignoreMediaStore: Boolean
        get() = preferences.getBoolean(IGNORE_MEDIA_STORE, false)

    val useFolderImages: Boolean
        get() = preferences.getBoolean(USE_FOLDER_ART, true)

    val downloadArtistImages: Boolean
        get() = preferences.getBoolean(ALLOW_ONLINE_ARTIST_IMAGES, appBool(R.bool.default_artist_images_download))

    val preferredArtistImageSize: String
        get() = preferences.requireString(PREFERRED_ARTIST_IMAGE_SIZE, ImageSize.MEDIUM)

    var onlyAlbumArtists: Boolean
        get() = preferences.getBoolean(ONLY_ALBUM_ARTISTS, true)
        set(value) = preferences.edit { putBoolean(ONLY_ALBUM_ARTISTS, value) }

    val trashMusicFiles: Boolean
        get() = preferences.getBoolean(TRASH_MUSIC_FILES, false)

    val historyEnabled: Boolean
        get() = preferences.getBoolean(ENABLE_HISTORY, true)

    fun getLastAddedCutoff(context: Context = appContext()): Cutoff =
        getCutoff(context, LAST_ADDED_CUTOFF, true)

    fun getHistoryCutoff(context: Context = appContext()): Cutoff =
        getCutoff(context, HISTORY_CUTOFF)

    private fun getCutoff(
        context: Context,
        preferenceKey: String,
        asSeconds: Boolean = false
    ): Cutoff {
        val cutoff = preferences.requireString(preferenceKey, "")
        val description = when (cutoff) {
            PlaylistCutoff.TODAY -> context.getString(R.string.today)
            PlaylistCutoff.YESTERDAY -> context.getString(R.string.yesterday)
            PlaylistCutoff.THIS_WEEK -> context.getString(R.string.this_week)
            PlaylistCutoff.PAST_THREE_MONTHS -> context.getString(R.string.past_three_months)
            PlaylistCutoff.LAST_YEAR -> context.getString(R.string.this_year)
            PlaylistCutoff.THIS_MONTH -> context.getString(R.string.this_month)
            else -> context.getString(R.string.this_month)
        }
        val interval = calendarSingleton.getCutoffTimeMillis(cutoff).let { cutoffTimeMillis ->
            if (asSeconds) cutoffTimeMillis / 1000 else cutoffTimeMillis
        }
        return Cutoff(description, interval)
    }

    val whitelistEnabled: Boolean
        get() = preferences.getBoolean(WHITELIST_ENABLED, true)

    var blacklistEnabled: Boolean
        get() = preferences.getBoolean(BLACKLIST_ENABLED, true)
        set(value) = preferences.edit { putBoolean(BLACKLIST_ENABLED, value) }

    val minimumSongCountForArtist: Int
        get() = preferences.getInt(ARTIST_MINIMUM_SONGS, 1)

    val minimumSongCountForAlbum: Int
        get() = preferences.getInt(ALBUM_MINIMUM_SONGS, 1)

    val minimumSongDuration: Int
        get() = preferences.getInt(MINIMUM_SONG_DURATION, 30)

    val albumArtOnLockscreenAllowed: Boolean
        get() = preferences.getBoolean(ALBUM_ART_ON_LOCK_SCREEN, true)

    val blurredAlbumArtAllowed: Boolean
        get() = preferences.getBoolean(BLURRED_ALBUM_ART, false)

    val publicBroadcast: Boolean
        get() = preferences.getBoolean(SEND_PUBLIC_BROADCAST, true)

    val classicNotification: Boolean
        get() = preferences.getBoolean(CLASSIC_NOTIFICATION, false)

    val coloredNotification: Boolean
        get() = preferences.getBoolean(COLORED_NOTIFICATION, false)

    val notificationExtraTextLine: String
        get() = preferences.requireString(
            NOTIFICATION_EXTRA_TEXT_LINE,
            NotificationExtraText.ALBUM_NAME
        )

    val notificationPriority: String
        get() = preferences.requireString(NOTIFICATION_PRIORITY, NotificationPriority.MAXIMUM)

    val updateSearchMode: String
        get() = preferences.requireString(UPDATE_SEARCH_MODE, UpdateSearchMode.WEEKLY)

    val updateOnlyWifi: Boolean
        get() = preferences.getBoolean(ONLY_WIFI, false)

    val experimentalUpdates: Boolean
        get() = preferences.getBoolean(EXPERIMENTAL_UPDATES, false)

    var lastUpdateSearch: Long
        get() = preferences.getLong(LAST_UPDATE_SEARCH, -1)
        set(value) = preferences.edit { putLong(LAST_UPDATE_SEARCH, value) }

    var lastUpdateId: Long
        get() = preferences.getLong(LAST_UPDATE_ID, -1)
        set(value) = preferences.edit { putLong(LAST_UPDATE_ID, value) }

    var startDirectory: File
        get() = File(preferences.requireString(START_DIRECTORY, FileUtil.getDefaultStartDirectory().path))
        set(file) = preferences.edit { putString(START_DIRECTORY, file.getCanonicalPathSafe()) }

    var savedArtworkCopyrightNoticeShown: Boolean
        get() = preferences.getBoolean(SAVED_ARTWORK_COPYRIGHT_NOTICE_SHOWN, false)
        set(value) = preferences.edit { putBoolean(SAVED_ARTWORK_COPYRIGHT_NOTICE_SHOWN, value) }

    var loudnessEnhancerWarningShown: Boolean
        get() = preferences.getBoolean(LOUDNESS_ENHANCER_WARNING_SHOWN, false)
        set(value) = preferences.edit { putBoolean(LOUDNESS_ENHANCER_WARNING_SHOWN, value) }

    var initializedBlacklist: Boolean
        get() = preferences.getBoolean(INITIALIZED_BLACKLIST, false)
        set(value) = preferences.edit { putBoolean(INITIALIZED_BLACKLIST, value) }

    var sAFSDCardUri: String?
        get() = preferences.nullString(SAF_SDCARD_URI)
        set(uri) = preferences.edit { putString(SAF_SDCARD_URI, uri) }

    var lastSleepTimerValue: Int
        get() = preferences.getInt(LAST_SLEEP_TIMER_VALUE, 30)
        set(value) = preferences.edit { putInt(LAST_SLEEP_TIMER_VALUE, value) }

    var nextSleepTimerElapsedRealTime: Long
        get() = preferences.getLong(NEXT_SLEEP_TIMER_ELAPSED_REALTIME, -1)
        set(value) = preferences.edit { putLong(NEXT_SLEEP_TIMER_ELAPSED_REALTIME, value) }

    var isSleepTimerFinishMusic: Boolean
        get() = preferences.getBoolean(SLEEP_TIMER_FINISH_SONG, false)
        set(value) = preferences.edit { putBoolean(SLEEP_TIMER_FINISH_SONG, value) }

    fun SharedPreferences.nullString(key: String): String? = getString(key, null)

    fun SharedPreferences.requireString(key: String, defaultValue: String): String =
        requireNotNull(getString(key, defaultValue))

    private inline fun <reified T : Enum<T>> SharedPreferences.enumValue(key: String, defaultValue: T): T =
        nullString(key)?.toEnum<T>() ?: defaultValue

    private fun appBool(resid: Int): Boolean = appContext().resources.getBoolean(resid)

    private fun appStr(resid: Int): String = appContext().getString(resid)
}

interface GeneralTheme {
    companion object {
        const val LIGHT = "light"
        const val DARK = "dark"
        const val BLACK = "black"
        const val AUTO = "auto"
    }
}

interface BottomTitlesMode {
    companion object {
        const val AUTO = "auto"
        const val SELECTED = "selected"
        const val LABELED = "labeled"
        const val UNLABELED = "unlabeled"
    }
}

interface AppBarMode {
    companion object {
        const val COMPACT = "compact"
        const val EXPANDED = "expanded"
    }
}

interface CoverSwipingEffect {
    companion object {
        const val CASCADING = "cascading"
        const val DEPTH = "depth"
        const val HINGE = "hinge"
        const val HORIZONTAL_FLIP = "horizontal_flip"
        const val VERTICAL_FLIP = "vertical_flip"
        const val STACK = "stack"
        const val ZOOM_OUT = "zoom-out"
    }
}

interface ReplayGainSourceMode {
    companion object {
        const val NONE = "none"
        const val MODE_NONE: Byte = 0
        const val TRACK = "track"
        const val MODE_TRACK: Byte = 1
        const val ALBUM = "album"
        const val MODE_ALBUM: Byte = 2
    }
}

interface SelectedShuffleMode {
    companion object {
        const val SHUFFLE_ARTISTS = "shuffle_artists"
        const val SHUFFLE_ALBUMS = "shuffle_albums"
        const val SHUFFLE_SONGS = "shuffle_songs"
        const val SHUFFLE_ALL = "shuffle_all"
    }
}


interface PlaylistCutoff {
    companion object {
        const val TODAY = "today"
        const val YESTERDAY = "yesterday"
        const val THIS_WEEK = "this_week"
        const val THIS_MONTH = "this_month"
        const val PAST_THREE_MONTHS = "past_three_months"
        const val LAST_YEAR = "last_year"
    }
}

interface AutoDownloadMetadataPolicy {
    companion object {
        const val ALWAYS = "always"
        const val ONLY_WIFI = "only_wifi"
        const val NEVER = "never"
    }
}

interface ImageSize {
    companion object {
        const val LARGE = "large"
        const val MEDIUM = "medium"
        const val SMALL = "small"
    }
}

interface NotificationExtraText {
    companion object {
        const val ALBUM_NAME = "album"
        const val ALBUM_ARTIST_NAME = "album_artist"
        const val ALBUM_AND_YEAR = "album_and_year"
        const val NEXT_SONG = "next_song"
    }
}

interface NotificationPriority {
    companion object {
        const val MAXIMUM = "maximum"
        const val HIGH = "high"
        const val NORMAL = "normal"
        const val LOW = "low"
    }
}

interface UpdateSearchMode {
    companion object {
        const val EVERY_DAY = "every_day"
        const val WEEKLY = "weekly"
        const val EVERY_FIFTEEN_DAYS = "every_fifteen_days"
        const val MONTHLY = "monthly"
        const val NEVER = "never"
    }
}

const val BLACK_THEME = "black_theme"
const val MATERIAL_YOU = "material_you"
const val USE_CUSTOM_FONT = "use_custom_font"
const val APPBAR_MODE = "appbar_mode"
const val GENERAL_THEME = "general_theme"
const val LIBRARY_CATEGORIES = "library_categories"
const val REMEMBER_LAST_PAGE = "remember_last_page"
const val TAB_TITLES_MODE = "tab_titles_mode"
const val LAST_PAGE = "last_page"
const val QUEUE_QUICK_ACTION = "play_queue_action"
const val NOW_PLAYING_SCREEN = "now_playing_screen"
const val OPEN_ON_PLAY = "open_on_play"
const val ADD_EXTRA_CONTROLS = "add_extra_controls"
const val SWIPE_TO_DISMISS = "swipe_to_dismiss"
const val LYRICS_ON_COVER = "lyrics_on_cover"
const val LEFT_RIGHT_SWIPING = "left_right_swiping"
const val CAROUSAL_EFFECT = "carousal_effect"
const val COVER_SWIPING_EFFECT = "cover_swiping_effect"
const val COVER_DOUBLE_TAP_ACTION = "cover_double_tap_action"
const val COVER_LONG_PRESS_ACTION = "cover_long_press_action"
const val ANIMATE_PLAYER_CONTROL = "animate_player_control"
const val CIRCLE_PLAY_BUTTON = "circle_play_button"
const val DISPLAY_ALBUM_TITLE = "display_album_title"
const val DISPLAY_EXTRA_INFO = "display_extra_info"
const val EXTRA_INFO = "now_playing_extra_info"
const val PREFER_REMAINING_TIME = "prefer_remaining_time"
const val PREFER_ALBUM_ARTIST_NAME = "prefer_album_artist_name_on_np"
const val PLAYBACK_SPEED = "playback_speed"
const val PLAYBACK_PITCH = "playback_pitch"
const val GAPLESS_PLAYBACK = "gapless_playback"
const val AUTO_PLAY_ON_SKIP = "auto_play_on_skip"
const val REWIND_WITH_BACK = "rewind_with_back"
const val FAST_FORWARD = "fast_forward"
const val FAST_BACKWARD = "fast_backward"
const val REPLAYGAIN_SOURCE_MODE = "replaygain_source_mode"
const val REPLAYGAIN_PREAMP = "replaygain_preamp"
const val REPLAYGAIN_PREAMP_WITH_TAG = "replaygain_preamp_with_tag"
const val REPLAYGAIN_PREAMP_WITHOUT_TAG = "replaygain_preamp_without_tag"
const val QUEUE_NEXT_MODE = "queue_next_mode"
const val REMEMBER_SHUFFLE_MODE = "remember_shuffle_mode"
const val ALBUM_SHUFFLE_MODE = "album_shuffle_mode"
const val ARTIST_SHUFFLE_MODE = "artist_shuffle_mode"
const val RESUME_ON_CONNECT = "resume_on_connect"
const val PAUSE_ON_DISCONNECT = "pause_on_disconnect"
const val RESUME_ON_BLUETOOTH_CONNECT = "resume_on_bluetooth_connect"
const val PAUSE_ON_BLUETOOTH_DISCONNECT = "pause_on_bluetooth_disconnect"
const val AUDIO_DUCKING = "audio_ducking"
const val IGNORE_AUDIO_FOCUS = "ignore_audio_focus"
const val PAUSE_ON_TRANSIENT_FOCUS_LOSS = "pause_on_transient_focus_loss"
const val AUTO_DOWNLOAD_METADATA_POLICY = "auto_download_metadata_policy"
const val IGNORE_MEDIA_STORE = "ignore_media_store"
const val USE_FOLDER_ART = "use_folder_art"
const val ALLOW_ONLINE_ARTIST_IMAGES = "allow_online_artist_images"
const val PREFERRED_ARTIST_IMAGE_SIZE = "preferred_artist_image_size"
const val ONLY_ALBUM_ARTISTS = "only_album_artists"
const val TRASH_MUSIC_FILES = "trash_music_files"
const val ENABLE_HISTORY = "enable_history_playlist"
const val HISTORY_CUTOFF = "history_interval"
const val LAST_ADDED_CUTOFF = "last_added_interval"
const val WHITELIST_ENABLED = "whitelist_enabled"
const val BLACKLIST_ENABLED = "blacklist_enabled"
const val ARTIST_MINIMUM_SONGS = "artist_minimum_songs"
const val ALBUM_MINIMUM_SONGS = "album_minimum_songs"
const val MINIMUM_SONG_DURATION = "minimum_song_duration"
const val SEND_PUBLIC_BROADCAST = "send_public_broadcast"
const val CLASSIC_NOTIFICATION = "classic_notification"
const val COLORED_NOTIFICATION = "colored_notification"
const val NOTIFICATION_EXTRA_TEXT_LINE = "notification_extra_text_line"
const val NOTIFICATION_PRIORITY = "notification_priority"
const val ALBUM_ART_ON_LOCK_SCREEN = "album_art_on_lock_screen"
const val BLURRED_ALBUM_ART = "blurred_album_art"
const val LANGUAGE_NAME = "language_name"
const val BACKUP_DATA = "backup_data"
const val RESTORE_DATA = "restore_data"
const val UPDATE_SEARCH_MODE = "update_search_mode"
const val ONLY_WIFI = "update_only_wifi"
const val LAST_UPDATE_SEARCH = "last_update_search"
const val LAST_UPDATE_ID = "last_update_id"
const val EXPERIMENTAL_UPDATES = "experimental_updates"
const val START_DIRECTORY = "start_directory"
const val SAVED_ARTWORK_COPYRIGHT_NOTICE_SHOWN = "saved_artwork_copyright_notice_shown"
const val LOUDNESS_ENHANCER_WARNING_SHOWN = "loudness_enhancer_warning_shown"
const val INITIALIZED_BLACKLIST = "initialized_blacklist"
const val SAF_SDCARD_URI = "saf_sdcard_uri"
const val LAST_SLEEP_TIMER_VALUE = "last_sleep_timer_value"
const val NEXT_SLEEP_TIMER_ELAPSED_REALTIME = "next_sleep_timer_elapsed_real_time"
const val SLEEP_TIMER_FINISH_SONG = "sleep_timer_finish_music"
const val SAVED_REPEAT_MODE = "SAVED_REPEAT_MODE"
const val SAVED_SHUFFLE_MODE = "SAVED_SHUFFLE_MODE"
const val SAVED_POSITION_IN_TRACK = "SAVED_POSITION_IN_TRACK"
const val SAVED_QUEUE_POSITION = "SAVED_QUEUE_POSITION"