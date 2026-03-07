package com.example.mymediaplayer.shared

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class MyMusicServiceBenchmarkTest {

    @Test
    fun benchmarkEnrichFromCache() {
        val service = MyMusicService()

        // Use reflection to access private MediaCacheService
        val mediaCacheServiceField = MyMusicService::class.java.getDeclaredField("mediaCacheService")
        mediaCacheServiceField.isAccessible = true
        val mediaCacheService = mediaCacheServiceField.get(service) as MediaCacheService

        // Populate cache with many files
        val cachedFilesCount = 10000
        for (i in 0 until cachedFilesCount) {
            mediaCacheService.addFile(
                MediaFileInfo(
                    uriString = "content://test/song" + i,
                    displayName = "Song " + i,
                    sizeBytes = 1000L,
                    title = "Song " + i
                )
            )
        }

        // Create a list to enrich
        val playlistSize = 1000
        val playlistFiles = (0 until playlistSize).map { i ->
            MediaFileInfo(
                uriString = "content://test/song" + i,
                displayName = "Song " + i,
                sizeBytes = 0L,
                title = "Song " + i
            )
        }

        // Use reflection to call private enrichFromCache method
        val enrichFromCacheMethod = MyMusicService::class.java.getDeclaredMethod("enrichFromCache", List::class.java)
        enrichFromCacheMethod.isAccessible = true

        // Warm up
        for (i in 0 until 10) {
            enrichFromCacheMethod.invoke(service, playlistFiles)
        }

        // Benchmark
        val iterations = 100
        val timeMs = measureTimeMillis {
            for (i in 0 until iterations) {
                enrichFromCacheMethod.invoke(service, playlistFiles)
            }
        }

        val output = "Enrichment of " + playlistSize + " items against cache of " + cachedFilesCount + " took " + (timeMs / iterations.toDouble()) + " ms on average\n"
        println(output)

        assertTrue("Benchmark completed", true)
    }
}
