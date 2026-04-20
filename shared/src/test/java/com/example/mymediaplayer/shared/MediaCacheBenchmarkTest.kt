package com.example.mymediaplayer.shared

import org.junit.Test
import kotlin.system.measureTimeMillis

class MediaCacheBenchmarkTest {

    @Test
    fun benchmarkAlbumsByLatestAddedDesc() {
        val service = MediaCacheService()
        // Create 1000 albums, each with 10 files
        val files = mutableListOf<MediaFileInfo>()
        for (i in 1..1000) {
            for (j in 1..10) {
                files.add(
                    MediaFileInfo(
                        uriString = "uri-$i-$j",
                        displayName = "file-$i-$j",
                        sizeBytes = 100L,
                        title = "Title $i-$j",
                        artist = "Artist $i",
                        album = "Album $i",
                        genre = "Genre $i",
                        durationMs = 1000L,
                        year = 2000 + (i % 20),
                        addedAtMs = 1000000L + i * 1000 + j
                    )
                )
            }
        }
        service.addAllFiles(files)
        service.buildAlbumArtistIndexesFromCache()

        val time1 = measureTimeMillis {
            for (i in 1..100) {
                service.albumsByLatestAddedDesc()
            }
        }

        println("BENCHMARK_RESULT_MS: $time1")
    }
}
