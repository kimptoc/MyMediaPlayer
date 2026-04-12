package com.example.mymediaplayer.shared

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class SmartPlaylistBenchmarkTest {

    @Test
    fun benchmarkSmartPlaylistMostPlayed() {
        val service = MyMusicService()
        val context = RuntimeEnvironment.getApplication()

        // Setup preferences with play counts
        val prefs = context.getSharedPreferences("mymediaplayer_prefs", Context.MODE_PRIVATE)
        val playCountsBuilder = StringBuilder()
        for (i in 0 until 50000) {
            if (i % 2 == 0) {
                playCountsBuilder.append("content://test/song$i\t${i % 100}\n")
            }
        }
        prefs.edit().putString("play_counts", playCountsBuilder.toString()).apply()

        val files = mutableListOf<MediaFileInfo>()
        for (i in 0 until 50000) {
            files.add(
                MediaFileInfo(
                    uriString = "content://test/song$i",
                    displayName = "Song $i",
                    sizeBytes = 1000L,
                    title = "Song $i"
                )
            )
        }

        service.mediaCacheService.clearFiles()
        service.mediaCacheService.addAllFiles(files)

        // To access getSharedPreferences in MyMusicService we need a Context.
        // We'll mock the internal behavior of the method resolveSmartPlaylistTracksById which depends on getSharedPreferences,
        // wait, we can't easily mock `getSharedPreferences` on `MyMusicService` itself since it's an Activity/Service
        // Robolectric usually provides the context, but MyMusicService is not explicitly started via Robolectric.buildService

        // Wait, resolveSmartPlaylistTracksById calls getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // Let's attach the application context to the service
        val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContext.isAccessible = true
        attachBaseContext.invoke(service, context)

        val method = MyMusicService::class.java.getDeclaredMethod("resolveSmartPlaylistTracksById", String::class.java)
        method.isAccessible = true

        // Warm up
        for (i in 0 until 10) {
            method.invoke(service, "most_played")
        }

        // Benchmark
        val iterations = 50
        val timeMs = measureTimeMillis {
            for (i in 0 until iterations) {
                method.invoke(service, "most_played")
            }
        }

        val output = "resolveSmartPlaylistTracksById(most_played) of 50000 items took ${timeMs / iterations.toDouble()} ms on average\n"
        println(output)

        assertTrue("Benchmark completed", true)
    }
}
