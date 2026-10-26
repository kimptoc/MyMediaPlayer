package com.example.mymediaplayer.shared

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [33])
class AnnouncementPreGeneratorBackendTest {

    private var lastCloudTtsBody: String? = null

    @Before
    fun setup() {
        lastCloudTtsBody = null
    }

    @After
    fun teardown() {
        AnnouncementPreGenerator.testInterceptor = null
        lastCloudTtsBody = null
    }

    private fun pcmBase64(sizeBytes: Int = 8): String {
        val pcm = ByteArray(sizeBytes) { it.toByte() }
        return Base64.encodeToString(pcm, Base64.NO_WRAP)
    }

    private fun response(
        chain: okhttp3.Interceptor.Chain,
        code: Int,
        body: String,
    ): okhttp3.Response = okhttp3.Response.Builder()
        .request(chain.request())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Error")
        .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
        .build()

    private fun preGen(context: Context, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        AnnouncementPreGenerator(context, TestScope(scheduler))

    @Test
    fun geminiApi_success_returnsWavWithRiffAndWaveHeaders() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(
                chain,
                200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"${pcmBase64()}\"}}]}}]}",
            )
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val result = generator.fetchGeminiApiTtsAudio("hello", "fake_key")

        assertTrue(result is AnnouncementPreGenerator.SynthResult.Success)
        val file = (result as AnnouncementPreGenerator.SynthResult.Success).file
        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertTrue("WAV should contain the 44-byte header plus PCM payload", bytes.size > 44)
    }

    @Test
    fun geminiApi_http429_returnsResourceExhausted() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(chain, 429, "{}")
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val result = generator.fetchGeminiApiTtsAudio("hi", "fake_key")

        assertEquals(AnnouncementPreGenerator.SynthResult.ResourceExhausted, result)
    }

    @Test
    fun geminiApi_http500_returnsFailed() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(chain, 500, "{}")
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val result = generator.fetchGeminiApiTtsAudio("hi", "fake_key")

        assertEquals(AnnouncementPreGenerator.SynthResult.Failed, result)
    }

    @Test
    fun geminiApi_quotaExhausted_fallsBackToCloudGeminiVoice() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            val isGeminiApi = chain.request().url.host.contains("generativelanguage")
            if (isGeminiApi) {
                response(chain, 429, "{}")
            } else {
                lastCloudTtsBody = chain.request().body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                }
                response(chain, 200, "{\"audioContent\":\"${pcmBase64(4)}\"}")
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val file = generator.synthesizeWithGeminiApi("hello", geminiKey = "gemini_key", ttsKey = "tts_key")

        assertNotNull("Fallback should have produced audio via Cloud TTS", file)
        assertTrue(
            "Fallback request should use the Cloud TTS Gemini model",
            lastCloudTtsBody?.contains(TtsModels.CLOUD_TTS_GEMINI_MODEL) == true,
        )
    }

    @Test
    fun geminiApi_missingKey_fallsBackToCloudGeminiVoice() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            lastCloudTtsBody = chain.request().body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
            response(chain, 200, "{\"audioContent\":\"${pcmBase64(4)}\"}")
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val file = generator.synthesizeWithGeminiApi("hello", geminiKey = null, ttsKey = "tts_key")

        assertNotNull(file)
        assertTrue(lastCloudTtsBody?.contains(TtsModels.CLOUD_TTS_GEMINI_MODEL) == true)
    }

    @Test
    fun geminiApi_quotaExhausted_andCloudGeminiForbidden_fallsBackToStandardVoice() = runTest {
        var standardBody: String? = null
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            if (chain.request().url.host.contains("generativelanguage")) {
                response(chain, 429, "{}")
            } else {
                val body = chain.request().body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
                if (body?.contains(TtsModels.CLOUD_TTS_GEMINI_MODEL) == true) {
                    response(chain, 403, "{}")
                } else {
                    standardBody = body
                    response(chain, 200, "{\"audioContent\":\"${pcmBase64(4)}\"}")
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val file = generator.synthesizeWithGeminiApi("hello", geminiKey = "gemini_key", ttsKey = "tts_key")

        assertNotNull("Standard voice fallback should produce audio", file)
        assertTrue(
            "Standard fallback must not request the Gemini model",
            standardBody?.contains(TtsModels.CLOUD_TTS_GEMINI_MODEL) != true,
        )
        assertTrue("Standard fallback should omit voice.modelName", standardBody?.contains("modelName") != true)
    }

    @Test
    fun cloudTts_emptyAudioContent_returnsNull() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(chain, 200, "{\"audioContent\":\"\"}")
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        assertNull(generator.fetchCloudGeminiTtsAudio("hi", "tts_key"))
    }

    @Test
    fun geminiApi_emptyAudioData_returnsFailed() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(chain, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"\"}}]}}]}")
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        assertEquals(AnnouncementPreGenerator.SynthResult.Failed, generator.fetchGeminiApiTtsAudio("hi", "fake_key"))
    }

    @Test
    fun geminiApi_audioPartNotFirst_isFoundAndRateParsedFromMimeType() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(
                chain,
                200,
                "{\"candidates\":[{\"content\":{\"parts\":[" +
                    "{\"text\":\"thinking out loud\"}," +
                    "{\"inlineData\":{\"mimeType\":\"audio/L16;codec=pcm;rate=16000\",\"data\":\"${pcmBase64(8)}\"}}" +
                    "]}}]}",
            )
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = preGen(context, this.testScheduler)

        val result = generator.fetchGeminiApiTtsAudio("hi", "fake_key")

        assertTrue(result is AnnouncementPreGenerator.SynthResult.Success)
        val bytes = (result as AnnouncementPreGenerator.SynthResult.Success).file.readBytes()
        // sample rate lives at bytes 24..27 of the WAV header, little-endian.
        val sampleRate = (bytes[24].toInt() and 0xff) or
            ((bytes[25].toInt() and 0xff) shl 8) or
            ((bytes[26].toInt() and 0xff) shl 16) or
            ((bytes[27].toInt() and 0xff) shl 24)
        assertEquals(16000, sampleRate)
    }

    @Test
    fun geminiApi_success_reportsGeminiVoice() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            response(chain, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"${pcmBase64()}\"}}]}}]}")
        }
        val generator = preGen(ApplicationProvider.getApplicationContext(), this.testScheduler)

        val announcement = generator.synthesizeWithGeminiApi("hello", geminiKey = "gemini_key", ttsKey = "tts_key")

        assertEquals("Gemini API", announcement?.voice)
    }

    @Test
    fun geminiApi_quotaExhausted_isSkippedUntilCooldownEnds() = runTest {
        var geminiCalls = 0
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            if (chain.request().url.host.contains("generativelanguage")) {
                geminiCalls++
                response(chain, 429, "{}")
            } else {
                response(chain, 200, "{\"audioContent\":\"${pcmBase64(4)}\"}")
            }
        }
        var now = 1_000L
        val generator = AnnouncementPreGenerator(
            ApplicationProvider.getApplicationContext(),
            TestScope(this.testScheduler),
            clock = { now },
        )

        generator.synthesizeWithGeminiApi("one", geminiKey = "gemini_key", ttsKey = "tts_key")
        now += AnnouncementPreGenerator.GEMINI_QUOTA_COOLDOWN_MS - 1
        val duringCooldown = generator.synthesizeWithGeminiApi("two", geminiKey = "gemini_key", ttsKey = "tts_key")

        assertEquals("a used-up quota must not be asked again straight away", 1, geminiCalls)
        assertEquals("Cloud TTS Gemini voice", duringCooldown?.voice)

        now += 2
        generator.synthesizeWithGeminiApi("three", geminiKey = "gemini_key", ttsKey = "tts_key")

        assertEquals("the Gemini API is tried again once the pause is over", 2, geminiCalls)
    }

    @Test
    fun everyVoiceFailing_recordsWhyEachOneFailed() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            if (chain.request().url.host.contains("generativelanguage")) {
                response(chain, 429, "{}")
            } else {
                val body = chain.request().body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
                response(chain, if (body?.contains(TtsModels.CLOUD_TTS_GEMINI_MODEL) == true) 403 else 429, "{}")
            }
        }
        val generator = preGen(ApplicationProvider.getApplicationContext(), this.testScheduler)
        val failures = mutableListOf<String>()

        val announcement = generator.synthesizeWithGeminiApi("hello", "gemini_key", "tts_key", failures)

        assertNull(announcement)
        assertEquals(3, failures.size)
        assertEquals("Gemini API quota used up (HTTP 429)", failures[0])
        assertEquals("Cloud TTS Gemini voice: HTTP 403", failures[1])
        assertTrue(failures[2], failures[2].startsWith("Cloud TTS standard voice (") && failures[2].endsWith("): HTTP 429"))
    }

    @Test
    fun fallbackToStandardVoice_reportsStandardVoice() = runTest {
        AnnouncementPreGenerator.testInterceptor = okhttp3.Interceptor { chain ->
            if (chain.request().url.host.contains("generativelanguage")) {
                response(chain, 429, "{}")
            } else {
                val body = chain.request().body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
                if (body?.contains(TtsModels.CLOUD_TTS_GEMINI_MODEL) == true) {
                    response(chain, 403, "{}")
                } else {
                    response(chain, 200, "{\"audioContent\":\"${pcmBase64(4)}\"}")
                }
            }
        }
        val generator = preGen(ApplicationProvider.getApplicationContext(), this.testScheduler)

        val announcement = generator.synthesizeWithGeminiApi("hello", geminiKey = "gemini_key", ttsKey = "tts_key")

        assertTrue(announcement?.voice, announcement?.voice?.startsWith("Cloud TTS standard voice (en-US-") == true)
    }

    @Test
    fun sourceMessage_namesTheVoiceOrTheReason() {
        val file = java.io.File("x.wav")
        assertEquals(
            "Cloud intro: Gemini API",
            announcementSourceMessage("intro", AnnouncementPreGenerator.ReadyResult.Ready(AnnouncementPreGenerator.Announcement(file, "Gemini API"))),
        )
        assertEquals(
            "Android TTS outro: Gemini API quota used up (HTTP 429)",
            announcementSourceMessage("outro", AnnouncementPreGenerator.ReadyResult.Unavailable("Gemini API quota used up (HTTP 429)")),
        )
    }
}
