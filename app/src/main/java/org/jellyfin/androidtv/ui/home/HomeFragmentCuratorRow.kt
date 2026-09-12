package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

class HomeFragmentCuratorRow(
	private val featuredCollections: List<BaseItemDto>,
	private val userId: UUID,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		featuredCollections.forEach { collection ->
			val request = GetItemsRequest(
				userId = userId,
				parentId = collection.id,
				// BOX_SET included because some collections (e.g. "franchise box-sets mode"
				// rules in the Curator plugin) have other BoxSets as children rather than
				// movies directly — clicking one navigates to its own collection browser
				// via ItemLauncher, same as any other BoxSet.
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET),
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
			)
			HomeFragmentBrowseRowDefRow(BrowseRowDef(collection.name ?: return@forEach, request, 100))
				.addToRowsAdapter(context, cardPresenter, rowsAdapter)
		}
	}
}
