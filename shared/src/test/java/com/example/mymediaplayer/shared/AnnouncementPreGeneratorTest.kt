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

        val result = preGen.getReadyAudio(track, true)

        assertNull(result)
    }

    @Test
    fun cancelAll_cancelsIncompleteDeferreds() = runTest {
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

        preGen.cancelAll()

        org.junit.Assert.assertTrue(cache.isEmpty())
        org.junit.Assert.assertTrue(deferred.isCancelled)
    }

    @Test
    fun cancelAll_deletesCompletedFiles() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        val cacheField = AnnouncementPreGenerator::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(preGen) as ConcurrentHashMap<Any, Deferred<File?>>

        val cacheKeyClass = Class.forName("com.example.mymediaplayer.shared.AnnouncementPreGenerator\$CacheKey")
        val constructor = cacheKeyClass.getDeclaredConstructor(String::class.java, Boolean::class.java)
        constructor.isAccessible = true
        val key = constructor.newInstance("test_uri2", true)

        val file = File.createTempFile("test", ".mp3").apply { setReadable(false, false); setWritable(false, false); setExecutable(false, false); setReadable(true, true); setWritable(true, true); }
        org.junit.Assert.assertTrue(file.exists())

        val deferred = CompletableDeferred<File?>()
        deferred.complete(file)
        cache[key] = deferred

        preGen.cancelAll()

        org.junit.Assert.assertTrue(cache.isEmpty())
        org.junit.Assert.assertFalse(file.exists())
    }
}
