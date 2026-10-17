package org.jellyfin.androidtv.ui.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.SubtitleLanguage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CultureDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private val SUPPORTED_ITEM_KINDS = setOf(
	BaseItemKind.MOVIE,
	BaseItemKind.EPISODE,
	BaseItemKind.VIDEO,
	BaseItemKind.MUSIC_VIDEO,
	BaseItemKind.TRAILER,
)

/**
 * Whether [user] may search for and download subtitles from the server's subtitle providers
 * (e.g. the OpenSubtitles plugin) for this item.
 */
fun BaseItemDto.supportsRemoteSubtitles(user: UserDto?): Boolean {
	val policy = user?.policy ?: return false
	return type in SUPPORTED_ITEM_KINDS && (policy.isAdministrator || policy.enableSubtitleManagement)
}

/**
 * The three-letter language code to search subtitles in by default: the subtitle language from
 * the server account, then the one from the app preferences, then the device language.
 */
fun defaultSubtitleSearchLanguage(user: UserDto?, userPreferences: UserPreferences): String {
	user?.configuration?.subtitleLanguagePreference
		?.takeIf { it.isNotBlank() }
		?.let { return it }

	userPreferences[UserPreferences.defaultSubtitleLanguage]
		.takeIf { it != SubtitleLanguage.DEFAULT && it != SubtitleLanguage.NONE }
		?.let { return it.code }

	return runCatching { Locale.getDefault().isO3Language }.getOrNull()
		?.takeIf { it.isNotBlank() }
		?: "eng"
}

/**
 * The languages the server knows, sorted by name.
 */
suspend fun ApiClient.getSubtitleLanguages(): List<CultureDto> = withContext(Dispatchers.IO) {
	localizationApi.getCultures().content
		.filter { !it.threeLetterIsoLanguageName.isNullOrBlank() }
		.sortedBy { it.displayName.lowercase() }
}

/**
 * Find the culture matching a three-letter language code. Languages can have several codes
 * (e.g. "ger" and "deu"), so all of them are compared.
 */
fun List<CultureDto>.findLanguage(code: String): CultureDto? = firstOrNull { culture ->
	culture.threeLetterIsoLanguageName.equals(code, ignoreCase = true) ||
		culture.threeLetterIsoLanguageNames.any { it.equals(code, ignoreCase = true) }
}

/**
 * Search the server's subtitle providers. Subtitles that are timed for this exact file come
 * first, then ones made by people over machine translations, then the best rated and most
 * downloaded ones.
 */
suspend fun ApiClient.searchRemoteSubtitles(
	itemId: UUID,
	language: String,
): List<RemoteSubtitleInfo> = withContext(Dispatchers.IO) {
	subtitleApi.searchRemoteSubtitles(itemId = itemId, language = language).content
		.filter { it.id != null }
		.sortedWith(
			compareByDescending<RemoteSubtitleInfo> { it.isHashMatch == true }
				.thenBy { it.isMachineTranslated }
				.thenByDescending { it.communityRating ?: 0f }
				.thenByDescending { it.downloadCount ?: 0 }
		)
}

val RemoteSubtitleInfo.isMachineTranslated get() = machineTranslated == true || aiTranslated == true

sealed interface RemoteSubtitleDownload {
	/** The subtitle was added to the item as [stream]; [item] is the refreshed item. */
	data class Added(val item: BaseItemDto, val stream: MediaStream) : RemoteSubtitleDownload

	/** The server accepted the download but hasn't added the subtitle to the item yet. */
	data object Pending : RemoteSubtitleDownload
}

/**
 * Download a subtitle found with [searchRemoteSubtitles] to the server. The server adds it to
 * the item in the background, so poll the item until the new subtitle stream shows up.
 *
 * @param mediaSourceId the version of the item to add the subtitle to, when it has several
 * @param onDownloaded called once the server downloaded the subtitle, before it's added
 */
suspend fun ApiClient.downloadRemoteSubtitle(
	itemId: UUID,
	mediaSourceId: String?,
	subtitleId: String,
	onDownloaded: suspend () -> Unit = {},
): RemoteSubtitleDownload = withContext(Dispatchers.IO) {
	fun BaseItemDto.externalSubtitles(): List<MediaStream> {
		val mediaSource = mediaSources?.firstOrNull { it.id == mediaSourceId } ?: mediaSources?.firstOrNull()
		val streams = mediaSource?.mediaStreams ?: mediaStreams
		return streams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE && it.isExternal }
	}

	val existingPaths = userLibraryApi.getItem(itemId = itemId).content
		.externalSubtitles()
		.mapNotNull { it.path }
		.toSet()

	subtitleApi.downloadRemoteSubtitles(
		itemId = mediaSourceId?.toUUIDOrNull() ?: itemId,
		subtitleId = subtitleId,
	)
	withContext(Dispatchers.Main) { onDownloaded() }

	repeat(POLL_ATTEMPTS) {
		delay(POLL_INTERVAL)
		val item = userLibraryApi.getItem(itemId = itemId).content
		val stream = item.externalSubtitles().firstOrNull { it.path != null && it.path !in existingPaths }
		if (stream != null) return@withContext RemoteSubtitleDownload.Added(item, stream)
	}

	RemoteSubtitleDownload.Pending
}

private const val POLL_ATTEMPTS = 8
private val POLL_INTERVAL = 1500.milliseconds
