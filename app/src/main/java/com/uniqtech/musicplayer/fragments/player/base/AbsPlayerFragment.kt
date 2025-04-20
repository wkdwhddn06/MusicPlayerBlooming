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

import android.animation.AnimatorSet
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.activities.MainActivity
import com.uniqtech.musicplayer.activities.tageditor.AbsTagEditorActivity
import com.uniqtech.musicplayer.activities.tageditor.SongTagEditorActivity
import com.uniqtech.musicplayer.database.toSongEntity
import com.uniqtech.musicplayer.dialogs.LyricsDialog
import com.uniqtech.musicplayer.dialogs.SleepTimerDialog
import com.uniqtech.musicplayer.dialogs.WebSearchDialog
import com.uniqtech.musicplayer.dialogs.playlists.CreatePlaylistDialog
import com.uniqtech.musicplayer.extensions.currentFragment
import com.uniqtech.musicplayer.extensions.media.albumArtistName
import com.uniqtech.musicplayer.extensions.media.extraInfo
import com.uniqtech.musicplayer.extensions.navigation.albumDetailArgs
import com.uniqtech.musicplayer.extensions.navigation.artistDetailArgs
import com.uniqtech.musicplayer.extensions.navigation.genreDetailArgs
import com.uniqtech.musicplayer.extensions.openIntent
import com.uniqtech.musicplayer.extensions.resources.inflateMenu
import com.uniqtech.musicplayer.extensions.showToast
import com.uniqtech.musicplayer.extensions.utilities.buildInfoString
import com.uniqtech.musicplayer.extensions.whichFragment
import com.uniqtech.musicplayer.fragments.LibraryViewModel
import com.uniqtech.musicplayer.fragments.ReloadType
import com.uniqtech.musicplayer.fragments.base.AbsMusicServiceFragment
import com.uniqtech.musicplayer.helper.color.MediaNotificationProcessor
import com.uniqtech.musicplayer.helper.menu.newPopupMenu
import com.uniqtech.musicplayer.helper.menu.onSongMenu
import com.uniqtech.musicplayer.misc.CoverSaverCoroutine
import com.uniqtech.musicplayer.model.Genre
import com.uniqtech.musicplayer.model.GestureOnCover
import com.uniqtech.musicplayer.model.NowPlayingAction
import com.uniqtech.musicplayer.model.Song
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.service.constants.ServiceEvent
import com.uniqtech.musicplayer.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * @author Christians M. A. (mardous)
 */
abstract class AbsPlayerFragment(@LayoutRes layoutRes: Int) :
    AbsMusicServiceFragment(layoutRes),
    Toolbar.OnMenuItemClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    PlayerAlbumCoverFragment.Callbacks {

    val libraryViewModel: LibraryViewModel by activityViewModel()

    private var coverFragment: PlayerAlbumCoverFragment? = null
    private val coverSaver: CoverSaverCoroutine by lazy {
        CoverSaverCoroutine(requireContext(), viewLifecycleOwner.lifecycleScope, IO)
    }

    protected abstract val playerControlsFragment: AbsPlayerControlsFragment

    protected open val playerToolbar: Toolbar?
        get() = null

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onCreateChildFragments()
        Preferences.registerOnSharedPreferenceChangeListener(this)
    }

    @CallSuper
    protected open fun onCreateChildFragments() {
        coverFragment = whichFragment(R.id.playerAlbumCoverFragment)
        coverFragment?.setCallbacks(this)
    }

    internal fun inflateMenuInView(view: View?) {
        if (view != null) {
            if (view is Toolbar) {
                view.inflateMenu(R.menu.menu_now_playing, this) {
                    onMenuInflated(it)
                }
            } else {
                val popupMenu = newPopupMenu(view, R.menu.menu_now_playing) {
                    onMenuInflated(it)
                }.also { popupMenu ->
                    popupMenu.setOnMenuItemClickListener { onMenuItemClick(it) }
                }
                view.setOnClickListener {
                    popupMenu.show()
                }
            }
        }
    }

    @CallSuper
    protected open fun onMenuInflated(menu: Menu) {}

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        val currentSong = MusicPlayer.currentSong
        return when (menuItem.itemId) {
            R.id.action_playing_queue -> {
                onQuickActionEvent(NowPlayingAction.OpenPlayQueue)
                true
            }

            R.id.action_favorite -> {
                onQuickActionEvent(NowPlayingAction.ToggleFavoriteState)
                true
            }

//            R.id.action_show_lyrics -> {
//                onQuickActionEvent(NowPlayingAction.Lyrics)
//                true
//            }

            R.id.action_sound_settings -> {
                onQuickActionEvent(NowPlayingAction.SoundSettings)
                true
            }

//            R.id.action_sleep_timer -> {
//                onQuickActionEvent(NowPlayingAction.SleepTimer)
//                true
//            }

            R.id.action_tag_editor -> {
                onQuickActionEvent(NowPlayingAction.TagEditor)
                true
            }

//            R.id.action_web_search -> {
//                onQuickActionEvent(NowPlayingAction.WebSearch)
//                true
//            }

            R.id.action_go_to_album -> {
                onQuickActionEvent(NowPlayingAction.OpenAlbum)
                true
            }

            R.id.action_go_to_artist -> {
                onQuickActionEvent(NowPlayingAction.OpenArtist)
                true
            }

            R.id.action_go_to_genre -> {
                libraryViewModel.genreBySong(currentSong).observe(viewLifecycleOwner) { genre ->
                    goToGenre(requireActivity(), genre)
                }
                true
            }

//            R.id.action_share_now_playing -> {
//                ShareSongDialog.create(MusicPlayer.currentSong)
//                    .show(childFragmentManager, "SHARE_SONG")
//                true
//            }
//
//            R.id.action_equalizer -> {
//                goToDestination(requireActivity(), R.id.nav_equalizer)
//                true
//            }

            else -> currentSong.onSongMenu(this, menuItem)
        }
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        libraryViewModel.setPaletteColor(color.backgroundColor)
    }

    override fun onGestureDetected(gestureOnCover: GestureOnCover): Boolean {
        return when (gestureOnCover) {
            GestureOnCover.DoubleTap -> onQuickActionEvent(Preferences.coverDoubleTapAction)
            GestureOnCover.LongPress -> onQuickActionEvent(Preferences.coverLongPressAction)
            else -> false
        }
    }

    override fun onLyricsVisibilityChange(animatorSet: AnimatorSet, lyricsVisible: Boolean) {
//        val lyricsItem = playerToolbar?.menu?.findItem(R.id.action_show_lyrics)
//        if (lyricsItem != null) {
//            if (lyricsVisible) {
//                lyricsItem.setIcon(R.drawable.ic_lyrics_24dp)
//                    .setTitle(R.string.action_hide_lyrics)
//            } else {
//                lyricsItem.setIcon(R.drawable.ic_lyrics_outline_24dp)
//                    .setTitle(R.string.action_show_lyrics)
//            }
//        }
    }

    protected open fun onSongInfoChanged(song: Song) {
        playerControlsFragment.onSongInfoChanged(song)
    }

    override fun onFavoritesStoreChanged() {
        super.onFavoritesStoreChanged()
        updateIsFavorite(true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        onSongInfoChanged(MusicPlayer.currentSong)
        playerControlsFragment.onQueueInfoChanged(MusicPlayer.getNextSongInfo(requireContext()))
        playerControlsFragment.onUpdatePlayPause(MusicPlayer.isPlaying)
        playerControlsFragment.onUpdateRepeatMode(MusicPlayer.repeatMode)
        playerControlsFragment.onUpdateShuffleMode(MusicPlayer.shuffleMode)
        updateIsFavorite(false)
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        onSongInfoChanged(MusicPlayer.currentSong)
        playerControlsFragment.onQueueInfoChanged(MusicPlayer.getNextSongInfo(requireContext()))
        updateIsFavorite(false)
    }

    override fun onPlayStateChanged() {
        super.onPlayStateChanged()
        playerControlsFragment.onUpdatePlayPause(MusicPlayer.isPlaying)
    }

    override fun onRepeatModeChanged() {
        super.onRepeatModeChanged()
        playerControlsFragment.onUpdateRepeatMode(MusicPlayer.repeatMode)
    }

    override fun onShuffleModeChanged() {
        super.onShuffleModeChanged()
        playerControlsFragment.onUpdateShuffleMode(MusicPlayer.shuffleMode)
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        playerControlsFragment.onQueueInfoChanged(MusicPlayer.getNextSongInfo(requireContext()))
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            DISPLAY_EXTRA_INFO,
            EXTRA_INFO -> {
                playerControlsFragment.onSongInfoChanged(MusicPlayer.currentSong)
            }

            DISPLAY_ALBUM_TITLE,
            PREFER_ALBUM_ARTIST_NAME -> {
                playerControlsFragment.onSongInfoChanged(MusicPlayer.currentSong)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    internal fun onQuickActionEvent(action: NowPlayingAction): Boolean {
        val currentSong = MusicPlayer.currentSong
        return when (action) {
            NowPlayingAction.OpenAlbum -> {
                goToAlbum(requireActivity(), currentSong)
                true
            }

            NowPlayingAction.OpenArtist -> {
                goToArtist(requireActivity(), currentSong)
                true
            }

            NowPlayingAction.OpenPlayQueue -> {
                findNavController().navigate(R.id.nav_queue)
                true
            }

            NowPlayingAction.TogglePlayState -> {
                MusicPlayer.togglePlayPause()
                true
            }

            NowPlayingAction.WebSearch -> {
                WebSearchDialog.create(currentSong).show(childFragmentManager, "WEB_SEARCH_DIALOG")
                true
            }

            NowPlayingAction.SaveAlbumCover -> {
                requestSaveCover()
                true
            }

            NowPlayingAction.ShufflePlayQueue -> {
                MusicPlayer.shuffleQueue()
                true
            }

            NowPlayingAction.Lyrics -> {
                if (coverFragment?.isShowingLyrics() == true) {
                    coverFragment?.toggleLyrics()
                } else {
                    LyricsDialog.create(currentSong).show(childFragmentManager, "LYRICS_DIALOG")
                }
                true
            }

            NowPlayingAction.AddToPlaylist -> {
                CreatePlaylistDialog.create(currentSong)
                    .show(childFragmentManager, "ADD_TO_PLAYLIST")
                true
            }

            NowPlayingAction.ToggleFavoriteState -> {
                toggleFavorite(MusicPlayer.currentSong)
                true
            }

            NowPlayingAction.TagEditor -> {
                val tagEditorIntent = Intent(requireContext(), SongTagEditorActivity::class.java)
                tagEditorIntent.putExtra(AbsTagEditorActivity.EXTRA_ID, currentSong.id)
                startActivity(tagEditorIntent)
                true
            }

            NowPlayingAction.SleepTimer -> {
                SleepTimerDialog().show(childFragmentManager, "SLEEP_TIMER")
                true
            }

            NowPlayingAction.SoundSettings -> {
                findNavController().navigate(R.id.nav_sound_settings)
                true
            }

            else -> false
        }
    }

    fun onShow() {
        coverFragment?.showLyrics()
        playerControlsFragment.onShow()
    }

    fun onHide() {
        coverFragment?.hideLyrics()
        playerControlsFragment.onHide()
    }

    protected open fun onIsFavoriteChanged(isFavorite: Boolean, withAnimation: Boolean) {
        val iconRes = if (withAnimation) {
            if (isFavorite) R.drawable.avd_favorite else R.drawable.avd_unfavorite
        } else {
            if (isFavorite) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_outline_24dp
        }
        val titleRes =
            if (isFavorite) R.string.action_remove_from_favorites else R.string.action_add_to_favorites

        val playerToolbar = playerToolbar
        if (playerToolbar != null) {
            playerToolbar.menu.findItem(R.id.action_favorite)?.apply {
                setIcon(iconRes)
                setTitle(titleRes)
                icon.also {
                    if (it is AnimatedVectorDrawable) {
                        it.start()
                    }
                }
            }
        }
    }

    protected open fun onToggleFavorite(song: Song, isFavorite: Boolean) {
        val textId = when {
            isFavorite -> R.string.added_to_favorites_label
            else -> R.string.removed_from_favorites_label
        }
        showToast(textId)
    }

    private fun updateIsFavorite(withAnim: Boolean = false) {
        lifecycleScope.launch(IO) {
            val isFavorite = libraryViewModel.isSongFavorite(MusicPlayer.currentSong.id)
            withContext(Dispatchers.Main) {
                onIsFavoriteChanged(isFavorite, withAnim)
            }
        }
    }

    private fun toggleFavorite(song: Song) {
        lifecycleScope.launch(IO) {
            val playlist = libraryViewModel.favoritePlaylist()
            val songEntity = song.toSongEntity(playlist.playListId)
            val isFavorite = libraryViewModel.isSongFavorite(song.id)
            if (isFavorite) {
                libraryViewModel.removeSongFromPlaylist(songEntity)
            } else {
                libraryViewModel.insertSongs(listOf(songEntity))
            }
            libraryViewModel.forceReload(ReloadType.Playlists)
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(ServiceEvent.FAVORITE_STATE_CHANGED))
        }
    }

    fun getSongArtist(song: Song): String {
        val artistName = if (Preferences.preferAlbumArtistName)
            song.albumArtistName() else song.artistName
        if (Preferences.displayAlbumTitle) {
            return buildInfoString(artistName, song.albumName)
        }
        return artistName
    }

    fun isExtraInfoEnabled(): Boolean =
        Preferences.displayExtraInfo && Preferences.nowPlayingExtraInfoList.any { it.isEnabled }

    fun getExtraInfoString(song: Song) =
        if (isExtraInfoEnabled()) song.extraInfo(Preferences.nowPlayingExtraInfoList) else null

    private fun requestSaveCover() {
        if (!Preferences.savedArtworkCopyrightNoticeShown) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.save_artwork_copyright_info_title)
                .setMessage(R.string.save_artwork_copyright_info_message)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                    Preferences.savedArtworkCopyrightNoticeShown = true
                    requestSaveCover()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            coverSaver.saveArtwork(
                MusicPlayer.currentSong,
                onPreExecute = {
                    view?.let { safeView ->
                        Snackbar.make(
                            safeView,
                            R.string.saving_cover_please_wait,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                },
                onSuccess = { uri, mimeType ->
                    view?.let { safeView ->
                        Snackbar.make(
                            safeView,
                            R.string.save_artwork_success,
                            Snackbar.LENGTH_SHORT
                        )
                            .setAction(R.string.save_artwork_view_action) {
                                try {
                                    startActivity(uri.openIntent(mimeType))
                                } catch (e: ActivityNotFoundException) {
                                    context?.showToast(e.toString())
                                }
                            }
                            .show()
                    }
                },
                onError = { errorMessage ->
                    view?.let { safeView ->
                        if (!errorMessage.isNullOrEmpty()) {
                            Snackbar.make(safeView, errorMessage, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                })
        }
    }
}

fun goToArtist(activity: Activity, song: Song) {
    goToDestination(
        activity,
        R.id.nav_artist_detail,
        artistDetailArgs(song),
        removeTransition = true,
        singleTop = false
    )
}

fun goToAlbum(activity: Activity, song: Song) {
    goToDestination(
        activity,
        R.id.nav_album_detail,
        albumDetailArgs(song.albumId),
        removeTransition = true,
        singleTop = false
    )
}

fun goToGenre(activity: Activity, genre: Genre) {
    goToDestination(
        activity,
        R.id.nav_genre_detail,
        genreDetailArgs(genre),
        singleTop = false
    )
}

fun goToDestination(
    activity: Activity,
    destinationId: Int,
    args: Bundle? = null,
    removeTransition: Boolean = false,
    singleTop: Boolean = true
) {
    if (activity !is MainActivity) return
    activity.apply {
        if (removeTransition) {
            // Remove exit transition of current fragment, so
            // it doesn't exit with a weird transition
            currentFragment(R.id.fragment_container)?.exitTransition = null
        }

        //Hide Bottom Bar First, else Bottom Sheet doesn't collapse fully
        setBottomNavVisibility(false)
        if (getBottomSheetBehavior().state == BottomSheetBehavior.STATE_EXPANDED) {
            collapsePanel()
        }

        val navOptions = when {
            singleTop -> navOptions { launchSingleTop = true }
            else -> null
        }
        findNavController(R.id.fragment_container)
            .navigate(destinationId, args, navOptions)
    }
}