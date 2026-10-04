package org.jellyfin.androidtv.ui.card

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Badge background in BBFC style: a rounded triangle for U/PG, a circle for everything else. */
class AgeRatingBadgeDrawable(
	private val rating: UkAgeRating,
	private val strokeWidth: Float,
	cornerRadius: Float,
) : Drawable() {
	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = rating.color
		pathEffect = CornerPathEffect(cornerRadius)
	}
	private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		color = 0x80000000.toInt()
		strokeWidth = this@AgeRatingBadgeDrawable.strokeWidth
		pathEffect = CornerPathEffect(cornerRadius)
	}
	private val path = Path()
	private val oval = RectF()

	override fun draw(canvas: Canvas) {
		val inset = strokeWidth / 2
		val left = bounds.left + inset
		val top = bounds.top + inset
		val right = bounds.right - inset
		val bottom = bounds.bottom - inset

		if (rating.isTriangle) {
			path.reset()
			path.moveTo(bounds.exactCenterX(), top)
			path.lineTo(right, bottom)
			path.lineTo(left, bottom)
			path.close()
			canvas.drawPath(path, fillPaint)
			canvas.drawPath(path, strokePaint)
		} else {
			oval.set(left, top, right, bottom)
			canvas.drawOval(oval, fillPaint)
			canvas.drawOval(oval, strokePaint)
		}
	}

	override fun setAlpha(alpha: Int) {
		fillPaint.alpha = alpha
		strokePaint.alpha = alpha
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		fillPaint.colorFilter = colorFilter
		strokePaint.colorFilter = colorFilter
	}

	@Deprecated("Deprecated in Java")
	override fun getOpacity() = PixelFormat.TRANSLUCENT
}
