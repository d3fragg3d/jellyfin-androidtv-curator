package org.jellyfin.androidtv.ui.browsing

// Must match the Curator plugin's kidsUsername — the server compares case-insensitively, so we do too
private const val KIDS_USERNAME = "Kids"

fun isKidsUser(name: String?) = name.equals(KIDS_USERNAME, ignoreCase = true)
