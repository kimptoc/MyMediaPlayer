package com.example.mymediaplayer.shared

import org.junit.Test
import kotlin.system.measureTimeMillis

class MediaCacheServiceBenchmarkTest {
    @Test
    fun benchmarkAddFileVsAddAllFiles() {
        val service = MediaCacheService()
        val files = List(50000) { i ->
            MediaFileInfo(
                uriString = "content://media/external/audio/media/$i",
                displayName = "Song $i.mp3",
                sizeBytes = 1000L,
                title = "Song $i",
                artist = "Artist $i",
                album = "Album $i"
            )
        }

        // Warmup
        service.clearCache()
        for (i in 0..10) {
            files.take(100).forEach { service.addFile(it) }
        }
        service.clearCache()

        val time1 = measureTimeMillis {
            files.forEach { service.addFile(it) }
        }
        println("addFile individually (50k items): $time1 ms")

        service.clearCache()

        val time2 = measureTimeMillis {
            service.addAllFiles(files)
        }
        println("addAllFiles (50k items): $time2 ms")
    }
}
