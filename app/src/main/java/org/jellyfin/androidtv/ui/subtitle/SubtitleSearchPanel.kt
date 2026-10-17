package org.jellyfin.androidtv.ui.subtitle

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.screen.Colors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CultureDto
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.Locale

private sealed interface SearchState {
	data object Loading : SearchState
	data class Results(val subtitles: List<RemoteSubtitleInfo>) : SearchState
	data class Failed(val forbidden: Boolean) : SearchState
}

private sealed interface DownloadState {
	data object Idle : DownloadState
	data class Downloading(val subtitleId: String) : DownloadState
	data class Adding(val subtitleId: String) : DownloadState
}

/**
 * Side panel to search the server's subtitle providers (e.g. the OpenSubtitles plugin) for
 * subtitles for [item] and download one of them.
 *
 * @param mediaSourceId the version of the item to add the subtitle to, when it has several
 * @param onFinished called once a download completed, with the subtitle that was added
 */
@Composable
fun SubtitleSearchPanel(
	item: BaseItemDto,
	mediaSourceId: String?,
	onFinished: (RemoteSubtitleDownload) -> Unit,
) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val userRepository = koinInject<UserRepository>()
	val userPreferences = koinInject<UserPreferences>()
	val scope = rememberCoroutineScope()
	val searchItemId = mediaSourceId?.toUUIDOrNull() ?: item.id

	var language by rememberSaveable {
		mutableStateOf(defaultSubtitleSearchLanguage(userRepository.currentUser.value, userPreferences))
	}
	var languages by remember { mutableStateOf(emptyList<CultureDto>()) }
	var pickingLanguage by remember { mutableStateOf(false) }
	var search by remember { mutableStateOf<SearchState>(SearchState.Loading) }
	var download by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

	val languageName = languages.findLanguage(language)?.displayName
		?: Locale(language).displayLanguage.ifBlank { language }

	LaunchedEffect(Unit) {
		languages = try {
			api.getSubtitleLanguages()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Timber.w(e, "Unable to load the server languages")
			emptyList()
		}
	}

	LaunchedEffect(language) {
		search = SearchState.Loading
		search = try {
			SearchState.Results(api.searchRemoteSubtitles(searchItemId, language))
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Timber.w(e, "Unable to search for subtitles")
			SearchState.Failed(forbidden = e is InvalidStatusException && e.status in 401..403)
		}
	}

	fun startDownload(subtitle: RemoteSubtitleInfo) {
		val subtitleId = subtitle.id ?: return
		if (download != DownloadState.Idle) return

		download = DownloadState.Downloading(subtitleId)
		scope.launch {
			try {
				val result = api.downloadRemoteSubtitle(
					itemId = item.id,
					mediaSourceId = mediaSourceId,
					subtitleId = subtitleId,
					onDownloaded = { download = DownloadState.Adding(subtitleId) },
				)
				onFinished(result)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Timber.w(e, "Unable to download subtitle %s", subtitleId)
				Toast.makeText(context, R.string.subtitle_download_failed, Toast.LENGTH_LONG).show()
				download = DownloadState.Idle
			}
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
			.onPreviewKeyEvent { event ->
				// Back closes the language list before it closes the panel
				if (pickingLanguage && event.key == Key.Back) {
					if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) pickingLanguage = false
					true
				} else false
			},
		contentAlignment = Alignment.CenterEnd,
	) {
		Column(
			modifier = Modifier
				.padding(vertical = 24.dp, horizontal = 16.dp)
				.width(480.dp)
				.fillMaxHeight()
				.clip(RoundedCornerShape(12.dp))
				.background(Colors.PanelBackground)
				.padding(20.dp),
		) {
			Text(
				text = stringResource(R.string.subtitle_search_title),
				color = Colors.OnSurface,
				fontSize = 18.sp,
				fontWeight = FontWeight.SemiBold,
			)
			Text(
				text = item.subtitleSearchName(),
				color = Colors.OnSurface.copy(alpha = 0.7f),
				fontSize = 13.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			Spacer(Modifier.height(16.dp))

			val languageFocusRequester = remember { FocusRequester() }
			PanelItem(
				onClick = { if (languages.isNotEmpty()) pickingLanguage = !pickingLanguage },
				modifier = Modifier.focusRequester(languageFocusRequester),
			) {
				Text(
					text = stringResource(R.string.subtitle_search_language),
					color = Colors.OnSurface,
					fontSize = 14.sp,
					modifier = Modifier.weight(1f),
				)
				Text(
					text = languageName,
					color = Colors.OnSurface.copy(alpha = 0.7f),
					fontSize = 14.sp,
				)
				Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Colors.OnSurface)
			}

			HorizontalDivider(color = Colors.Divider, modifier = Modifier.padding(vertical = 12.dp))

			if (pickingLanguage) {
				LanguageList(
					languages = languages,
					selected = language,
					onSelect = {
						pickingLanguage = false
						language = it
					},
				)
			} else when (val state = search) {
				SearchState.Loading -> {
					RequestFocus(languageFocusRequester)
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						modifier = Modifier.padding(12.dp),
					) {
						CircularProgressIndicator(color = Colors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
						Message(stringResource(R.string.subtitle_search_searching))
					}
				}

				is SearchState.Failed -> {
					RequestFocus(languageFocusRequester)
					Message(
						stringResource(if (state.forbidden) R.string.subtitle_search_forbidden else R.string.subtitle_search_failed),
						Modifier.padding(12.dp),
					)
				}

				is SearchState.Results -> if (state.subtitles.isEmpty()) {
					RequestFocus(languageFocusRequester)
					Column(
						verticalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier.padding(12.dp),
					) {
						Message(stringResource(R.string.subtitle_search_no_results, languageName))
						Text(
							text = stringResource(R.string.subtitle_search_no_results_hint),
							color = Colors.OnSurface.copy(alpha = 0.7f),
							fontSize = 13.sp,
						)
					}
				} else {
					val firstResultFocusRequester = remember(state) { FocusRequester() }
					RequestFocus(firstResultFocusRequester)
					LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						itemsIndexed(state.subtitles) { index, subtitle ->
							SubtitleResult(
								subtitle = subtitle,
								download = download,
								onClick = { startDownload(subtitle) },
								modifier = if (index == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun LanguageList(
	languages: List<CultureDto>,
	selected: String,
	onSelect: (String) -> Unit,
) {
	val selectedIndex = languages.indexOf(languages.findLanguage(selected)).coerceAtLeast(0)
	val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
	val selectedFocusRequester = remember { FocusRequester() }
	RequestFocus(selectedFocusRequester)

	LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
		itemsIndexed(languages) { index, culture ->
			PanelItem(
				onClick = { culture.threeLetterIsoLanguageName?.let(onSelect) },
				modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocusRequester) else Modifier,
			) {
				Text(
					text = culture.displayName,
					color = Colors.OnSurface,
					fontSize = 14.sp,
					modifier = Modifier.weight(1f),
				)
				if (index == selectedIndex) {
					Icon(Icons.Default.Check, contentDescription = null, tint = Colors.Primary, modifier = Modifier.size(18.dp))
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleResult(
	subtitle: RemoteSubtitleInfo,
	download: DownloadState,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val status = when (download) {
		is DownloadState.Downloading -> R.string.subtitle_downloading.takeIf { download.subtitleId == subtitle.id }
		is DownloadState.Adding -> R.string.subtitle_adding.takeIf { download.subtitleId == subtitle.id }
		DownloadState.Idle -> null
	}

	PanelItem(
		onClick = onClick,
		interactionSource = interactionSource,
		modifier = modifier,
	) {
		Column(
			verticalArrangement = Arrangement.spacedBy(2.dp),
			modifier = Modifier.weight(1f),
		) {
			// Release names are what subtitles are matched on, so show them in full when focused
			Text(
				text = subtitle.name.orEmpty(),
				color = Colors.OnSurface,
				fontSize = 14.sp,
				maxLines = 1,
				overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
				modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
			)

			if (status != null) {
				Text(text = stringResource(status), color = Colors.Primary, fontSize = 12.sp)
			} else {
				SubtitleDetails(subtitle)
			}
		}

		Spacer(Modifier.width(12.dp))

		if (status != null) {
			CircularProgressIndicator(color = Colors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
		} else {
			Column(horizontalAlignment = Alignment.End) {
				subtitle.communityRating?.takeIf { it > 0f }?.let { rating ->
					IconValue(Icons.Default.Star, String.format(Locale.getDefault(), "%.1f", rating), Color(0xFFFFC107))
				}
				subtitle.downloadCount?.let { count ->
					IconValue(Icons.Default.Download, abbreviateCount(count), Colors.OnSurface.copy(alpha = 0.7f))
				}
			}
		}
	}
}

@Composable
private fun SubtitleDetails(subtitle: RemoteSubtitleInfo) {
	val details = buildList {
		if (subtitle.forced == true) add(stringResource(R.string.subtitle_badge_forced))
		if (subtitle.hearingImpaired == true) add(stringResource(R.string.subtitle_badge_hearing_impaired))
		if (subtitle.isMachineTranslated) add(stringResource(R.string.subtitle_badge_machine_translated))
		subtitle.format?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
		subtitle.providerName?.takeIf { it.isNotBlank() }?.let(::add)
	}

	Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
		if (subtitle.isHashMatch == true) {
			Icon(Icons.Default.Check, contentDescription = null, tint = Colors.Primary, modifier = Modifier.size(14.dp))
			Text(
				text = stringResource(R.string.subtitle_badge_hash_match),
				color = Colors.Primary,
				fontSize = 12.sp,
				fontWeight = FontWeight.SemiBold,
			)
		}
		Text(
			text = details.joinToString(" · "),
			color = Colors.OnSurface.copy(alpha = 0.7f),
			fontSize = 12.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun IconValue(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, tint: Color) {
	Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
		Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
		Text(text = value, color = Colors.OnSurface, fontSize = 12.sp)
	}
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
	Text(text = text, color = Colors.OnSurface, fontSize = 14.sp, modifier = modifier)
}

/**
 * A focusable row styled like the preference items.
 */
@Composable
private fun PanelItem(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
	content: @Composable RowScope.() -> Unit,
) {
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(6.dp)

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(shape)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.border(width = if (focused) 1.5.dp else 0.dp, color = if (focused) Colors.FocusedBorder else Color.Transparent, shape = shape)
			.background(if (focused) Colors.FocusedOverlay else Colors.Surface, shape)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		content = content,
	)
}

/**
 * Move the focus to [focusRequester] once it's laid out.
 */
@Composable
private fun RequestFocus(focusRequester: FocusRequester) {
	LaunchedEffect(focusRequester) {
		withFrameNanos { }
		runCatching { focusRequester.requestFocus() }
	}
}

private fun BaseItemDto.subtitleSearchName(): String = when {
	type == BaseItemKind.EPISODE && seriesName != null -> buildString {
		append(seriesName)
		if (parentIndexNumber != null && indexNumber != null) append(" · S%02dE%02d".format(parentIndexNumber, indexNumber))
		name?.let { append(" · ").append(it) }
	}

	else -> listOfNotNull(name, productionYear?.toString()).joinToString(" · ")
}

private fun abbreviateCount(count: Int): String = when {
	count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000f)
	count >= 10_000 -> "${count / 1_000}k"
	count >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", count / 1_000f)
	else -> count.toString()
}
