package com.example.mymediaplayer.shared

data class MediaFileInfo(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val addedAtMs: Long? = null
) {
    val cleanTitle: String
        get() {
            val raw = title?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.')
            return raw.replaceFirst(Regex("""\.(mp3|m4a|flac|wav|ogg|aac|wma|opus)$""", RegexOption.IGNORE_CASE), "")
        }
}
