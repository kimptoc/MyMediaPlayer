package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private var capturedBody: String? = null

    @Before
    fun setup() {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            capturedAuthHeader = chain.request().header("Authorization")
            capturedBody = chain.request().body?.let { b -> okio.Buffer().also { b.writeTo(it) }.readUtf8() }
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

    @Test
    fun fetchKiloText_keepsTheDotsInAJwtKey() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2lnbmF0dXJlLXBhcnQ_-x"

        preGen.fetchKiloText(title = "Test Title", artist = "Test Artist", isIntro = true, apiKey = " $jwt\n")

        assertEquals("Bearer $jwt", capturedAuthHeader)
    }

    @Test
    fun fetchKiloText_withKey_usesTheEfficientAutoModel() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        preGen.fetchKiloText(title = "Test Title", artist = "Test Artist", isIntro = true, apiKey = "key")

        assertEquals("kilo-auto/efficient", org.json.JSONObject(capturedBody!!).getString("model"))
    }

    @Test
    fun fetchKiloText_withoutKey_usesTheFreeAutoModelAnonymously() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        preGen.fetchKiloText(title = "Test Title", artist = "Test Artist", isIntro = true, apiKey = null)

        assertEquals("Bearer anonymous", capturedAuthHeader)
        assertEquals("kilo-auto/free", org.json.JSONObject(capturedBody!!).getString("model"))
    }

    @Test
    fun fetchKiloText_leavesRoomForTheLineAfterReasoning() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        preGen.fetchKiloText(title = "Test Title", artist = "Test Artist", isIntro = true, apiKey = null)

        // The free model used 100-150 tokens reasoning; at 150 the line came back empty.
        assertEquals(500, org.json.JSONObject(capturedBody!!).getInt("max_tokens"))
    }

    @Test
    fun fetchKiloText_promptAllowsUpTo16Words() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preGen = AnnouncementPreGenerator(context, TestScope(this.testScheduler))

        preGen.fetchKiloText(title = "Test Title", artist = "Test Artist", isIntro = false, apiKey = null)

        val prompt = org.json.JSONObject(capturedBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(prompt, prompt.contains("Up to 16 words"))
    }
}
