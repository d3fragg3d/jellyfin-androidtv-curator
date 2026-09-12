package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.GenreCardItem
import org.jellyfin.androidtv.ui.presentation.GenreCardPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.koin.android.ext.android.inject
import java.util.UUID

@Serializable
internal data class GenreCollectionInfo(
	@SerialName("RuleId") val ruleId: String,
	@SerialName("Name") val name: String,
	@SerialName("MediaType") val mediaType: String,
	@SerialName("Type") val type: String,
	@SerialName("Genres") val genres: List<String> = emptyList(),
	@SerialName("ExcludeGenres") val excludeGenres: List<String> = emptyList(),
	@SerialName("JellyfinId") val jellyfinId: String? = null,
	@SerialName("ItemCount") val itemCount: Int,
	@SerialName("KidsOnly") val kidsOnly: Boolean,
)

class CuratorMovieGenrePickerFragment : VerticalGridSupportFragment() {

	companion object {
		private const val COLUMNS = 6
		private val json = Json { ignoreUnknownKeys = true }
	}

	private val api by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private var includeType: BaseItemKind = BaseItemKind.MOVIE
	private var includeTypeArg: String = "Movie"
	private lateinit var rowAdapter: ArrayObjectAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		includeTypeArg = requireArguments().getString(Extras.IncludeType) ?: "Movie"
		includeType = BaseItemKind.fromNameOrNull(includeTypeArg) ?: BaseItemKind.MOVIE
		title = folder.name

		val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false)
		gridPresenter.numberOfColumns = COLUMNS
		setGridPresenter(gridPresenter)

		rowAdapter = ArrayObjectAdapter(GenreCardPresenter())
		setAdapter(rowAdapter)

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item !is GenreCardItem) return@OnItemViewClickedListener
			when {
				item.isAllMovies -> {
					navigationRepository.navigate(Destinations.libraryBrowser(folder))
				}
				item.collectionId != null -> {
					val boxSet = BaseItemDto(
						id = item.collectionId,
						name = item.name,
						type = BaseItemKind.BOX_SET,
						displayPreferencesId = item.collectionId.toString(),
					)
					navigationRepository.navigate(Destinations.libraryBrowser(boxSet, includeTypeArg))
				}
				item.genreNames.isNotEmpty() -> {
					navigationRepository.navigate(
						Destinations.libraryBrowserByGenre(folder, item.genreNames.first())
					)
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		progressBarManager.enableProgressBar()
		loadCollections(rowAdapter)
	}

	private fun loadCollections(adapter: ArrayObjectAdapter) {
		if (adapter.size() > 0) return
		lifecycleScope.launch {
			progressBarManager.show()

			val mediaType = if (includeType == BaseItemKind.SERIES) "TvShow" else "Movie"
			val isKidsUser = userRepository.currentUser.value?.name == "Kids"
			val userId = userRepository.currentUser.value?.id

			val collections = withContext(Dispatchers.IO) {
				runCatching {
					val response = api.request(
						method = HttpMethod.GET,
						pathTemplate = "/Curator/genre-collections",
						queryParameters = mapOf("mediaType" to mediaType),
					)
					json.decodeFromString<List<GenreCollectionInfo>>(response.body.decodeToString())
						.filter { if (isKidsUser) it.kidsOnly else !it.kidsOnly }
						.sortedBy { it.name }
				}.getOrDefault(emptyList())
			}

			// Pre-parse UUIDs for custom collections so we don't duplicate the logic
			val customIds: List<UUID?> = collections.map { collection ->
				if (collection.type == "custom" && collection.jellyfinId != null) {
					val h = collection.jellyfinId.replace("-", "")
					runCatching {
						UUID.fromString("${h.substring(0,8)}-${h.substring(8,12)}-${h.substring(12,16)}-${h.substring(16,20)}-${h.substring(20)}")
					}.getOrNull()
				} else null
			}

			fun fetchBackdropByParentId(parentId: UUID, typeFilter: BaseItemKind? = includeType) = async(Dispatchers.IO) {
				runCatching {
					val result by api.itemsApi.getItems(
						userId = userId,
						parentId = parentId,
						includeItemTypes = typeFilter?.let { setOf(it) },
						recursive = true,
						limit = 10,
						sortBy = setOf(ItemSortBy.RANDOM),
						enableImages = true,
						imageTypeLimit = 1,
						enableImageTypes = setOf(ImageType.BACKDROP),
					)
					result.items?.firstNotNullOfOrNull { item ->
						item.itemBackdropImages.firstOrNull()?.getUrl(api, maxWidth = 520, maxHeight = 292)
					}
				}.getOrNull()
			}

			fun fetchBackdropByGenres(genres: List<String>) = async(Dispatchers.IO) {
				runCatching {
					val result by api.itemsApi.getItems(
						userId = userId,
						genres = genres.toSet(),
						includeItemTypes = setOf(includeType),
						recursive = true,
						limit = 10,
						sortBy = setOf(ItemSortBy.RANDOM),
						enableImages = true,
						imageTypeLimit = 1,
						enableImageTypes = setOf(ImageType.BACKDROP),
					)
					result.items?.firstNotNullOfOrNull { item ->
						item.itemBackdropImages.firstOrNull()?.getUrl(api, maxWidth = 520, maxHeight = 292)
					}
				}.getOrNull()
			}

			val allMoviesImageDeferred = fetchBackdropByParentId(folder.id!!)
			val collectionImageDeferreds = collections.mapIndexed { index, collection ->
				when (collection.type) {
					"custom" -> customIds[index]?.let { fetchBackdropByParentId(it, null) }
					"native" -> fetchBackdropByGenres(collection.genres)
					else -> null
				}
			}

			val allMoviesImage = allMoviesImageDeferred.await()
			val collectionImages = collectionImageDeferreds.map { it?.await() }

			adapter.add(GenreCardItem("All ${folder.name}", allMoviesImage, isAllMovies = true))
			collections.forEachIndexed { index, collection ->
				val collectionId = customIds[index]
				val imageUrl = collectionImages[index]
					?: collectionId?.let {
						api.imageApi.getItemImageUrl(
							itemId = it,
							imageType = ImageType.PRIMARY,
							maxWidth = 520,
							maxHeight = 292,
						)
					}
				val cardItem = when (collection.type) {
					"custom" -> GenreCardItem(
						name = collection.name,
						imageUrl = imageUrl,
						collectionId = collectionId,
					)
					"native" -> GenreCardItem(
						name = collection.name,
						imageUrl = imageUrl,
						genreNames = collection.genres,
					)
					else -> return@forEachIndexed
				}
				adapter.add(cardItem)
			}

			progressBarManager.hide()
		}
	}
}
