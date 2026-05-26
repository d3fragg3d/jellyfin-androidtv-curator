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
import androidx.compose.runtime.MutableState
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

enum class PreferenceState { NONE, PREFERRED, AVOIDED }

data class GenrePreferenceItem(
	val ruleId: String,
	val name: String,
	val imageUrl: String?,
	val mediaType: String,
	var state: PreferenceState = PreferenceState.NONE,
)

class GenrePreferencePresenter : Presenter() {
	companion object {
		const val CARD_WIDTH = 260
		const val CARD_HEIGHT = 130
	}

	private class ComposeViewWrapper(
		composeView: ComposeView,
		val focused: MutableState<Boolean>,
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
		private val focused: MutableState<Boolean> = mutableStateOf(false),
		private val itemState: MutableState<PreferenceState> = mutableStateOf(PreferenceState.NONE),
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView, focused)) {

		fun bind(item: GenrePreferenceItem) {
			itemState.value = item.state
			composeView.setContent {
				val state = itemState.value
				val isFocused = focused.value
				val shape = RoundedCornerShape(4.dp)

				val borderColor = when {
					state == PreferenceState.PREFERRED -> Color(0xFF4CAF50)
					state == PreferenceState.AVOIDED -> Color(0xFFE53935)
					isFocused -> Color(0xFF00A4DC)
					else -> Color.Transparent
				}

				Box(
					modifier = Modifier
						.size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
						.clip(shape)
						.background(Color(0xFF1A237E))
						.border(3.dp, borderColor, shape),
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
										0.3f to Color.Transparent,
										1.0f to Color(0xE6000000),
									)
								)
						)
					}

					if (state != PreferenceState.NONE) {
						Box(
							modifier = Modifier
								.align(Alignment.TopEnd)
								.padding(6.dp)
								.background(
									color = if (state == PreferenceState.PREFERRED) Color(0xFF4CAF50) else Color(0xFFE53935),
									shape = RoundedCornerShape(3.dp),
								)
								.padding(horizontal = 5.dp, vertical = 2.dp)
						) {
							Text(
								text = if (state == PreferenceState.PREFERRED) "▲ More" else "▼ Less",
								style = TextStyle(
									color = Color.White,
									fontSize = 11.sp,
									fontWeight = FontWeight.Bold,
								),
							)
						}
					}

					val displayName = if (item.mediaType == "TvShow") "${item.name} (TV)" else item.name
					Text(
						text = displayName,
						style = TextStyle(
							color = Color.White,
							fontSize = 14.sp,
							fontWeight = FontWeight.SemiBold,
						),
						modifier = Modifier
							.padding(horizontal = 10.dp, vertical = 8.dp)
							.align(Alignment.BottomStart),
					)
				}
			}
		}

		fun updateState(newState: PreferenceState) {
			itemState.value = newState
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
		ViewHolder(ComposeView(parent.context))

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (viewHolder !is ViewHolder || item !is GenrePreferenceItem) return
		viewHolder.bind(item)
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
	override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder) = Unit
}
