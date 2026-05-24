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
import org.robolectric.annotation.Config
import org.junit.After
import org.junit.Before
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [33])
class AnnouncementPreGeneratorTest {

    @Before
    fun setup() {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            if (NetworkQualityCheckerTest.mockFailConnection) throw java.io.IOException("Mock connection failed")
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }
    }

    @After
    fun teardown() {
        NetworkQualityCheckerTest.mockFailConnection = false
        AnnouncementPreGenerator.testInterceptor = null
    }

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

    @Test
    fun getReadyAudio_fetchKiloTextException_returnsNull() = runTest {
        NetworkQualityCheckerTest.mockFailConnection = true

        try {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("pref_kilo_key", "fake_kilo_key")
                .putString("pref_cloud_tts_key", "fake_tts_key")
                .apply()

            val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

            val track = MediaFileInfo(
                uriString = "test_uri_exception",
                displayName = "Exception Title",
                title = "Exception Title",
                artist = "Exception Artist",
                album = null,
                durationMs = 10000,
                sizeBytes = 1000L
            )

            var result: File? = File("dummy")
            val job = launch {
                result = preGen.getReadyAudio(track, true)
            }

            testScheduler.advanceUntilIdle()
            job.join()

            assertNull("Audio should be null due to simulated network exception", result)
        } finally {
            NetworkQualityCheckerTest.mockFailConnection = false
        }
    }

    @Test
    fun fetchKiloText_exception_returnsNull() = runTest {
        NetworkQualityCheckerTest.installMockFactory()
        NetworkQualityCheckerTest.mockFailConnection = true

        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

            var result: String? = "dummy"
            val job = launch {
                result = preGen.fetchKiloText(
                    title = "Test Title",
                    artist = "Test Artist",
                    isIntro = true,
                    apiKey = "fake_kilo_key"
                )
            }

            testScheduler.advanceUntilIdle()
            job.join()

            assertNull("Text should be null due to simulated network exception", result)
        } finally {
            NetworkQualityCheckerTest.mockFailConnection = false
        }
    }

}
