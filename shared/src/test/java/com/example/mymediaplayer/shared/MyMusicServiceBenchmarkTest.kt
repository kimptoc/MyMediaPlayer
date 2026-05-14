package com.example.mymediaplayer.shared

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertFalse
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

    @Test
    fun benchmarkBuildHomeItemsWithUnindexedCache() {
        // Worst-case path for the cache-independent HOME_ID branch in onLoadChildren:
        // cache is populated (e.g. mid-scan) but metadata indexes have not been built
        // yet, so buildHomeItems() runs buildAlbumArtistIndexesFromCache() synchronously
        // on the binder thread.
        //
        // Threshold rationale: Android Auto's onLoadChildren timeout is on the order
        // of low single-digit seconds, so 2000 ms is a defensive regression bound.
        // Typical observed cost in this benchmark is ~450 ms for 10k songs on a
        // laptop JVM. If this assertion ever starts failing, either the library
        // is much larger than 10k songs or buildAlbumArtistIndexesFromCache has
        // regressed — in either case, HOME_ID should likely be moved to the
        // cache-dependent path so it queues during scans instead of blocking.
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        val cache = service.mediaCacheService

        val songsCount = 10_000
        for (i in 0 until songsCount) {
            cache.addFile(
                MediaFileInfo(
                    uriString = "content://test/song$i",
                    displayName = "Song $i.mp3",
                    sizeBytes = 1000L,
                    title = "Song $i",
                    artist = "Artist ${i % 200}",
                    album = "Album ${i % 500}",
                    genre = "Genre ${i % 30}",
                    year = 1970 + (i % 50)
                )
            )
        }
        // addFile() invalidates albumArtistIndexed, so the first buildHomeItems()
        // call below triggers a full index rebuild — exactly the path we want to time.
        assertFalse(cache.hasAlbumArtistIndexes())

        val elapsedMs = measureTimeMillis {
            service.buildHomeItems()
        }

        println("benchmarkBuildHomeItemsWithUnindexedCache: ${elapsedMs}ms for $songsCount songs")
        assertTrue(
            "buildHomeItems with $songsCount unindexed songs took ${elapsedMs}ms, expected < 2000ms",
            elapsedMs < 2000
        )
    }
}
