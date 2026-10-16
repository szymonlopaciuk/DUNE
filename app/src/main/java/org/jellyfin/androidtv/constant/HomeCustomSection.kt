package org.jellyfin.androidtv.constant

import android.content.Context
import androidx.annotation.StringRes
import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * How a custom home section is shown on the home screen.
 */
enum class HomeSectionDisplayMode(
	override val serializedName: String,
	override val nameRes: Int,
) : PreferenceEnum {
	HIDDEN("hidden", R.string.home_section_display_hidden),
	ROW("row", R.string.home_section_display_row),
	CAROUSEL("carousel", R.string.home_section_display_carousel),
}

/**
 * Optional sections that can be added to the bottom of the home screen.
 *
 * Genre sections match any of [genres]. Movie and TV metadata providers name some genres
 * differently (e.g. "Science Fiction" for movies, "Sci-Fi & Fantasy" for series), so the
 * lists combine both to include movies and shows alike.
 */
enum class HomeCustomSection(
	val key: String,
	val genres: List<String> = emptyList(),
	@param:StringRes private val titleRes: Int? = null,
) {
	SUGGESTED_MOVIES("suggested_movies", titleRes = R.string.show_suggested_movies_row),
	COLLECTIONS("collections", titleRes = R.string.show_collections_row),
	ACTION("action", listOf("Action", "Action & Adventure")),
	ADVENTURE("adventure", listOf("Adventure", "Action & Adventure")),
	ANIMATION("animation", listOf("Animation")),
	COMEDY("comedy", listOf("Comedy")),
	CRIME("crime", listOf("Crime")),
	DOCUMENTARY("documentary", listOf("Documentary")),
	DRAMA("drama", listOf("Drama")),
	FAMILY("family", listOf("Family", "Kids")),
	FANTASY("fantasy", listOf("Fantasy", "Sci-Fi & Fantasy")),
	HISTORY("history", listOf("History")),
	HORROR("horror", listOf("Horror")),
	MYSTERY("mystery", listOf("Mystery")),
	REALITY("reality", listOf("Reality")),
	ROMANCE("romance", listOf("Romance")),
	SCIENCE_FICTION("science_fiction", listOf("Science Fiction", "Sci-Fi & Fantasy")),
	THRILLER("thriller", listOf("Thriller")),
	WAR("war", listOf("War", "War & Politics")),
	MUSIC_PLAYLISTS("music_playlists", titleRes = R.string.lbl_music_playlists);

	val isGenre get() = genres.isNotEmpty()

	/** Only genre sections (movies and series) can be shown as a carousel. */
	val displayModes
		get() = if (isGenre) HomeSectionDisplayMode.entries
		else listOf(HomeSectionDisplayMode.HIDDEN, HomeSectionDisplayMode.ROW)

	fun getTitle(context: Context): String = titleRes?.let(context::getString) ?: genres.first()
}
