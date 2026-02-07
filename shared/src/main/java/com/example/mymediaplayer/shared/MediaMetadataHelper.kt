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
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
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
    val album: String?,
    val artist: String?,
    val genre: String?
)
