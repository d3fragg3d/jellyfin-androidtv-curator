package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.GenreCardItem
import org.jellyfin.androidtv.ui.presentation.GenreCardPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.koin.android.ext.android.inject

class CuratorMovieGenrePickerFragment : VerticalGridSupportFragment() {

	companion object {
		private const val COLUMNS = 6
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
			val destination = if (item.isAllMovies) {
				Destinations.libraryBrowser(folder)
			} else {
				Destinations.libraryBrowserByGenre(folder, item.name)
			}
			navigationRepository.navigate(destination)
		}

		loadGenres(adapter)
	}

	private fun loadGenres(adapter: ArrayObjectAdapter) {
		lifecycleScope.launch {
			adapter.add(GenreCardItem("All ${folder.name}", null, isAllMovies = true))

			val genres = withContext(Dispatchers.IO) {
				runCatching {
					api.genresApi.getGenres(
						parentId = folder.id,
						sortBy = setOf(ItemSortBy.SORT_NAME),
					).content.items.orEmpty()
				}.getOrDefault(emptyList())
			}

			val userId = userRepository.currentUser.value?.id

			genres.forEach { genre ->
				val name = genre.name ?: return@forEach
				val imageUrl = withContext(Dispatchers.IO) {
					runCatching {
						val result by api.itemsApi.getItems(
							userId = userId,
							parentId = folder.id,
							includeItemTypes = setOf(includeType),
							genres = setOf(name),
							recursive = true,
							limit = 1,
							sortBy = setOf(ItemSortBy.RANDOM),
							enableImages = true,
							imageTypeLimit = 1,
							enableImageTypes = setOf(ImageType.BACKDROP),
						)
						result.items?.firstOrNull()
							?.itemBackdropImages?.firstOrNull()
							?.getUrl(api, maxWidth = 520, maxHeight = 292)
					}.getOrNull()
				}
				adapter.add(GenreCardItem(name, imageUrl))
			}
		}
	}
}
