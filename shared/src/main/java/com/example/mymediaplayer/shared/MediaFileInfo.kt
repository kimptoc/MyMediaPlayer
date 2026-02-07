package com.example.mymediaplayer.shared

data class MediaFileInfo(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val year: Int? = null
)
