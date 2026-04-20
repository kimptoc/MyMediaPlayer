package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementPreGeneratorTest {

    @Test
    fun getReadyAudio_timeout_returnsNull() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        val cacheField = AnnouncementPreGenerator::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(preGen) as ConcurrentHashMap<Any, Deferred<File?>>

        val cacheKeyClass = Class.forName("com.example.mymediaplayer.shared.AnnouncementPreGenerator\$CacheKey")
        val constructor = cacheKeyClass.getDeclaredConstructor(String::class.java, Boolean::class.java)
        constructor.isAccessible = true
        val key = constructor.newInstance("test_uri", true)

        val deferred = CompletableDeferred<File?>()
        cache[key] = deferred

        val track = MediaFileInfo(
            uriString = "test_uri",
            displayName = "Test Title",
            title = "Test Title",
            artist = "Test Artist",
            album = null,
            durationMs = 10000,
            sizeBytes = 1000L
        )

        var result: File? = File("dummy")
        val job = launch {
            result = preGen.getReadyAudio(track, true)
        }

        testScheduler.advanceTimeBy(5001L)

        job.join()

        assertNull(result)
    }
}
