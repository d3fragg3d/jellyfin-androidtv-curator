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
private data class GenreCollectionInfo(
	@SerialName("RuleId") val ruleId: String,
	@SerialName("Name") val name: String,
	@SerialName("MediaType") val mediaType: String,
	@SerialName("JellyfinId") val jellyfinId: String?,
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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		includeType = requireArguments().getString(Extras.IncludeType)
			?.let(BaseItemKind::fromNameOrNull) ?: BaseItemKind.MOVIE
		title = folder.name

		val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false)
		gridPresenter.numberOfColumns = COLUMNS
		setGridPresenter(gridPresenter)

		val adapter = ArrayObjectAdapter(GenreCardPresenter())
		setAdapter(adapter)

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item !is GenreCardItem) return@OnItemViewClickedListener
			if (item.isAllMovies) {
				navigationRepository.navigate(Destinations.libraryBrowser(folder))
			} else if (item.collectionId != null) {
				val boxSet = BaseItemDto(
					id = item.collectionId,
					name = item.name,
					type = BaseItemKind.BOX_SET,
					displayPreferencesId = item.collectionId.toString(),
				)
				navigationRepository.navigate(Destinations.libraryBrowser(boxSet))
			}
		}

		loadCollections(adapter)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		progressBarManager.enableProgressBar()
	}

	private fun loadCollections(adapter: ArrayObjectAdapter) {
		lifecycleScope.launch {
			progressBarManager.show()

			val mediaType = if (includeType == BaseItemKind.SERIES) "TvShow" else "Movie"
			val isKidsUser = userRepository.currentUser.value?.name == "Kids"
			val userId = userRepository.currentUser.value?.id

			// Fetch collection list
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

			// Resolve hex UUIDs
			val resolved = collections.mapNotNull { collection ->
				val hex = collection.jellyfinId ?: return@mapNotNull null
				runCatching {
					val h = hex.replace("-", "")
					UUID.fromString("${h.substring(0,8)}-${h.substring(8,12)}-${h.substring(12,16)}-${h.substring(16,20)}-${h.substring(20)}")
				}.getOrNull()?.let { id -> collection to id }
			}

			// Fetch all images in parallel
			fun fetchBackdrop(parentId: UUID) = async(Dispatchers.IO) {
				runCatching {
					val result by api.itemsApi.getItems(
						userId = userId,
						parentId = parentId,
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

			val allMoviesImageDeferred = fetchBackdrop(folder.id!!)
			val collectionImageDeferreds = resolved.map { (_, id) -> fetchBackdrop(id) }

			val allMoviesImage = allMoviesImageDeferred.await()
			val collectionImages = collectionImageDeferreds.map { it.await() }

			// Add everything at once
			adapter.add(GenreCardItem("All ${folder.name}", allMoviesImage, isAllMovies = true))
			resolved.zip(collectionImages).forEach { (pair, imageUrl) ->
				val (collection, collectionId) = pair
				adapter.add(GenreCardItem(collection.name, imageUrl, collectionId = collectionId))
			}

			progressBarManager.hide()
		}
	}
}
