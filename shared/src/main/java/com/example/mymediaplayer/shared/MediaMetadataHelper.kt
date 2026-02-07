package com.example.mymediaplayer.shared

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

object MediaMetadataHelper {

    fun extractMetadata(context: Context, uriString: String): MediaMetadataInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriString))
            MediaMetadataInfo(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}

data class MediaMetadataInfo(
    val title: String?,
    val album: String?,
    val artist: String?,
    val genre: String?,
    val year: String?,
    val durationMs: String?
)
