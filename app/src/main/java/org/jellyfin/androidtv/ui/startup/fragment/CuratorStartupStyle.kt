package org.jellyfin.androidtv.ui.startup.fragment

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

// Shared look for the Curator startup screens ("Who's watching?" and "Manage server")

internal val CuratorMutedText = Color(0xFF8C8A9C)
internal val CuratorOutline = Color(0xFF3A3648)
internal val CuratorSurface = Color(0xFF17131F)

@Composable
internal fun CuratorStartupBackground(content: @Composable BoxScope.() -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black)
			.drawBehind {
				// Faint purple glow behind the content, fading to black at the edges
				drawRect(
					Brush.radialGradient(
						colors = listOf(Color(0xFF261C40), Color(0xFF0E0B18), Color.Black),
						center = Offset(size.width / 2, size.height * 0.45f),
						radius = size.width * 0.6f,
					)
				)
			},
	) {
		Image(
			painter = painterResource(R.drawable.curator_symbol),
			contentDescription = null,
			modifier = Modifier
				.align(Alignment.TopStart)
				.padding(start = 48.dp, top = 32.dp)
				.height(40.dp),
		)

		content()
	}
}

@Composable
internal fun CuratorScreenTitle(text: String) {
	Text(
		text = text,
		color = Color.White,
		fontSize = 34.sp,
		fontWeight = FontWeight.Medium,
		letterSpacing = 0.5.sp,
	)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CuratorPillButton(
	text: String,
	@DrawableRes icon: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val contentColor = if (focused) Color.Black else CuratorMutedText

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.clip(RoundedCornerShape(50))
			.background(if (focused) Color.White else Color.Transparent)
			.border(1.5.dp, if (focused) Color.White else CuratorOutline, RoundedCornerShape(50))
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.padding(horizontal = 22.dp, vertical = 10.dp),
	) {
		Image(
			painter = painterResource(icon),
			contentDescription = null,
			colorFilter = ColorFilter.tint(contentColor),
			modifier = Modifier.size(18.dp),
		)
		Spacer(Modifier.width(10.dp))
		Text(
			text = text,
			color = contentColor,
			fontSize = 15.sp,
		)
	}
}
