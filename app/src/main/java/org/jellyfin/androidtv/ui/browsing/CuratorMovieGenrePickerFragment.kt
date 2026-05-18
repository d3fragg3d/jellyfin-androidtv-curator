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
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.GridButtonPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.android.ext.android.inject

class CuratorMovieGenrePickerFragment : VerticalGridSupportFragment() {

	companion object {
		private const val ALL_MOVIES_ID = -1
		private const val COLUMNS = 5
	}

	private val api by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()

	private lateinit var folder: BaseItemDto

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		title = folder.name

		val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false)
		gridPresenter.numberOfColumns = COLUMNS
		setGridPresenter(gridPresenter)

		val adapter = ArrayObjectAdapter(GridButtonPresenter(190, 110))
		setAdapter(adapter)

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val button = when (item) {
				is GridButtonBaseRowItem -> item.gridButton
				is GridButton -> item
				else -> return@OnItemViewClickedListener
			}
			val destination = if (button.id == ALL_MOVIES_ID) {
				Destinations.libraryBrowser(folder)
			} else {
				Destinations.libraryBrowserByGenre(folder, button.text)
			}
			navigationRepository.navigate(destination)
		}

		loadGenres(adapter)
	}

	private fun loadGenres(adapter: ArrayObjectAdapter) {
		lifecycleScope.launch {
			adapter.add(GridButton(ALL_MOVIES_ID, getString(R.string.lbl_all_movies)))

			val genres = withContext(Dispatchers.IO) {
				runCatching {
					api.genresApi.getGenres(
						parentId = folder.id,
						sortBy = setOf(ItemSortBy.SORT_NAME),
					).content.items.orEmpty()
				}.getOrDefault(emptyList())
			}

			genres.forEachIndexed { index, genre ->
				val name = genre.name ?: return@forEachIndexed
				adapter.add(GridButton(index, name))
			}
		}
	}
}
