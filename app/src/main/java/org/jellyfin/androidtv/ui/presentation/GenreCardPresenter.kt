package org.jellyfin.androidtv.ui.presentation

import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage

data class GenreCardItem(
	val name: String,
	val imageUrl: String?,
	val isAllMovies: Boolean = false,
	val collectionId: java.util.UUID? = null,
)

class GenreCardPresenter : Presenter() {
	companion object {
		const val CARD_WIDTH = 260
		const val CARD_HEIGHT = 146
	}

	private class ComposeViewWrapper(
		composeView: ComposeView,
		val focused: androidx.compose.runtime.MutableState<Boolean>,
	) : FrameLayout(composeView.context) {
		init {
			isFocusable = true
			isFocusableInTouchMode = true
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			addView(composeView)
		}

		override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
			super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
			focused.value = gainFocus
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			if (isAttachedToWindow) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			else setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
		}
	}

	inner class ViewHolder(
		private val composeView: ComposeView,
		private val focused: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false),
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView, focused)) {
		fun bind(item: GenreCardItem) = composeView.setContent {
			val bgColor = if (item.isAllMovies) Color(0xFF00A4DC) else Color(0xFF1A237E)
			val shape = RoundedCornerShape(4.dp)

			Box(
				modifier = Modifier
					.size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
					.clip(shape)
					.background(bgColor)
					.then(
						if (focused.value) Modifier.border(3.dp, Color(0xFF00A4DC), shape)
						else Modifier
					),
			) {
				if (item.imageUrl != null) {
					AsyncImage(
						url = item.imageUrl,
						aspectRatio = CARD_WIDTH.toFloat() / CARD_HEIGHT,
						modifier = Modifier.fillMaxSize(),
					)

					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								Brush.verticalGradient(
									0.45f to Color.Transparent,
									1.0f to Color(0xD9000000),
								)
							)
					)
				}

				Text(
					text = item.name,
					style = TextStyle(
						color = Color.White,
						fontSize = 15.sp,
						fontWeight = FontWeight.SemiBold,
					),
					modifier = Modifier
						.padding(horizontal = 12.dp, vertical = 10.dp)
						.align(Alignment.BottomStart),
				)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
		ViewHolder(ComposeView(parent.context))

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (viewHolder !is ViewHolder || item !is GenreCardItem) return
		viewHolder.bind(item)
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
	override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder) = Unit
}
