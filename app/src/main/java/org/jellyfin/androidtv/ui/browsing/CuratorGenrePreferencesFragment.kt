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
import org.jellyfin.androidtv.ui.presentation.GenrePreferenceItem
import org.jellyfin.androidtv.ui.presentation.GenrePreferencePresenter
import org.jellyfin.androidtv.ui.presentation.PreferenceState
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.koin.android.ext.android.inject
import java.util.UUID

@Serializable
private data class UserPreferencesDto(
	@SerialName("PreferredRuleIds") val preferredRuleIds: List<String> = emptyList(),
	@SerialName("AvoidedRuleIds") val avoidedRuleIds: List<String> = emptyList(),
)

class CuratorGenrePreferencesFragment : VerticalGridSupportFragment() {

	companion object {
		private const val COLUMNS = 4
		private val json = Json { ignoreUnknownKeys = true }
	}

	private val api by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()

	private lateinit var rowAdapter: ArrayObjectAdapter

	// Tracks current state for all genre collections; ruleId -> state
	private val pendingStates = HashMap<String, PreferenceState>()
	private var isDirty = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		title = "Genre Preferences"

		val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false)
		gridPresenter.numberOfColumns = COLUMNS
		setGridPresenter(gridPresenter)

		rowAdapter = ArrayObjectAdapter(GenrePreferencePresenter())
		setAdapter(rowAdapter)

		onItemViewClickedListener = OnItemViewClickedListener { viewHolder, item, _, _ ->
			if (item !is GenrePreferenceItem) return@OnItemViewClickedListener
			val holder = viewHolder as? GenrePreferencePresenter.ViewHolder ?: return@OnItemViewClickedListener

			val next = when (pendingStates[item.ruleId] ?: PreferenceState.NONE) {
				PreferenceState.NONE -> PreferenceState.PREFERRED
				PreferenceState.PREFERRED -> PreferenceState.AVOIDED
				PreferenceState.AVOIDED -> PreferenceState.NONE
			}
			pendingStates[item.ruleId] = next
			holder.updateState(next)
			isDirty = true
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		if (rowAdapter.size() > 0) return
		progressBarManager.enableProgressBar()
		loadPreferences()
	}

	override fun onStop() {
		super.onStop()
		if (isDirty) savePreferences()
	}

	private fun loadPreferences() {
		lifecycleScope.launch {
			progressBarManager.show()

			val userId = userRepository.currentUser.value?.id
			val isKidsUser = userRepository.currentUser.value?.name == "Kids"

			// Fetch genre collections and current preferences in parallel
			val collectionsDeferred = async(Dispatchers.IO) {
				runCatching {
					val movieResp = api.request(
						method = HttpMethod.GET,
						pathTemplate = "/Curator/genre-collections",
						queryParameters = mapOf("mediaType" to "Movie"),
					)
					val tvResp = api.request(
						method = HttpMethod.GET,
						pathTemplate = "/Curator/genre-collections",
						queryParameters = mapOf("mediaType" to "TvShow"),
					)
					val movies = json.decodeFromString<List<GenreCollectionInfo>>(movieResp.body.decodeToString())
					val tv = json.decodeFromString<List<GenreCollectionInfo>>(tvResp.body.decodeToString())
					(movies + tv)
						.filter { if (isKidsUser) it.kidsOnly else !it.kidsOnly }
						.sortedBy { it.name }
				}.getOrDefault(emptyList())
			}

			val prefsDeferred = async(Dispatchers.IO) {
				if (userId == null) return@async UserPreferencesDto()
				runCatching {
					val resp = api.request(
						method = HttpMethod.GET,
						pathTemplate = "/Curator/users/{userId}/preferences",
						pathParameters = mapOf("userId" to userId.toString()),
					)
					json.decodeFromString<UserPreferencesDto>(resp.body.decodeToString())
				}.getOrDefault(UserPreferencesDto())
			}

			val collections = collectionsDeferred.await()
			val prefs = prefsDeferred.await()

			// Build initial pending state map from server preferences
			for (collection in collections) {
				pendingStates[collection.ruleId] = when {
					prefs.preferredRuleIds.contains(collection.ruleId) -> PreferenceState.PREFERRED
					prefs.avoidedRuleIds.contains(collection.ruleId) -> PreferenceState.AVOIDED
					else -> PreferenceState.NONE
				}
			}

			// Pre-parse UUIDs for custom collections
			val customIds: List<UUID?> = collections.map { c ->
				if (c.type == "custom" && c.jellyfinId != null) {
					val h = c.jellyfinId.replace("-", "")
					runCatching {
						UUID.fromString("${h.substring(0,8)}-${h.substring(8,12)}-${h.substring(12,16)}-${h.substring(16,20)}-${h.substring(20)}")
					}.getOrNull()
				} else null
			}

			// Fetch backdrop images in parallel
			fun fetchBackdropByParentId(parentId: UUID) = async(Dispatchers.IO) {
				runCatching {
					val result by api.itemsApi.getItems(
						userId = userId,
						parentId = parentId,
						includeItemTypes = setOf(BaseItemKind.MOVIE),
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
						includeItemTypes = setOf(BaseItemKind.MOVIE),
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

			val imageDeferreds = collections.mapIndexed { index, c ->
				when (c.type) {
					"custom" -> customIds[index]?.let { fetchBackdropByParentId(it) }
					"native" -> fetchBackdropByGenres(c.genres)
					else -> null
				}
			}
			val images = imageDeferreds.map { it?.await() }

			collections.forEachIndexed { index, c ->
				val collectionId = customIds[index]
				val imageUrl = images[index]
					?: collectionId?.let {
						api.imageApi.getItemImageUrl(
							itemId = it,
							imageType = ImageType.PRIMARY,
							maxWidth = 520,
							maxHeight = 292,
						)
					}
				rowAdapter.add(
					GenrePreferenceItem(
						ruleId = c.ruleId,
						name = c.name,
						imageUrl = imageUrl,
						mediaType = c.mediaType,
						state = pendingStates[c.ruleId] ?: PreferenceState.NONE,
					)
				)
			}

			progressBarManager.hide()
		}
	}

	private fun savePreferences() {
		val preferred = pendingStates.filter { it.value == PreferenceState.PREFERRED }.keys.toList()
		val avoided = pendingStates.filter { it.value == PreferenceState.AVOIDED }.keys.toList()
		val userId = userRepository.currentUser.value?.id ?: return

		lifecycleScope.launch(Dispatchers.IO) {
			runCatching {
				api.request(
					method = HttpMethod.POST,
					pathTemplate = "/Curator/users/{userId}/preferences",
					pathParameters = mapOf("userId" to userId.toString()),
					requestBody = UserPreferencesDto(
						preferredRuleIds = preferred,
						avoidedRuleIds = avoided,
					),
				)
			}
		}
	}
}
