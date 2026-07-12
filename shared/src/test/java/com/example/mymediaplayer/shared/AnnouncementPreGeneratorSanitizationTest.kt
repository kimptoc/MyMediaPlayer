package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.After
import org.junit.Before
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [33])
class AnnouncementPreGeneratorSanitizationTest {

    private var capturedAuthHeader: String? = null

    @Before
    fun setup() {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            capturedAuthHeader = chain.request().header("Authorization")
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{\"choices\":[{\"message\":{\"content\":\"Mock Kilo Intro\"}}]}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }
    }

    @After
    fun teardown() {
        AnnouncementPreGenerator.testInterceptor = null
        capturedAuthHeader = null
    }

    @Test
    fun fetchKiloText_sanitizesApiKey() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        val dirtyKey = "sk_test_123\n\r<script>alert(1)</script>; DROP TABLE;"
        preGen.fetchKiloText(
            title = "Test Title",
            artist = "Test Artist",
            isIntro = true,
            apiKey = dirtyKey
        )

        assertEquals("Bearer sk_test_123scriptalert1/scriptDROPTABLE", capturedAuthHeader)
    }
}
