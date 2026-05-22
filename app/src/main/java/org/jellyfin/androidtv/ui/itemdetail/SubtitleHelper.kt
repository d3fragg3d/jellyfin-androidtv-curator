package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import timber.log.Timber
import java.util.UUID

private val LANGUAGES = linkedMapOf(
    "English" to "eng",
    "Spanish" to "spa",
    "French" to "fra",
    "German" to "deu",
    "Italian" to "ita",
    "Portuguese" to "por",
    "Dutch" to "nld",
    "Japanese" to "jpn",
    "Chinese" to "zho",
    "Korean" to "kor",
    "Arabic" to "ara",
    "Russian" to "rus",
)

fun FullDetailsFragment.showSubtitlePicker(anchor: View, api: ApiClient, item: BaseItemDto) {
    val existingSubs = item.mediaSources
        ?.flatMap { it.mediaStreams ?: emptyList() }
        ?.filter { it.type == MediaStreamType.SUBTITLE }
        ?: emptyList()

    if (existingSubs.isNotEmpty()) {
        showExistingSubtitlesDialog(anchor, api, item.id!!, existingSubs)
    } else {
        showLanguagePicker(anchor, api, item.id!!)
    }
}

private fun FullDetailsFragment.showExistingSubtitlesDialog(
    anchor: View,
    api: ApiClient,
    itemId: UUID,
    existing: List<MediaStream>,
) {
    val labels = existing.map { stream ->
        stream.displayTitle ?: buildString {
            stream.language?.uppercase()?.let { append(it) } ?: append("Unknown")
            if (stream.isForced) append(" · Forced")
            if (stream.isHearingImpaired) append(" · SDH")
            stream.codec?.let { append(" ($it)") }
            if (stream.isExternal) append(" · External")
        }
    }.toTypedArray()

    AlertDialog.Builder(requireContext())
        .setTitle(R.string.lbl_subtitle_track)
        .setItems(labels) { _, _ -> }
        .setNeutralButton(R.string.lbl_download_subtitles) { _, _ ->
            showLanguagePicker(anchor, api, itemId)
        }
        .setNegativeButton(R.string.lbl_cancel, null)
        .show()
}

private fun FullDetailsFragment.showLanguagePicker(anchor: View, api: ApiClient, itemId: UUID) =
    popupMenu(requireContext(), anchor) {
        LANGUAGES.forEach { (displayName, code) ->
            item(displayName) { searchSubtitles(api, itemId, code) }
        }
    }.show()

private fun FullDetailsFragment.searchSubtitles(api: ApiClient, itemId: UUID, language: String) {
    lifecycleScope.launch {
        val results = withContext(Dispatchers.IO) {
            runCatching { api.subtitleApi.searchRemoteSubtitles(itemId, language).content }
                .getOrElse { error ->
                    Timber.e(error, "Failed to search subtitles for $itemId language=$language")
                    null
                }
        }

        if (results == null) {
            Toast.makeText(requireContext(), R.string.subtitle_search_failed, Toast.LENGTH_SHORT).show()
            return@launch
        }

        if (results.isEmpty()) {
            Toast.makeText(requireContext(), R.string.subtitle_no_results, Toast.LENGTH_SHORT).show()
            return@launch
        }

        showSubtitleResults(api, itemId, results)
    }
}

private fun FullDetailsFragment.showSubtitleResults(
    api: ApiClient,
    itemId: UUID,
    results: List<RemoteSubtitleInfo>,
) {
    // Hash matches first, then sorted by download count descending
    val sorted = results.sortedWith(
        compareByDescending<RemoteSubtitleInfo> { it.isHashMatch == true }
            .thenByDescending { it.downloadCount ?: 0 }
    )

    val adapter = object : ArrayAdapter<RemoteSubtitleInfo>(
        requireContext(),
        android.R.layout.simple_list_item_2,
        android.R.id.text1,
        sorted,
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val sub = getItem(position)!!

            view.findViewById<TextView>(android.R.id.text1).text = buildString {
                if (sub.isHashMatch == true) append("✓ Perfect Match  ")
                sub.threeLetterIsoLanguageName?.uppercase()?.let { append(it) }
                sub.format?.let { append(" · ${it.uppercase()}") }
                if (sub.hearingImpaired == true) append(" · SDH")
                if (sub.forced == true) append(" · Forced")
                if (sub.aiTranslated == true || sub.machineTranslated == true) append(" · AI")
            }

            view.findViewById<TextView>(android.R.id.text2).text = buildString {
                val name = sub.name?.take(60) ?: ""
                append(name)
                sub.downloadCount?.let {
                    if (name.isNotEmpty()) append("   ")
                    append("$it downloads")
                }
            }

            return view
        }
    }

    AlertDialog.Builder(requireContext())
        .setTitle(R.string.subtitle_select)
        .setAdapter(adapter) { _, index ->
            val sub = sorted[index]
            sub.id?.let { downloadSubtitle(api, itemId, it) }
        }
        .setNegativeButton(R.string.lbl_cancel, null)
        .show()
}

private fun FullDetailsFragment.downloadSubtitle(api: ApiClient, itemId: UUID, subtitleId: String) {
    lifecycleScope.launch {
        val success = withContext(Dispatchers.IO) {
            runCatching { api.subtitleApi.downloadRemoteSubtitles(itemId, subtitleId) }
                .onFailure { Timber.e(it, "Failed to download subtitle $subtitleId for $itemId") }
                .isSuccess
        }

        val message = if (success) R.string.subtitle_downloaded else R.string.subtitle_download_failed
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
