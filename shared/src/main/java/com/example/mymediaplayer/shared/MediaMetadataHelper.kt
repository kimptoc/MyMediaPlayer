package com.example.mymediaplayer.shared

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache

object MediaMetadataHelper {

    private const val TAG = "MediaMetadataHelper"
    private val metadataCache = LruCache<String, MediaMetadataInfo>(1024)

    fun extractMetadata(context: Context, uriString: String): MediaMetadataInfo? {
        metadataCache.get(uriString)?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriString))
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            Log.d(TAG, "extractMetadata uri=$uriString title=$title artist=$artist album=$album genre=$genre")
            if (title == null && artist == null && album == null) {
                Log.w(TAG, "No ID3 tags found for: $uriString")
            }
            val info = MediaMetadataInfo(
                title = title,
                album = album,
                artist = artist,
                genre = genre,
                year = year,
                durationMs = durationMs
            )
            metadataCache.put(uriString, info)
            info
        } catch (e: Exception) {
            Log.e(TAG, "extractMetadata FAILED for $uriString: ${e.javaClass.simpleName}: ${e.message}")
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
