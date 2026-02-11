package com.example.mymediaplayer.shared

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat

internal fun buildSongListItems(
    songs: List<MediaFileInfo>,
    listKey: String,
    defaultIconUri: Uri? = null
): MutableList<MediaItem> {
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
    items += buildSongItems(songs, defaultIconUri)
    return items
}

internal fun buildSongItems(
    songs: List<MediaFileInfo>,
    defaultIconUri: Uri? = null
): List<MediaItem> {
    return songs.map { fileInfo ->
        val builder = MediaDescriptionCompat.Builder()
            .setMediaId(fileInfo.uriString)
            .setTitle(fileInfo.title ?: fileInfo.displayName)
            .setSubtitle(fileInfo.artist)
        if (defaultIconUri != null) {
            builder.setIconUri(defaultIconUri)
        }
        MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE)
    }
}

internal fun buildCategoryListItems(
    categories: List<String>,
    prefix: String,
    counts: Map<String, Int>? = null,
    iconUri: Uri? = null
): MutableList<MediaItem> {
    return categories.map { category ->
        val label = counts?.get(category)?.let { "$category ($it)" } ?: category
        val builder = MediaDescriptionCompat.Builder()
            .setMediaId(prefix + Uri.encode(category))
            .setTitle(label)
        if (iconUri != null) {
            builder.setIconUri(iconUri)
        }
        MediaItem(builder.build(), MediaItem.FLAG_BROWSABLE)
    }.toMutableList()
}
