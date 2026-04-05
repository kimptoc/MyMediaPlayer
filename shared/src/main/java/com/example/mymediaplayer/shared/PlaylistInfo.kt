package com.example.mymediaplayer.shared

data class PlaylistInfo(
    val uriString: String,
    val displayName: String
)

/** Deterministic short hash for use in mediaIds (avoids long content:// URIs). */
fun playlistShortId(uriString: String): String =
    uriString.hashCode().toUInt().toString(36)
