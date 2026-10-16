package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeCustomSection
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.GenreSortBy

@Composable
fun GenresPreferenceScreenCompose(
    userPreferences: UserPreferences,
    userSettingPreferences: UserSettingPreferences,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but continue with screen initialization
        }
    }

    // Get preference states
    val (genreItemLimit, setGenreItemLimit) = rememberPreferenceState(
        preference = UserPreferences.genreItemLimit,
        preferences = userPreferences
    )

    val (genreSortBy, setGenreSortBy) = rememberEnumPreferenceState(
        preference = UserPreferences.genreSortBy,
        preferences = userPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.home_custom_sections_general))
        }

        item {
            SeekBarPreference(
                title = context.getString(R.string.genre_item_limit),
                value = genreItemLimit,
                range = 5..35,
                onValueChange = setGenreItemLimit,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstItemFocusRequester),
                step = 1
            )
        }

        item {
            EnumPreference(
                title = context.getString(R.string.pref_genre_sort_by),
                value = genreSortBy,
                onValueChange = setGenreSortBy,
                options = GenreSortBy.entries,
                optionLabel = { context.getString(it.nameRes) }
            )
        }

        item {
            PreferenceHeader(context.getString(R.string.home_custom_sections_genres))
        }

        items(HomeCustomSection.entries.filter { it.isGenre }) { section ->
            CustomSectionPreference(section, userSettingPreferences)
        }

        item {
            PreferenceHeader(context.getString(R.string.home_custom_sections_other))
        }

        items(HomeCustomSection.entries.filter { !it.isGenre }) { section ->
            CustomSectionPreference(section, userSettingPreferences)
        }

        item {
            val (showMusicVideos, setShowMusicVideos) = rememberPreferenceState(
                preference = userSettingPreferences.showMusicVideosRow,
                preferences = userSettingPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.show_music_videos_row),
                checked = showMusicVideos,
                preference = userSettingPreferences.showMusicVideosRow,
                onCheckedChange = setShowMusicVideos
            )
        }
    }
}

@Composable
private fun CustomSectionPreference(
    section: HomeCustomSection,
    userSettingPreferences: UserSettingPreferences
) {
    val context = LocalContext.current
    val preference = remember(section) { userSettingPreferences.customSectionDisplay(section) }
    val (mode, setMode) = rememberEnumPreferenceState(
        preference = preference,
        preferences = userSettingPreferences
    )

    EnumPreference(
        title = section.getTitle(context),
        value = mode,
        onValueChange = setMode,
        options = section.displayModes,
        optionLabel = { context.getString(it.nameRes) }
    )
}
