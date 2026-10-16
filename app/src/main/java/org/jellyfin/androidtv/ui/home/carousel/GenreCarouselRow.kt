package org.jellyfin.androidtv.ui.home.carousel

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.presentation.CustomRowHeaderPresenter
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.math.roundToInt

/**
 * A home screen row showing [items] in a [FeaturedCarousel] instead of a row of cards.
 */
class GenreCarouselRow(
	title: String,
	val items: List<BaseItemDto>,
	val carouselItems: List<CarouselItem>,
) : Row(HeaderItem(title)) {
	var activeIndex = 0

	val activeItem: BaseItemDto?
		get() = carouselItems.getOrNull(activeIndex)?.let { active ->
			items.firstOrNull { it.id.toString() == active.id }
		}
}

/**
 * Presents a [GenreCarouselRow] as a scaled-down version of the featured carousel, right-aligned
 * with the featured carousel at the top of the home screen.
 *
 * Selecting the row reports it through the row's item selected listener with a null item.
 */
class GenreCarouselRowPresenter(
	private val onItemClicked: (BaseItemDto) -> Unit,
) : RowPresenter() {
	init {
		headerPresenter = CustomRowHeaderPresenter()
		selectEffectEnabled = false
	}

	private class ViewHolder(val composeView: ComposeView) : RowPresenter.ViewHolder(composeView)

	override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
		val composeView = ComposeView(parent.context).apply {
			layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		}
		return ViewHolder(composeView)
	}

	override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(vh, item)
		val row = item as GenreCarouselRow
		(vh as ViewHolder).composeView.setContent {
			GenreCarousel(
				row = row,
				onItemClicked = onItemClicked,
				onActiveIndexChanged = { index -> row.activeIndex = index },
			)
		}
	}

	override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
		(vh as ViewHolder).composeView.disposeComposition()
		super.onUnbindRowViewHolder(vh)
	}

	private companion object {
		// Unscaled height: enough for the logo/title, info line and three lines of synopsis,
		// a bit less than the featured carousel's 259dp (see fragment_home.xml)
		val DESIGN_HEIGHT = 232.dp

		// Distance from the screen's edges, as the featured carousel
		val SCREEN_MARGIN = 24.dp

		// List rows overlap the row above by 10dp (home_row_spacing) and get their gaps from
		// padding around the cards, which a carousel doesn't have
		val ROW_TOP_GAP = 10.dp
		val ROW_BOTTOM_GAP = 24.dp
		const val SCALE = 0.7f
	}

	@Composable
	private fun GenreCarousel(
		row: GenreCarouselRow,
		onItemClicked: (BaseItemDto) -> Unit,
		onActiveIndexChanged: (Int) -> Unit,
	) {
		var activeIndex by remember(row) { mutableIntStateOf(row.activeIndex) }

		// The rows container doesn't line up with the screen edges, so measure where the row ends
		// on screen and pad it to end where the featured carousel ends.
		val density = LocalDensity.current
		val screenWidth = LocalConfiguration.current.screenWidthDp.dp
		var endPadding by remember { mutableStateOf(0.dp) }

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = ROW_TOP_GAP, bottom = ROW_BOTTOM_GAP)
				.height(DESIGN_HEIGHT * SCALE)
				.onGloballyPositioned { coordinates ->
					val rowEnd = with(density) { coordinates.boundsInWindow().right.toDp() }
					endPadding = (rowEnd - (screenWidth - SCREEN_MARGIN)).coerceAtLeast(0.dp)
				}
				.padding(end = endPadding),
			contentAlignment = Alignment.TopEnd,
		) {
			FeaturedCarousel(
				items = row.carouselItems,
				onItemSelected = { selected ->
					row.items.firstOrNull { it.id.toString() == selected.id }?.let(onItemClicked)
				},
				activeIndex = activeIndex,
				onActiveIndexChanged = { index ->
					activeIndex = index
					onActiveIndexChanged(index)
				},
				// Span the screen between the margins; laid out unscaled so it keeps the same height
				modifier = Modifier.scaledLayout((screenWidth - SCREEN_MARGIN * 2) / SCALE, DESIGN_HEIGHT, SCALE),
				autoAdvanceWhenFocused = true,
			)
		}
	}

	/** Lays the content out at [width]x[height] and draws it scaled down by [scale]. */
	private fun Modifier.scaledLayout(width: Dp, height: Dp, scale: Float) = layout { measurable, _ ->
		val placeable = measurable.measure(Constraints.fixed(width.roundToPx(), height.roundToPx()))
		layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
			placeable.placeWithLayer(0, 0) {
				scaleX = scale
				scaleY = scale
				transformOrigin = TransformOrigin(0f, 0f)
			}
		}
	}
}
