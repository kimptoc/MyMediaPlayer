package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The announcement line comes from Gemini when there is a Gemini key, then Kilo, then a stock phrase. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnnouncementPreGeneratorLineTest {

    private val requestedUrls = mutableListOf<String>()

    @After
    fun teardown() {
        AnnouncementPreGenerator.testInterceptor = null
    }

    @Test
    fun withGeminiKey_geminiWritesTheLine_andKiloIsNotAsked() = runTest {
        respond(gemini = 200 to GEMINI_LINE, kilo = 200 to KILO_LINE)

        val line = generator(this.testScheduler).fetchLine("Song", "Artist", isIntro = true, geminiKey = "g", kiloKey = "k")

        assertEquals("Here's Song by Artist" to "Gemini", line)
        assertEquals(1, requestedUrls.size)
        assertTrue(requestedUrls[0], requestedUrls[0].contains("models/${TtsModels.GEMINI_TEXT_MODEL}:generateContent"))
    }

    @Test
    fun geminiFailing_fallsBackToKilo() = runTest {
        respond(gemini = 429 to "{}", kilo = 200 to KILO_LINE)

        val line = generator(this.testScheduler).fetchLine("Song", "Artist", isIntro = true, geminiKey = "g", kiloKey = "k")

        assertEquals("Kilo says Song by Artist" to "Kilo", line)
    }

    @Test
    fun withoutGeminiKey_kiloWritesTheLine() = runTest {
        respond(gemini = 200 to GEMINI_LINE, kilo = 200 to KILO_LINE)

        val line = generator(this.testScheduler).fetchLine("Song", "Artist", isIntro = true, geminiKey = null, kiloKey = "k")

        assertEquals("Kilo says Song by Artist" to "Kilo", line)
        assertTrue(requestedUrls.none { it.contains("generativelanguage") })
    }

    @Test
    fun bothFailing_usesAStockPhrase() = runTest {
        respond(gemini = 500 to "{}", kilo = 401 to "{}")

        val (text, source) = generator(this.testScheduler).fetchLine("Song", "Artist", isIntro = false, geminiKey = "g", kiloKey = "k")

        assertEquals("stock phrase", source)
        assertTrue(text, text.contains("Song") && text.contains("Artist"))
    }

    private fun respond(gemini: Pair<Int, String>, kilo: Pair<Int, String>) {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            val url = chain.request().url.toString()
            requestedUrls += url
            val (code, body) = if (url.contains("generativelanguage")) gemini else kilo
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }
    }

    private fun generator(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        AnnouncementPreGenerator(ApplicationProvider.getApplicationContext<Context>(), TestScope(scheduler))

    private companion object {
        const val GEMINI_LINE = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\\\"Here's Song by Artist\\\"\\n\"}]}}]}"
        const val KILO_LINE = "{\"choices\":[{\"message\":{\"content\":\"Kilo says Song by Artist\"}}]}"
    }
}
