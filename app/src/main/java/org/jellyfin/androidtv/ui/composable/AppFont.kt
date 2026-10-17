package org.jellyfin.androidtv.ui.composable

import android.content.SharedPreferences
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppFont
import org.jellyfin.androidtv.ui.base.LocalTypography
import org.koin.compose.koinInject
import timber.log.Timber
import androidx.compose.material.LocalTextStyle as Material2LocalTextStyle
import androidx.compose.material3.LocalTextStyle as Material3LocalTextStyle
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.Typography as Material3Typography
import androidx.tv.material3.LocalTextStyle as TvLocalTextStyle
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
import org.jellyfin.androidtv.ui.base.LocalTextStyle as BaseLocalTextStyle

/**
 * Theme the text in [content] with the app font chosen in the preferences. This is the Compose
 * counterpart of the AppFont theme overlays used for views; wrap the content of every Compose
 * root with it (inside any MaterialTheme it sets up).
 *
 * The app uses text components from several libraries (Material 3, TV Material, Material and
 * its own), so the font is set on each of their typographies and default text styles. Text
 * should take its style from these rather than building one from scratch.
 */
@Composable
fun AppFontTheme(content: @Composable () -> Unit) {
	val context = LocalContext.current
	val appFont = rememberAppFont()
	val fontFamily = remember(appFont) { appFont.fontRes?.let { loadFontFamily(context, it) } }

	if (fontFamily == null) {
		content()
		return
	}

	// The Material themes are only used here to provide their typography; keep the indication
	// (focus/press feedback) they would otherwise replace
	val indication = LocalIndication.current
	val material3TextStyle = Material3LocalTextStyle.current
	val tvTextStyle = TvLocalTextStyle.current
	val material2TextStyle = Material2LocalTextStyle.current
	val baseTextStyle = BaseLocalTextStyle.current
	val baseTypography = LocalTypography.current
	val material3Typography = Material3Theme.typography
	val tvTypography = TvMaterialTheme.typography

	Material3Theme(
		colorScheme = Material3Theme.colorScheme,
		shapes = Material3Theme.shapes,
		typography = remember(material3Typography, fontFamily) { material3Typography.withFontFamily(fontFamily) },
	) {
		TvMaterialTheme(
			colorScheme = TvMaterialTheme.colorScheme,
			shapes = TvMaterialTheme.shapes,
			typography = remember(tvTypography, fontFamily) { tvTypography.withFontFamily(fontFamily) },
		) {
			CompositionLocalProvider(
				LocalIndication provides indication,
				Material3LocalTextStyle provides material3TextStyle.withFontFamily(fontFamily),
				TvLocalTextStyle provides tvTextStyle.withFontFamily(fontFamily),
				Material2LocalTextStyle provides material2TextStyle.withFontFamily(fontFamily),
				BaseLocalTextStyle provides baseTextStyle.withFontFamily(fontFamily),
				LocalTypography provides baseTypography.copy(default = baseTypography.default.withFontFamily(fontFamily)),
				content = content,
			)
		}
	}
}

/**
 * The app font preference, updated when it's changed.
 */
@Composable
private fun rememberAppFont(): AppFont {
	val context = LocalContext.current
	val userPreferences = koinInject<UserPreferences>()
	var appFont by remember { mutableStateOf(userPreferences[UserPreferences.appFont]) }

	DisposableEffect(userPreferences) {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
		val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
			if (key == UserPreferences.appFont.key) appFont = userPreferences[UserPreferences.appFont]
		}
		sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
		onDispose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
	}

	return appFont
}

private fun loadFontFamily(context: android.content.Context, fontRes: Int): FontFamily? = try {
	ResourcesCompat.getFont(context, fontRes)?.let { FontFamily(Typeface(it)) }
} catch (e: Exception) {
	Timber.w(e, "Unable to load app font")
	null
}

// Replace generic fonts only (Material's typographies use sans-serif), keep deliberately chosen ones.
// Font padding is sized from the font's bounding box, which for fonts with deep glyphs (e.g. Google
// Sans) leaves a large gap under the text, so turn it off like the AppFont theme overlays do.
@Suppress("DEPRECATION")
private fun TextStyle.withFontFamily(fontFamily: FontFamily) = when (this.fontFamily) {
	null, FontFamily.Default, FontFamily.SansSerif -> copy(
		fontFamily = fontFamily,
		platformStyle = PlatformTextStyle(includeFontPadding = false),
	)
	else -> this
}

private fun Material3Typography.withFontFamily(fontFamily: FontFamily) = copy(
	displayLarge = displayLarge.withFontFamily(fontFamily),
	displayMedium = displayMedium.withFontFamily(fontFamily),
	displaySmall = displaySmall.withFontFamily(fontFamily),
	headlineLarge = headlineLarge.withFontFamily(fontFamily),
	headlineMedium = headlineMedium.withFontFamily(fontFamily),
	headlineSmall = headlineSmall.withFontFamily(fontFamily),
	titleLarge = titleLarge.withFontFamily(fontFamily),
	titleMedium = titleMedium.withFontFamily(fontFamily),
	titleSmall = titleSmall.withFontFamily(fontFamily),
	bodyLarge = bodyLarge.withFontFamily(fontFamily),
	bodyMedium = bodyMedium.withFontFamily(fontFamily),
	bodySmall = bodySmall.withFontFamily(fontFamily),
	labelLarge = labelLarge.withFontFamily(fontFamily),
	labelMedium = labelMedium.withFontFamily(fontFamily),
	labelSmall = labelSmall.withFontFamily(fontFamily),
)

private fun TvTypography.withFontFamily(fontFamily: FontFamily) = copy(
	displayLarge = displayLarge.withFontFamily(fontFamily),
	displayMedium = displayMedium.withFontFamily(fontFamily),
	displaySmall = displaySmall.withFontFamily(fontFamily),
	headlineLarge = headlineLarge.withFontFamily(fontFamily),
	headlineMedium = headlineMedium.withFontFamily(fontFamily),
	headlineSmall = headlineSmall.withFontFamily(fontFamily),
	titleLarge = titleLarge.withFontFamily(fontFamily),
	titleMedium = titleMedium.withFontFamily(fontFamily),
	titleSmall = titleSmall.withFontFamily(fontFamily),
	bodyLarge = bodyLarge.withFontFamily(fontFamily),
	bodyMedium = bodyMedium.withFontFamily(fontFamily),
	bodySmall = bodySmall.withFontFamily(fontFamily),
	labelLarge = labelLarge.withFontFamily(fontFamily),
	labelMedium = labelMedium.withFontFamily(fontFamily),
	labelSmall = labelSmall.withFontFamily(fontFamily),
)
