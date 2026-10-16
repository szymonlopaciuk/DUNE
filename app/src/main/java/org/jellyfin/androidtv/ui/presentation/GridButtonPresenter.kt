package org.jellyfin.androidtv.ui.presentation

import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.composable.AppFontTheme
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem

class GridButtonPresenter @JvmOverloads constructor(
    private val width: Int = 110,
    private val imageHeight: Int = 110,
) : Presenter() {
    private class ComposeViewWrapper(composeView: ComposeView) : FrameLayout(composeView.context) {
        init {
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            addView(composeView)
        }

        // Hack to prevent Compose crash with leanback presenters
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (isAttachedToWindow) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            else setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
        }
    }

    inner class ViewHolder(
        private val composeView: ComposeView,
    ) : Presenter.ViewHolder(ComposeViewWrapper(composeView)) {
        private var isFocused by mutableStateOf(false)

        init {
            view.setOnFocusChangeListener { _, hasFocus ->
                isFocused = hasFocus
            }
        }

        fun bind(value: GridButton) = composeView.setContent { AppFontTheme {
            val backgroundColor = if (isFocused) {
                colorResource(android.R.color.white)
            } else {
                colorResource(R.color.button_default_normal_background)
            }

            val textColor = if (isFocused) {
                colorResource(android.R.color.black)
            } else {
                colorResource(R.color.button_default_normal_text)
            }

            Box(
                modifier = Modifier
                    .width(width.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(backgroundColor)
            ) {
                if (value.imageRes != null) {
                    Image(
                        painter = painterResource(value.imageRes),
                        contentDescription = value.text,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width.dp, imageHeight.dp)
                    )
                }

                Text(
                    text = value.text,
                    style = LocalTextStyle.current.copy(
                        color = textColor,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .padding(15.dp, 10.dp)
                        .align(Alignment.BottomStart)
                )
            }
        } }
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup): ViewHolder =
        ViewHolder(ComposeView(parent.context))

    // This version is called first by the framework
    override fun onBindViewHolder(
        @NonNull viewHolder: Presenter.ViewHolder,
        @NonNull item: Any,
        @NonNull payloads: MutableList<Any>
    ) {
        onBindViewHolder(viewHolder, item)
    }

    // This version is called for backward compatibility
    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        if (viewHolder !is ViewHolder || item == null) return

        when (item) {
            is GridButtonBaseRowItem -> viewHolder.bind(item.gridButton)
            is GridButton -> viewHolder.bind(item)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        // No cleanup needed
    }

    @Suppress("UNUSED_PARAMETER")
    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder) {
        // No action needed
    }
}
