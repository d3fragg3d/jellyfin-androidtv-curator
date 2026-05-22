package org.jellyfin.androidtv.ui.playback

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

private val SUBTITLE_LANGUAGES = linkedMapOf(
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

fun PlaybackController.showSubtitleDownloader(context: Context, anchor: View) {
    val api by fragment.inject<ApiClient>()
    val itemId = getCurrentlyPlayingItem()?.id ?: return

    PopupMenu(context, anchor, Gravity.END).apply {
        SUBTITLE_LANGUAGES.forEach { (displayName, code) ->
            menu.add(displayName).setOnMenuItemClickListener {
                searchSubtitles(api, context, itemId, code)
                true
            }
        }
        show()
    }
}

private fun PlaybackController.searchSubtitles(
    api: ApiClient,
    context: Context,
    itemId: UUID,
    language: String,
) {
    fragment.lifecycleScope.launch {
        val results = withContext(Dispatchers.IO) {
            runCatching { api.subtitleApi.searchRemoteSubtitles(itemId, language).content }
                .getOrElse { error ->
                    Timber.e(error, "Failed to search subtitles for $itemId language=$language")
                    null
                }
        }

        if (results == null) {
            Toast.makeText(context, R.string.subtitle_search_failed, Toast.LENGTH_SHORT).show()
            return@launch
        }

        if (results.isEmpty()) {
            Toast.makeText(context, R.string.subtitle_no_results, Toast.LENGTH_SHORT).show()
            return@launch
        }

        showSubtitleResults(api, context, itemId, results)
    }
}

private fun PlaybackController.showSubtitleResults(
    api: ApiClient,
    context: Context,
    itemId: UUID,
    results: List<RemoteSubtitleInfo>,
) {
    val sorted = results.sortedWith(
        compareByDescending<RemoteSubtitleInfo> { it.isHashMatch == true }
            .thenByDescending { it.downloadCount ?: 0 }
    )

    val adapter = object : ArrayAdapter<RemoteSubtitleInfo>(
        context,
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

    AlertDialog.Builder(context)
        .setTitle(R.string.subtitle_select)
        .setAdapter(adapter) { _, index ->
            val sub = sorted[index]
            sub.id?.let { downloadSubtitle(api, context, itemId, it) }
        }
        .setNegativeButton(R.string.lbl_cancel, null)
        .show()
}

private fun PlaybackController.downloadSubtitle(
    api: ApiClient,
    context: Context,
    itemId: UUID,
    subtitleId: String,
) {
    fragment.lifecycleScope.launch {
        val success = withContext(Dispatchers.IO) {
            runCatching { api.subtitleApi.downloadRemoteSubtitles(itemId, subtitleId) }
                .onFailure { Timber.e(it, "Failed to download subtitle $subtitleId for $itemId") }
                .isSuccess
        }

        if (success) {
            val position = mCurrentPosition
            stop()
            play(position, -1)
            Toast.makeText(context, R.string.subtitle_downloaded_select, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, R.string.subtitle_download_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
