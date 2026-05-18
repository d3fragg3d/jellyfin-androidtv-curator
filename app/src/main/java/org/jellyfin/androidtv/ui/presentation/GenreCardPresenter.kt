package org.jellyfin.androidtv.ui.presentation

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
)

class GenreCardPresenter : Presenter() {
	companion object {
		const val CARD_WIDTH = 260
		const val CARD_HEIGHT = 146
	}

	private class ComposeViewWrapper(composeView: ComposeView) : FrameLayout(composeView.context) {
		init {
			isFocusable = true
			isFocusableInTouchMode = true
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			addView(composeView)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			if (isAttachedToWindow) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			else setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
		}
	}

	inner class ViewHolder(
		private val composeView: ComposeView,
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView)) {
		fun bind(item: GenreCardItem) = composeView.setContent {
			val bgColor = if (item.isAllMovies) Color(0xFF00A4DC) else Color(0xFF1A237E)

			Box(
				modifier = Modifier
					.size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(bgColor),
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
