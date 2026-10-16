package org.jellyfin.androidtv.preference

import android.content.Context
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.constant.HomeCustomSection
import org.jellyfin.androidtv.constant.HomeSectionDisplayMode
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.store.SharedPreferenceStore

class UserSettingPreferences(context: Context) : SharedPreferenceStore(
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
) {

    /** How each optional custom section is shown on the home screen. */
    fun customSectionDisplay(section: HomeCustomSection) =
        enumPreference("custom_section_${section.key}", HomeSectionDisplayMode.HIDDEN)

    @JvmField
    val skipBackLength = intPreference("skipBackLength", 10_000)
    @JvmField
    val skipForwardLength = intPreference("skipForwardLength", 30_000)

	val themeSongsEnabled = booleanPreference("themesongsEnabled", false)
	val themesongvolume = intPreference("themesongsVolume", 10)
	val themeSongsMovies = booleanPreference("themeSongsMovies", true)
	val themeSongsSeries = booleanPreference("themeSongsSeries", true)
	val themeSongsEpisodes = booleanPreference("themeSongsEpisodes", false)
	// Archive.org fallback preference
	val themeSongsArchiveFallback = booleanPreference("themeSongsArchiveFallback", true)

	// Media folder display options
    val useExtraSmallMediaFolders = booleanPreference("useExtraSmallMediaFolders", true)
    val showLiveTvButton = booleanPreference("show_live_tv_button", false)
    val showRandomButton = booleanPreference("show_masks_button", true)
    val useClassicHomeScreen = booleanPreference("use_classic_home_screen", false)

    // Optional home rows
    val showMusicVideosRow = booleanPreference("show_music_videos_row", false)

    val homesection0 = enumPreference("homesection0", HomeSectionType.LIBRARY_TILES_SMALL)
    val homesection1 = enumPreference("homesection1", HomeSectionType.CONTINUE_WATCHING_COMBINED)
    val homesection2 = enumPreference("homesection2", HomeSectionType.LATEST_MEDIA)
    val homesection3 = enumPreference("homesection3", HomeSectionType.NONE)
    val homesection4 = enumPreference("homesection4", HomeSectionType.NONE)
    val homesection5 = enumPreference("homesection5", HomeSectionType.NONE)
    val homesection6 = enumPreference("homesection6", HomeSectionType.NONE)
    val homesection7 = enumPreference("homesection7", HomeSectionType.NONE)
    val homesection8 = enumPreference("homesection8", HomeSectionType.NONE)
    val homesection9 = enumPreference("homesection9", HomeSectionType.NONE)


    }
