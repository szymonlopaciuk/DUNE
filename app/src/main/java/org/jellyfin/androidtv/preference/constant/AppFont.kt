package org.jellyfin.androidtv.preference.constant

import androidx.annotation.FontRes
import androidx.annotation.StyleRes
import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * Font used for the app's interface text.
 *
 * [themeOverlay] applies the font to views (see font_overlays.xml), [fontRes] is used for
 * Compose; it's null for the device's system font.
 */
enum class AppFont(
	override val nameRes: Int,
	@param:StyleRes val themeOverlay: Int,
	@param:FontRes val fontRes: Int?,
) : PreferenceEnum {
	SYSTEM(R.string.pref_app_font_system, R.style.AppFont_System, null),
	ROBOTO(R.string.pref_app_font_roboto, R.style.AppFont_Roboto, R.font.roboto),
	GOOGLE_SANS(R.string.pref_app_font_google_sans, R.style.AppFont_GoogleSans, R.font.google_sans),
	INTER(R.string.pref_app_font_inter, R.style.AppFont_Inter, R.font.inter),
	OVERPASS(R.string.pref_app_font_overpass, R.style.AppFont_Overpass, R.font.overpass),
	BANGERS(R.string.pref_app_font_bangers, R.style.AppFont_Bangers, R.font.bangers),

	// Downloaded through Google Play services, falls back to the system font without them
	BEBAS_NEUE(R.string.pref_app_font_bebas_neue, R.style.AppFont_BebasNeue, R.font.bebas_neue),
	BAGEL_FAT_ONE(R.string.pref_app_font_bagel_fat_one, R.style.AppFont_BagelFatOne, R.font.bagel_fat_one),
	FUGAZ_ONE(R.string.pref_app_font_fugaz_one, R.style.AppFont_FugazOne, R.font.fugaz_one),
}
