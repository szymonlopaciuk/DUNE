package org.jellyfin.androidtv.util

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat

/**
 * Resolve one of the theme's font family attributes (like `appFontFamilyLight`) to a typeface,
 * for text views created in code. Returns null when the theme doesn't set it.
 */
fun Context.resolveThemeFont(@AttrRes attr: Int): Typeface? {
	val value = TypedValue()
	if (!theme.resolveAttribute(attr, value, true)) return null

	return when {
		value.type == TypedValue.TYPE_STRING && value.resourceId == 0 -> Typeface.create(value.string.toString(), Typeface.NORMAL)
		value.resourceId != 0 -> runCatching { ResourcesCompat.getFont(this, value.resourceId) }.getOrNull()
		else -> null
	}
}
