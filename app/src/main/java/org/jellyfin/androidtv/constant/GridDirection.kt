package org.jellyfin.androidtv.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class GridDirection(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Vertical.
	 */
	VERTICAL(R.string.grid_direction_vertical),

	/** Horizontal rows. */
	HORIZONTAL(R.string.grid_direction_horizontal),

	/**
	 * List.
	 */
	LIST(R.string.grid_direction_list),
}
