package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.MenuCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.ui.playback.setSubtitleIndex
import org.jellyfin.androidtv.ui.subtitle.RemoteSubtitleDownload
import org.jellyfin.androidtv.ui.subtitle.SubtitleSearchDialog
import org.jellyfin.androidtv.ui.subtitle.supportsRemoteSubtitles
import org.jellyfin.sdk.model.api.MediaStreamType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class ClosedCaptionsAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue), KoinComponent {
	private companion object {
		const val SEARCH_ONLINE_ID = Int.MIN_VALUE
	}

	private val userRepository by inject<UserRepository>()
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_select_subtitle)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		if (playbackController.currentStreamInfo == null) {
			Timber.w("StreamInfo null trying to obtain subtitles")
			Toast.makeText(context, "Unable to obtain subtitle info", Toast.LENGTH_LONG).show()
			return
		}

		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		removePopup()
		popup = PopupMenu(context, view, Gravity.END).apply {
			with(menu) {
				var order = 0
				add(0, -1, order++, context.getString(R.string.lbl_none)).apply {
					isChecked = playbackController.subtitleStreamIndex == -1
				}

				for (sub in playbackController.currentMediaSource.mediaStreams.orEmpty()) {
					if (sub.type != MediaStreamType.SUBTITLE) continue

					add(0, sub.index, order++, sub.displayTitle).apply {
						isChecked = sub.index == playbackController.subtitleStreamIndex
					}
				}

				setGroupCheckable(0, true, false)

				val item = playbackController.currentlyPlayingItem
				if (!playbackController.isLiveTv && item?.supportsRemoteSubtitles(userRepository.currentUser.value) == true) {
					add(1, SEARCH_ONLINE_ID, order, context.getString(R.string.subtitle_search_online))
					MenuCompat.setGroupDividerEnabled(this, true)
				}
			}
			setOnDismissListener {
				videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
				popup = null
			}
			setOnMenuItemClickListener { item ->
				if (item.itemId == SEARCH_ONLINE_ID) searchOnline(playbackController, context)
				else playbackController.setSubtitleIndex(item.itemId)
				true
			}
		}
		popup?.show()
	}

	private fun searchOnline(playbackController: PlaybackController, context: Context) {
		val item = playbackController.currentlyPlayingItem ?: return
		val wasPlaying = playbackController.isPlaying
		if (wasPlaying) playbackController.pause()

		var downloaded = false
		SubtitleSearchDialog(context, item, playbackController.currentMediaSource?.id) { result ->
			downloaded = true
			when (result) {
				is RemoteSubtitleDownload.Added -> playbackController.reloadWithSubtitle(result.stream.index)
				RemoteSubtitleDownload.Pending -> if (wasPlaying) playbackController.play(playbackController.currentPosition)
			}
		}.apply {
			setOnDismissListener {
				if (!downloaded && wasPlaying) playbackController.play(playbackController.currentPosition)
			}
		}.show()
	}

	fun removePopup() {
		popup?.dismiss()
	}
}
