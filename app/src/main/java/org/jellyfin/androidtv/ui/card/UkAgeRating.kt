package org.jellyfin.androidtv.ui.card

import androidx.annotation.ColorInt
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * BBFC-style age rating shown as a small badge on poster cards.
 * Non-UK ratings are mapped to their nearest UK equivalent, erring on the higher side.
 */
enum class UkAgeRating(
	val label: String,
	@ColorInt val color: Int,
	@ColorInt val textColor: Int,
	val isTriangle: Boolean = false,
) {
	U("U", 0xFF00A651.toInt(), 0xFFFFFFFF.toInt(), isTriangle = true),
	PG("PG", 0xFFFFCC00.toInt(), 0xFF000000.toInt(), isTriangle = true),
	TWELVE("12", 0xFFF58220.toInt(), 0xFFFFFFFF.toInt()),
	TWELVE_A("12A", 0xFFF58220.toInt(), 0xFFFFFFFF.toInt()),
	FIFTEEN("15", 0xFFE6007E.toInt(), 0xFFFFFFFF.toInt()),
	EIGHTEEN("18", 0xFFD9001B.toInt(), 0xFFFFFFFF.toInt());

	companion object {
		@JvmStatic
		fun fromOfficialRating(officialRating: String?): UkAgeRating? =
			when (officialRating?.trim()?.uppercase()?.removePrefix("GB-")) {
				"U", "UC", "G", "TV-Y", "TV-G" -> U
				"PG", "TV-Y7", "TV-PG" -> PG
				"12" -> TWELVE
				"12A", "PG-13" -> TWELVE_A
				"15", "TV-14" -> FIFTEEN
				"18", "R18", "R", "NC-17", "TV-MA" -> EIGHTEEN
				else -> null
			}
	}
}

/** Release year for cards: "2014" for movies, "2008–2013" / "2019–" for series. */
fun BaseItemDto.getCardYear(): String? {
	val startYear = productionYear ?: premiereDate?.year ?: return null
	if (type != BaseItemKind.SERIES) return startYear.toString()

	val endYear = endDate?.year
	return when {
		status.equals("Continuing", ignoreCase = true) -> "$startYear–"
		endYear != null && endYear != startYear -> "$startYear–$endYear"
		else -> startYear.toString()
	}
}
