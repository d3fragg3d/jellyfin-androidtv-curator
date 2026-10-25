package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

fun colorScheme(): ColorScheme = ColorScheme(
	background = Color(0xFF101010),
	onBackground = Color(0xFFFFFFFF),
	// Curator: translucent buttons that turn white on focus, purple-tinted surfaces (see theme_curator.xml)
	button = Color(0x33FFFFFF),
	onButton = Color(0xFFDDDDDD),
	buttonFocused = Color(0xFFFFFFFF),
	onButtonFocused = Color(0xFF000000),
	buttonDisabled = Color(0x1AFFFFFF),
	onButtonDisabled = Color(0xFF686868),
	buttonActive = Color(0x4DFFFFFF),
	onButtonActive = Color(0xFFFFFFFF),
	input = Color(0xFF3A3648),
	onInput = Color(0xE6CCCCCC),
	inputFocused = Color(0xFFFFFFFF),
	onInputFocused = Color(0xFFFFFFFF),
	recording = Color(0xB3FF7474),
	onRecording = Color(0xFFDDDDDD),
	popover = Color(0xFF1C1729),
)

@Immutable
data class ColorScheme(
	val background: Color,
	val onBackground: Color,

	val button: Color,
	val onButton: Color,
	val buttonFocused: Color,
	val onButtonFocused: Color,
	val buttonDisabled: Color,
	val onButtonDisabled: Color,
	val buttonActive: Color,
	val onButtonActive: Color,

	val input: Color,
	val onInput: Color,
	val inputFocused: Color,
	val onInputFocused: Color,

	val recording: Color,
	val onRecording: Color,

	val popover: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
