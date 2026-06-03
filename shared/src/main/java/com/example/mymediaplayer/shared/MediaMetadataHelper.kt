package com.example.mymediaplayer.shared

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object MediaMetadataHelper {

    private const val TAG = "MediaMetadataHelper"
    private val metadataCache = LruCache<String, MediaMetadataInfo>(1024)
    private const val METADATA_EXTRACTION_TIMEOUT_MS = 5000L
    @Volatile
    private var executor: ExecutorService? = null

    private fun getExecutor(): ExecutorService {
        var e = executor
        if (e == null || e.isShutdown) {
            synchronized(this) {
                e = executor
                if (e == null || e.isShutdown) {
                    e = Executors.newSingleThreadExecutor()
                    executor = e
                }
            }
        }
        return e
    }

    fun extractMetadata(context: Context, uriString: String): MediaMetadataInfo? {
        metadataCache.get(uriString)?.let { return it }
        return try {
            val future = getExecutor().submit<MediaMetadataInfo?> {
                doExtractMetadata(context, uriString)
            }
            future.get(METADATA_EXTRACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "Metadata extraction timed out for $uriString (file may be in use by playback)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read metadata: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun doExtractMetadata(context: Context, uriString: String): MediaMetadataInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriString))
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            Log.d(TAG, "extractMetadata completed")
            if (title == null && artist == null && album == null) {
                Log.w(TAG, "No ID3 tags found for media")
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
            Log.w(TAG, "Failed to read metadata: ${e.javaClass.simpleName}")
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
