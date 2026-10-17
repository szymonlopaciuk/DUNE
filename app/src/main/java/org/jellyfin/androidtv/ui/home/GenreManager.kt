package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.HomeCustomSection
import org.jellyfin.androidtv.constant.HomeSectionDisplayMode
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.home.carousel.CarouselItem
import org.jellyfin.androidtv.ui.home.carousel.GenreCarouselRow
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.androidtv.util.ImageHelper
import timber.log.Timber

/**
 * Manages genre row loading and display for the home screen with optimized performance.
 * Features lazy loading, caching, and resource management.
 */
class GenreManager(
	private val context: Context,
	private val userRepository: UserRepository,
	private val userPreferences: UserPreferences,
	private val userSettingPreferences: UserSettingPreferences,
	private val api: ApiClient,
	private val imageHelper: ImageHelper,
) {

	companion object {
		private const val GENRE_CARD_HEIGHT = 140
		private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

		// With shuffled categories, every third row is shown as a carousel
		private const val CAROUSEL_INTERVAL = 3
	}

	data class GenreConfig(
		val section: HomeCustomSection,
		val displayMode: HomeSectionDisplayMode,
	) {
		val displayName get() = section.genres.firstOrNull() ?: section.key
	}

	data class CachedEnabledGenres(
		val genres: List<GenreConfig>,
		val timestamp: Long = System.currentTimeMillis()
	) {
		fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
	}

	private val itemLimit get() = userPreferences[UserPreferences.genreItemLimit]

	// Cache for enabled genres to avoid repeated preference access
	private var cachedEnabledGenres: CachedEnabledGenres? = null

	fun getEnabledGenres(): List<GenreConfig> {
		// Performance optimization: Use cache if valid
		val cached = cachedEnabledGenres
		return if (cached != null && !cached.isExpired()) {
			Timber.d("Using cached enabled genres (${cached.genres.size} genres)")
			cached.genres
		} else {
			Timber.d("Computing enabled genres")
			val enabled = HomeCustomSection.entries
				.map { section -> GenreConfig(section, userSettingPreferences[userSettingPreferences.customSectionDisplay(section)]) }
				.filter { config -> config.displayMode != HomeSectionDisplayMode.HIDDEN }
			cachedEnabledGenres = CachedEnabledGenres(enabled)
			Timber.d("Found ${enabled.size} enabled genres")
			enabled
		}
	}

	fun clearEnabledGenresCache() {
		cachedEnabledGenres = null
		Timber.d("Cleared enabled genres cache")
	}

	suspend fun loadGenreRows(
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		try {
			val enabledGenres = getEnabledGenres()
			Timber.d("Loading ${enabledGenres.size} enabled genre rows")

			if (enabledGenres.isEmpty()) {
				Timber.d("No genre rows enabled")
				return@withContext
			}

			val shuffle = !userSettingPreferences[userSettingPreferences.useClassicHomeScreen] &&
				userSettingPreferences[userSettingPreferences.shuffleHomeCategories]

			if (!shuffle) {
				enabledGenres.forEach { config -> loadSingleGenreRow(config, cardPresenter, rowsAdapter) }
			} else {
				// Every third row is a carousel. When the row in that place can't be one (it's not a
				// genre, or has nothing to show), the next one that can takes its place.
				var rowCount = 0
				var carouselDue = false
				enabledGenres.shuffled().forEach { config ->
					if ((rowCount + 1) % CAROUSEL_INTERVAL == 0) carouselDue = true
					val displayMode = if (carouselDue && config.section.isGenre) HomeSectionDisplayMode.CAROUSEL
					else HomeSectionDisplayMode.ROW

					if (loadSingleGenreRow(config.copy(displayMode = displayMode), cardPresenter, rowsAdapter)) {
						rowCount++
						if (displayMode == HomeSectionDisplayMode.CAROUSEL) carouselDue = false
					}
				}
			}

			val loadTime = System.currentTimeMillis() - startTime
			Timber.d("Successfully loaded ${enabledGenres.size} genre rows in ${loadTime}ms")
		} catch (e: Exception) {
			Timber.e(e, "Error loading genre rows")
		}
	}

	/**
	 * Load the row of a genre or category and add it to the rows. Returns whether a row was added.
	 */
	private suspend fun loadSingleGenreRow(
		config: GenreConfig,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	): Boolean {
		val rowStartTime = System.currentTimeMillis()
		try {
			Timber.d("Loading ${config.displayName} ${config.displayMode}")

			val section = config.section
			val row: HomeFragmentRow? = when {
				section == HomeCustomSection.SUGGESTED_MOVIES -> createSuggestedMoviesRow()
				section == HomeCustomSection.COLLECTIONS -> createCollectionsRow()
				section == HomeCustomSection.MUSIC_PLAYLISTS -> createMusicRow()
				config.displayMode == HomeSectionDisplayMode.CAROUSEL -> createGenreCarouselRow(section)
				else -> createGenreRow(section)
			}

			if (row == null) {
				Timber.d("No matching ${config.displayName} items found in library")
				return false
			}

			withContext(Dispatchers.Main) {
				row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
			}

			val rowLoadTime = System.currentTimeMillis() - rowStartTime
			Timber.d("Successfully added ${config.displayName} row in ${rowLoadTime}ms")
			return true
		} catch (e: Exception) {
			Timber.e(e, "Error loading ${config.displayName} row")
			return false
		}
	}

	/**
	 * Carousel featuring movies and series from the genre. Returns null when nothing matches.
	 */
	private suspend fun createGenreCarouselRow(section: HomeCustomSection): HomeFragmentRow? {
		val response = api.itemsApi.getItems(
			GetItemsRequest(
				userId = userRepository.currentUser.value?.id,
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				genres = section.genres,
				sortBy = listOf(userPreferences[UserPreferences.genreSortBy].itemSortBy),
				sortOrder = listOf(SortOrder.DESCENDING),
				limit = itemLimit,
				recursive = true,
				fields = ItemRepository.itemFields + ItemFields.OVERVIEW,
				enableImageTypes = setOf(
					org.jellyfin.sdk.model.api.ImageType.BACKDROP,
					org.jellyfin.sdk.model.api.ImageType.THUMB,
					org.jellyfin.sdk.model.api.ImageType.LOGO,
					org.jellyfin.sdk.model.api.ImageType.PRIMARY,
				),
				enableTotalRecordCount = false
			)
		)

		val items = response.content.items
		val carouselItems = items.mapNotNull { item -> CarouselItem.fromBaseItemDto(item, imageHelper, api) }
		if (carouselItems.isEmpty()) return null

		val title = section.getTitle(context)
		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				rowsAdapter.add(GenreCarouselRow(title, items, carouselItems))
			}
		}
	}

	private fun createGenreRow(section: HomeCustomSection): HomeFragmentRow {
		val genreName = section.getTitle(context)
		val query = GetItemsRequest(
			userId = userRepository.currentUser.value?.id,
			includeItemTypes = listOf(
				BaseItemKind.MOVIE,
				BaseItemKind.SERIES,
				BaseItemKind.MUSIC_ALBUM,
				BaseItemKind.MUSIC_ARTIST,
				BaseItemKind.MUSIC_VIDEO
			),
			excludeItemTypes = listOf(BaseItemKind.EPISODE),
			genres = section.genres,
			sortBy = listOf(userPreferences[UserPreferences.genreSortBy].itemSortBy),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = itemLimit,
			recursive = true,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false
		)

		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				val noInfoCardPresenter = CardPresenter(false, GENRE_CARD_HEIGHT).apply {
					setHomeScreen(true)
					setUniformAspect(true)
				}

				HomeFragmentBrowseRowDefRow(BrowseRowDef(genreName, query, itemLimit, false, true))
					.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
			}
		}
	}

	/**
	 * special music row for playlists
	 */
	private fun createMusicRow(): HomeFragmentRow {
		val currentUserId = userRepository.currentUser.value?.id
		val musicPlaylistQuery = GetItemsRequest(
			userId = currentUserId,
			includeItemTypes = setOf(BaseItemKind.PLAYLIST),
			mediaTypes = setOf(MediaType.AUDIO),
			sortBy = listOf(userPreferences[UserPreferences.genreSortBy].itemSortBy),
			limit = itemLimit,
			fields = ItemRepository.itemFields,
			recursive = true,
			excludeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE)
		)

		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				val noInfoCardPresenter = CardPresenter(false, GENRE_CARD_HEIGHT).apply {
					setHomeScreen(true)
					setUniformAspect(true)
				}

				HomeFragmentBrowseRowDefRow(BrowseRowDef("Music Playlists", musicPlaylistQuery, itemLimit, false, true))
					.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
			}
		}
	}

	/**
	 * Creates a Collections row with thumbnail images and same card size as Music Videos row
	 */
	private fun createCollectionsRow(): HomeFragmentRow {
		val currentUserId = userRepository.currentUser.value?.id
			?: throw IllegalStateException("User not available")

		// Create a query to get collections
		val query = GetItemsRequest(
			userId = currentUserId,
			includeItemTypes = listOf(BaseItemKind.BOX_SET),
			sortBy = setOf(ItemSortBy.DATE_CREATED),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = itemLimit,
			recursive = true,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			fields = ItemRepository.itemFields,
			enableImages = true
		)

		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				// Create a card presenter for collections with thumbnail images and specific dimensions to match Music Videos row
				val collectionsCardPresenter = CardPresenter(false, GENRE_CARD_HEIGHT).apply {
						setHomeScreen(true)
						setUniformAspect(true)
					}
				// Create the row definition with chunk size and change triggers
				val rowDef = BrowseRowDef(
					"Collections",
					query,
					15, // chunkSize
					false, // preferParentThumb
					true, // staticHeight
					arrayOf(ChangeTriggerType.LibraryUpdated)
				)

				// Add the row to the adapter
				HomeFragmentBrowseRowDefRow(rowDef).addToRowsAdapter(context, collectionsCardPresenter, rowsAdapter)
			}
		}
	}
	private fun createSuggestedMoviesRow(): HomeFragmentRow {
		return HomeFragmentSuggestedMoviesFragmentRow(userRepository, api)
	}

	fun hasEnabledGenres(): Boolean = getEnabledGenres().isNotEmpty()

	fun getEnabledGenreCount(): Int {
		return getEnabledGenres().size
	}

	fun preloadGenreConfigs() {
		getEnabledGenres()
	}

	/**
	 * Force refresh of enabled genres cache
	 */
	fun refreshEnabledGenres() {
		clearEnabledGenresCache()
		getEnabledGenres() // This will recreate the cache
		Timber.d("Refreshed enabled genres cache")
	}
}
