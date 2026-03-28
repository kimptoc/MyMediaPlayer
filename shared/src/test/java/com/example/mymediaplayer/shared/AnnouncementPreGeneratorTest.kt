package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnouncementPreGeneratorTest {

    private lateinit var context: Context
    private lateinit var preGenerator: AnnouncementPreGenerator
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preGenerator = AnnouncementPreGenerator(context, testScope)
    }

    @Test
    fun getReadyAudio_timeout_returnsNull() = testScope.runTest {
        val track = MediaFileInfo(
            uriString = "content://media/external/audio/media/1",
            displayName = "test.mp3",
            sizeBytes = 1000L,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 3000L
        )

        // Use reflection to access the cache and CacheKey constructor
        val cacheField = AnnouncementPreGenerator::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(preGenerator) as ConcurrentHashMap<Any, Any>

        val cacheKeyClass = Class.forName("com.example.mymediaplayer.shared.AnnouncementPreGenerator\$CacheKey")
        val cacheKeyConstructor = cacheKeyClass.getDeclaredConstructors().first()
        cacheKeyConstructor.isAccessible = true
        val key = cacheKeyConstructor.newInstance(track.uriString, true)

        val deferred = async {
            delay(10_000L) // Longer than READY_TIMEOUT_MS
            File("test.mp3")
        }

        cache[key] = deferred

        // Assert timeout happens and null is returned
        val result = preGenerator.getReadyAudio(track, true)

        // Timeout is 5000ms, the coroutine framework using runTest handles time virtually.
        assertNull("Expected null due to timeout", result)

        deferred.cancel()
    }
}
