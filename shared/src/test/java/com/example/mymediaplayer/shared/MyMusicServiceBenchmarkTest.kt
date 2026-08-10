package com.example.mymediaplayer.shared

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class MyMusicServiceBenchmarkTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences.throwGeneralSecurityException = false
        EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences.throwIOException = false
        EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences.throwException = false
        EncryptedPrefsManagerTest.ShadowMasterKeyBuilder.throwGeneralSecurityException = false
        EncryptedPrefsManagerTest.ShadowMasterKeyBuilder.throwIOException = false
        EncryptedPrefsManagerTest.ShadowMasterKeyBuilder.throwException = false
    }

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

    @Test
    @org.robolectric.annotation.Config(sdk = [34], shadows = [EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences::class, EncryptedPrefsManagerTest.ShadowMasterKeyBuilder::class])
    fun benchmarkPrefsMigration() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        val context = service.applicationContext
        val standardPrefs = context.getSharedPreferences("mymediaplayer_prefs", android.content.Context.MODE_PRIVATE)

        // 1. Measure empty migration
        val emptyTime = measureTimeMillis {
            for (i in 0 until 10) {
                // Reset states
                standardPrefs.edit().clear().commit()
                val standardPrefsFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/mymediaplayer_prefs.xml")
                standardPrefsFile.parentFile?.mkdirs()
                standardPrefsFile.createNewFile()
                EncryptedPrefsManager.clearCacheForTesting()
                MyMusicService.clearPrefsCacheForTesting()

                MyMusicService.getPrefs(context)
            }
        }
        val avgEmptyMs = emptyTime / 10.0
        println("Benchmark: Empty prefs migration took average $avgEmptyMs ms per iteration")

        // 2. Measure populated migration
        val populatedTime = measureTimeMillis {
            for (i in 0 until 10) {
                // Reset states and seed keys
                standardPrefs.edit().clear().commit()
                val editor = standardPrefs.edit()
                for (j in 0 until 20) {
                    editor.putString("key_$j", "value_$j")
                }
                editor.commit()

                val standardPrefsFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/mymediaplayer_prefs.xml")
                standardPrefsFile.parentFile?.mkdirs()
                standardPrefsFile.createNewFile()
                EncryptedPrefsManager.clearCacheForTesting()
                MyMusicService.clearPrefsCacheForTesting()

                MyMusicService.getPrefs(context)
            }
        }
        val avgPopulatedMs = populatedTime / 10.0
        println("Benchmark: Populated prefs migration (20 keys) took average $avgPopulatedMs ms per iteration")

        // Defensive regression bound, not a tight perf target: observed averages on a
        // laptop JVM are single-digit ms for both cases, so 500ms leaves generous
        // headroom for CI runner variance while still catching an actual regression
        // (e.g. the migration loop re-introducing a per-key disk commit).
        assertTrue(
            "Empty prefs migration took ${avgEmptyMs}ms on average, expected < 500ms",
            avgEmptyMs < 500
        )
        assertTrue(
            "Populated prefs migration (20 keys) took ${avgPopulatedMs}ms on average, expected < 500ms",
            avgPopulatedMs < 500
        )
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [33])
    fun benchmarkHandleSetMediaFiles() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()

        // Use reflection to get the callback object
        val callbackField = MyMusicService::class.java.getDeclaredField("callback")
        callbackField.isAccessible = true
        val callbackObj = callbackField.get(service)

        // Find the handleSetMediaFiles method
        println("Declared methods on callback class:")
        callbackObj.javaClass.declaredMethods.forEach { method ->
            println("  ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
        }
        val handleSetMediaFilesMethod = callbackObj.javaClass.declaredMethods.find { it.name.startsWith("handleSetMediaFiles") }
            ?: throw IllegalStateException("Could not find handleSetMediaFiles method")
        handleSetMediaFilesMethod.isAccessible = true

        val count = 2000
        val bundle = android.os.Bundle().apply {
            putStringArrayList("uris", ArrayList((0 until count).map { "content://test/song$it" }))
            putStringArrayList("names", ArrayList((0 until count).map { "Song $it.mp3" }))
            putLongArray("sizes", LongArray(count) { 1000L })
            putStringArrayList("titles", ArrayList((0 until count).map { "Song $it" }))
            putStringArrayList("artists", ArrayList((0 until count).map { "Artist $it" }))
            putStringArrayList("albums", ArrayList((0 until count).map { "Album $it" }))
            putStringArrayList("genres", ArrayList((0 until count).map { "Genre $it" }))
            putLongArray("durations", LongArray(count) { 200_000L })
            putIntArray("years", IntArray(count) { 2024 })
            putLongArray("added_at", LongArray(count) { 1700000000L })
        }

        // Warm up
        for (i in 0 until 5) {
            handleSetMediaFilesMethod.invoke(callbackObj, bundle)
        }

        // Benchmark
        val iterations = 50
        val timeMs = measureTimeMillis {
            for (i in 0 until iterations) {
                handleSetMediaFilesMethod.invoke(callbackObj, bundle)
            }
        }

        val avgMs = timeMs / iterations.toDouble()
        println("benchmarkHandleSetMediaFiles: $count items took average $avgMs ms per iteration")
        assertTrue(
            "handleSetMediaFiles with $count items took ${avgMs}ms per iteration, expected < 100ms",
            avgMs < 100.0
        )
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [33])
    fun handleSetMediaFiles_appliesFallbackRulesCorrectly() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()

        val callbackField = MyMusicService::class.java.getDeclaredField("callback")
        callbackField.isAccessible = true
        val callbackObj = callbackField.get(service)
        val handleSetMediaFilesMethod = callbackObj.javaClass.declaredMethods
            .find { it.name.startsWith("handleSetMediaFiles") }
            ?: throw IllegalStateException("Could not find handleSetMediaFiles method")
        handleSetMediaFilesMethod.isAccessible = true

        // Index 0: everything populated normally.
        // Index 1: blank strings -> null (title falls back to filename); negative
        //   numbers -> null.
        // Index 2: whitespace-only title -> fallback; zero duration/addedAt and year=1
        //   are valid values that must be kept, not treated as "missing".
        // Index 3: artists/albums/durations/years/addedAt arrays are shorter than the
        //   file count, so this index is out of range for all of them -> null.
        // genres is omitted from the bundle entirely for every index -> genre always null.
        val bundle = android.os.Bundle().apply {
            putStringArrayList(
                "uris",
                arrayListOf("content://test/0", "content://test/1", "content://test/2", "content://test/3")
            )
            putStringArrayList(
                "names",
                arrayListOf("Song Zero.mp3", "Song One.mp3", "Song Two.mp3", "Song Three.mp3")
            )
            putLongArray("sizes", longArrayOf(100L, 200L, 300L, 400L))
            putStringArrayList(
                "titles",
                arrayListOf("Real Title 0", "", "   ", "Title 3")
            )
            putStringArrayList("artists", arrayListOf("Artist 0", "", "Artist 2"))
            putStringArrayList("albums", arrayListOf("Album 0", "", "Album 2"))
            putLongArray("durations", longArrayOf(5000L, -1L, 0L))
            putIntArray("years", intArrayOf(1999, 0, 1))
            putLongArray("added_at", longArrayOf(1000L, -5L, 0L))
        }

        handleSetMediaFilesMethod.invoke(callbackObj, bundle)

        val byUri = service.mediaCacheService.cachedFiles.associateBy { it.uriString }
        assertEquals(4, byUri.size)

        val f0 = byUri.getValue("content://test/0")
        assertEquals("Real Title 0", f0.title)
        assertEquals("Artist 0", f0.artist)
        assertEquals("Album 0", f0.album)
        assertNull(f0.genre)
        assertEquals(5000L, f0.durationMs)
        assertEquals(1999, f0.year)
        assertEquals(1000L, f0.addedAtMs)

        val f1 = byUri.getValue("content://test/1")
        assertEquals("Song One", f1.title) // blank title -> falls back to filename
        assertNull(f1.artist) // blank -> null
        assertNull(f1.album)
        assertNull(f1.genre)
        assertNull(f1.durationMs) // negative -> null
        assertNull(f1.year) // 0 -> null
        assertNull(f1.addedAtMs) // negative -> null

        val f2 = byUri.getValue("content://test/2")
        assertEquals("Song Two", f2.title) // whitespace-only title -> falls back
        assertEquals("Artist 2", f2.artist)
        assertEquals("Album 2", f2.album)
        assertNull(f2.genre)
        assertEquals(0L, f2.durationMs) // zero is a valid duration, not "missing"
        assertEquals(1, f2.year)
        assertEquals(0L, f2.addedAtMs) // zero is a valid timestamp, not "missing"

        val f3 = byUri.getValue("content://test/3")
        assertEquals("Title 3", f3.title)
        assertNull(f3.artist) // list shorter than file count -> out of range -> null
        assertNull(f3.album)
        assertNull(f3.genre)
        assertNull(f3.durationMs)
        assertNull(f3.year)
        assertNull(f3.addedAtMs)
    }
}
