package org.jellyfin.androidtv.ui.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.ui.browsing.isKidsUser
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The server's Movies / TV Shows library image is shared by every user, so Kids could see horror artwork.
 * For the Kids user we build the same style of image — a backdrop with the library name centred on it — from a
 * random item in Kids' own library, which the server has already filtered by parental rating.
 */
object CuratorKidsLibraryImage : KoinComponent {
	private val api by inject<ApiClient>()
	private val imageLoader by inject<ImageLoader>()
	private val userRepository by inject<UserRepository>()

	// One image per library per app session, so the tile doesn't change on every scroll
	private val cache = ConcurrentHashMap<UUID, Bitmap>()

	fun appliesTo(library: BaseItemDto?): Boolean =
		library != null && itemKind(library.collectionType) != null && isKidsUser(userRepository.currentUser.value?.name)

	/**
	 * Load the kid-safe image into [imageView]. [isStillBound] guards against the card being recycled meanwhile.
	 */
	fun load(imageView: AsyncImageView, library: BaseItemDto, width: Int, height: Int, isStillBound: () -> Boolean) {
		imageView.load(aspectRatio = ImageHelper.ASPECT_RATIO_16_9)

		imageView.doOnAttach { view ->
			view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
				val bitmap = cache[library.id] ?: build(view.context, library, width, height)?.also { cache[library.id] = it }
				if (bitmap != null && isStillBound()) {
					imageView.load(placeholder = bitmap.toDrawable(view.resources), aspectRatio = ImageHelper.ASPECT_RATIO_16_9)
				}
			}
		}
	}

	private fun itemKind(collectionType: CollectionType?) = when (collectionType) {
		CollectionType.MOVIES -> BaseItemKind.MOVIE
		CollectionType.TVSHOWS -> BaseItemKind.SERIES
		else -> null
	}

	private suspend fun build(context: Context, library: BaseItemDto, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
		runCatching {
			val items by api.itemsApi.getItems(
				userId = userRepository.currentUser.value?.id,
				parentId = library.id,
				includeItemTypes = setOfNotNull(itemKind(library.collectionType)),
				recursive = true,
				limit = 10,
				sortBy = setOf(ItemSortBy.RANDOM),
				enableImages = true,
				imageTypeLimit = 1,
				enableImageTypes = setOf(ImageType.BACKDROP),
			)
			val url = items.items.firstNotNullOfOrNull { item ->
				item.itemBackdropImages.firstOrNull()?.getUrl(api, maxWidth = width, maxHeight = height)
			} ?: return@runCatching null

			val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
			val backdrop = imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null

			drawTile(backdrop, library.name.orEmpty(), width, height)
		}.onFailure { Timber.w(it, "Failed to build kids library image for ${library.name}") }.getOrNull()
	}

	private fun drawTile(backdrop: Bitmap, title: String, width: Int, height: Int): Bitmap {
		val tile = createBitmap(width, height)
		val canvas = Canvas(tile)

		// Centre-crop the backdrop into the tile
		val scale = maxOf(width.toFloat() / backdrop.width, height.toFloat() / backdrop.height)
		val srcWidth = (width / scale).toInt()
		val srcHeight = (height / scale).toInt()
		val left = (backdrop.width - srcWidth) / 2
		val top = (backdrop.height - srcHeight) / 2
		canvas.drawBitmap(backdrop, Rect(left, top, left + srcWidth, top + srcHeight), Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))

		// Dim so the title reads on bright artwork
		canvas.drawColor(Color.argb(90, 0, 0, 0))

		val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.WHITE
			typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
			textSize = height * 0.24f
			textAlign = Paint.Align.CENTER
			setShadowLayer(height * 0.04f, 0f, height * 0.01f, Color.argb(160, 0, 0, 0))
		}
		val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
		canvas.drawText(title, width / 2f, baseline, paint)

		return tile
	}
}
