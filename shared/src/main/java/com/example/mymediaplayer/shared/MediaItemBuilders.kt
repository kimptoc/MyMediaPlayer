package com.example.mymediaplayer.shared

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat

internal fun buildSongListItems(songs: List<MediaFileInfo>, listKey: String): MutableList<MediaItem> {
    val items = mutableListOf<MediaItem>()
    if (songs.isNotEmpty()) {
        items.add(
            MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("action:play_all:" + listKey)
                    .setTitle("[Play All]")
                    .build(),
                MediaItem.FLAG_PLAYABLE
            )
        )
        items.add(
            MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("action:shuffle:" + listKey)
                    .setTitle("[Shuffle]")
                    .build(),
                MediaItem.FLAG_PLAYABLE
            )
        )
    }
    items += songs.map { fileInfo ->
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(fileInfo.uriString)
            .setTitle(fileInfo.title ?: fileInfo.displayName)
            .setSubtitle(fileInfo.artist)
            .build()
        MediaItem(description, MediaItem.FLAG_PLAYABLE)
    }
    return items
}

internal fun buildCategoryListItems(
    categories: List<String>,
    prefix: String,
    counts: Map<String, Int>? = null
): MutableList<MediaItem> {
    return categories.map { category ->
        val label = counts?.get(category)?.let { "$category ($it)" } ?: category
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(prefix + Uri.encode(category))
            .setTitle(label)
            .build()
        MediaItem(description, MediaItem.FLAG_BROWSABLE)
    }.toMutableList()
}
