package org.jellyfin.androidtv.ui.subtitle

import android.content.Context
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.composable.AppFontTheme
import org.jellyfin.androidtv.ui.preference.screen.ProvidePreferenceColors
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Full screen dialog showing the [SubtitleSearchPanel] over the current screen.
 */
class SubtitleSearchDialog(
	context: Context,
	item: BaseItemDto,
	mediaSourceId: String?,
	private val listener: Listener,
) : ComponentDialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {
	fun interface Listener {
		/**
		 * Called after a subtitle was downloaded. [result] is [RemoteSubtitleDownload.Pending]
		 * when the server didn't add it to the item in time.
		 */
		fun onSubtitleDownloaded(result: RemoteSubtitleDownload)
	}

	init {
		val view = ComposeView(context).apply {
			setContent {
				AppFontTheme {
					ProvidePreferenceColors {
						SubtitleSearchPanel(item, mediaSourceId, ::onFinished)
					}
				}
			}
		}
		setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
	}

	private fun onFinished(result: RemoteSubtitleDownload) {
		val message = when (result) {
			is RemoteSubtitleDownload.Added -> context.getString(
				R.string.subtitle_added,
				result.stream.displayTitle ?: result.stream.title ?: result.stream.language.orEmpty(),
			)

			RemoteSubtitleDownload.Pending -> context.getString(R.string.subtitle_added_pending)
		}
		Toast.makeText(context, message, Toast.LENGTH_LONG).show()

		listener.onSubtitleDownloaded(result)
		dismiss()
	}
}
