package com.example.mymediaplayer.shared

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers

@RunWith(RobolectricTestRunner::class)
class PlaylistPerformanceTest {

    @Test
    fun benchmarkPlaylistReading() = runBlocking {
        val discoveredPlaylists = (1..100).map { PlaylistInfo("content://playlist/$it", "Playlist $it") }

        // Simulating the I/O block that readPlaylist will do in real-life.
        // It's known that readPlaylist uses ContentResolver and reads files line by line,
        // which inherently is a blocking I/O operation.

        fun blockingRead(uri: Uri): List<MediaFileInfo> {
            Thread.sleep(10) // Simulate 10ms I/O per playlist
            return listOf(MediaFileInfo(uri.toString(), uri.lastPathSegment ?: "", 0L))
        }

        val timeSeq = measureTimeMillis {
            val all = mutableListOf<MediaFileInfo>()
            for (playlist in discoveredPlaylists) {
                all += blockingRead(Uri.parse(playlist.uriString))
            }
        }

        val timeAsync = measureTimeMillis {
            val all = discoveredPlaylists.map { playlist ->
                async(Dispatchers.IO) {
                    blockingRead(Uri.parse(playlist.uriString))
                }
            }.awaitAll().flatten()
        }

        println("Sequential took: $timeSeq ms")
        println("Async took: $timeAsync ms")
        assert(timeAsync < timeSeq) { "Async should be faster than sequential" }
    }
}
