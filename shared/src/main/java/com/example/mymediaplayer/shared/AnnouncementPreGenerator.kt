package com.example.mymediaplayer.shared

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** The debug message saying which voice an intro or outro ([kind]) used, or why it fell back. */
internal fun announcementSourceMessage(kind: String, ready: AnnouncementPreGenerator.ReadyResult?): String =
    when (ready) {
        is AnnouncementPreGenerator.ReadyResult.Ready -> "Cloud $kind: ${ready.announcement.voice}"
        is AnnouncementPreGenerator.ReadyResult.Unavailable -> "Android TTS $kind: ${ready.reason}"
        null -> "Android TTS $kind: cloud voices not available"
    }

/**
 * Pre-generates announcement audio for upcoming track intros and outros while the current
 * song is playing, so there is no delay at track boundaries.
 *
 * When network quality is good, uses the Kilo API to write a natural radio-DJ line and
 * Google Cloud Text-to-Speech to synthesise it into an MP3. Falls back to Android TTS
 * (handled by the caller) when the network is unavailable or either API key is missing.
 *
 * API keys are stored via [ApiKeyStore] in an [androidx.security.crypto.EncryptedSharedPreferences]
 * file backed by the Android Keystore.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class AnnouncementPreGenerator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    companion object {
        private const val TAG = "AnnouncementPreGen"
        /** Kilo's free auto router; the only kind of model Kilo serves without a key. */
        private const val KILO_MODEL_ANON = "kilo-auto/free"
        /** Kilo's auto router picks a low-cost model, which is plenty for a one-line intro. */
        @androidx.annotation.VisibleForTesting
        internal const val KILO_MODEL_WITH_KEY = "kilo-auto/efficient"
        private const val READY_TIMEOUT_MS = 5000L

        /** How long to stop calling the Gemini API after it reports its quota is used up (429). */
        @androidx.annotation.VisibleForTesting
        internal const val GEMINI_QUOTA_COOLDOWN_MS = 60_000L

        private const val CLOUD_TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
        private const val STANDARD_TTS_LANGUAGE = "en-US"
        private val STANDARD_TTS_VOICES =
            listOf("en-US-Neural2-F", "en-US-Neural2-J", "en-US-Neural2-I")

        /** Gemini API TTS returns raw 16-bit little-endian PCM at 24 kHz, which we wrap as WAV. */
        private const val GEMINI_TTS_SAMPLE_RATE = 24000

        private const val GEMINI_API_LABEL = "Gemini API"
        private const val CLOUD_GEMINI_LABEL = "Cloud TTS Gemini voice"

        @androidx.annotation.VisibleForTesting
        internal var testInterceptor: okhttp3.Interceptor? = null

        private val okHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain -> testInterceptor?.intercept(chain) ?: chain.proceed(chain.request()) }
                .build()
        }
    }

    private data class CacheKey(val uri: String, val isIntro: Boolean)

    /** Announcement audio and the voice that made it. */
    internal data class Announcement(val file: File, val voice: String)

    /** Result of one generation: the audio, or why every voice failed. */
    internal sealed interface Generated {
        data class Done(val announcement: Announcement) : Generated
        data class Failed(val reason: String) : Generated
    }

    /** What [getReadyAudio] found: the audio, or why the caller must use on-device TTS. */
    internal sealed interface ReadyResult {
        data class Ready(val announcement: Announcement) : ReadyResult
        data class Unavailable(val reason: String) : ReadyResult
    }

    private val cache = ConcurrentHashMap<CacheKey, Deferred<Generated>>()
    private val networkChecker = NetworkQualityChecker(context)

    /** Until this [clock] time, the Gemini API is skipped because it reported its quota used up. */
    @Volatile private var geminiCooldownUntil = 0L

    /** Until this [clock] time, Gemini isn't asked to write the line because its last call failed. */
    @Volatile private var geminiTextCooldownUntil = 0L

    /**
     * Called when [current] starts. Its intro is needed straight away, so it is generated now
     * unless it was already made near the end of the previous track. Everything else is dropped.
     */
    fun prepareTrackStart(current: MediaFileInfo, introEnabled: Boolean) {
        val introKey = CacheKey(current.uriString, true)
        evictExcept(if (introEnabled) setOf(introKey) else emptySet())
        if (introEnabled) scheduleOne(current, isIntro = true)
    }

    /**
     * Called shortly before [current] ends: generates its outro and the intro of [next], so a
     * track that is skipped before then costs no requests.
     */
    fun prepareTrackEnd(current: MediaFileInfo, next: MediaFileInfo?, introEnabled: Boolean, outroEnabled: Boolean) {
        if (outroEnabled) scheduleOne(current, isIntro = false)
        if (introEnabled && next != null) scheduleOne(next, isIntro = true)
        evictExcept(
            setOfNotNull(
                CacheKey(current.uriString, true),
                CacheKey(current.uriString, false),
                next?.let { CacheKey(it.uriString, true) },
            )
        )
    }

    /** The (uri, isIntro) announcements currently generating or generated. */
    @androidx.annotation.VisibleForTesting
    internal fun scheduledForTesting(): Set<Pair<String, Boolean>> =
        cache.keys.map { it.uri to it.isIntro }.toSet()

    /**
     * Returns the pre-generated audio for [track] if it is ready within [READY_TIMEOUT_MS], or
     * the reason it isn't. The caller should fall back to Android TTS when it isn't ready.
     */
    suspend fun getReadyAudio(track: MediaFileInfo, isIntro: Boolean): ReadyResult {
        val key = CacheKey(track.uriString, isIntro)
        val deferred = cache[key]
        if (deferred == null) {
            Log.d(TAG, "No cached job for ${track.cleanTitle} (intro=$isIntro) - no cache entry")
            return ReadyResult.Unavailable("not generated")
        }
        if (!deferred.isCompleted) {
            Log.d(TAG, "Job not yet complete for ${track.cleanTitle} (intro=$isIntro)")
        }
        val startTime = System.currentTimeMillis()
        val generated = try {
            withTimeout(READY_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Pre-generated audio timed out after ${elapsed}ms (timeout=${READY_TIMEOUT_MS}ms) for ${track.cleanTitle} — falling back to TTS")
            return ReadyResult.Unavailable("cloud voice took over ${READY_TIMEOUT_MS / 1000}s")
        }
        val elapsed = System.currentTimeMillis() - startTime
        return when (generated) {
            is Generated.Done -> {
                Log.d(TAG, "Pre-generated audio ready in ${elapsed}ms for ${track.cleanTitle} (voice=${generated.announcement.voice})")
                ReadyResult.Ready(generated.announcement)
            }
            is Generated.Failed -> {
                Log.d(TAG, "No pre-generated audio for ${track.cleanTitle}: ${generated.reason} — falling back to TTS")
                ReadyResult.Unavailable(generated.reason)
            }
        }
    }

    /** Cancels all in-flight jobs and deletes any cached temp files. */
    fun cancelAll() {
        val entries = cache.entries.toList()
        cache.clear()
        entries.forEach { (_, deferred) ->
            if (deferred.isCompleted) {
                (deferred.getCompleted() as? Generated.Done)?.let { runCatching { it.announcement.file.delete() } }
            } else {
                deferred.cancel()
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------------------

    private fun scheduleOne(track: MediaFileInfo, isIntro: Boolean) {
        val key = CacheKey(track.uriString, isIntro)
        // Only start generation if there is no existing entry for this key.
        val deferred = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            Log.d(TAG, "Starting cloud generation for: ${track.cleanTitle} (intro=$isIntro)")
            generateAudio(track.cleanTitle, track.artist?.takeIf { it.isNotBlank() }, isIntro).also {
                when (it) {
                    is Generated.Done -> Log.i(TAG, "Announcement for ${track.cleanTitle} (intro=$isIntro) made with ${it.announcement.voice}")
                    is Generated.Failed -> Log.w(TAG, "No cloud announcement for ${track.cleanTitle} (intro=$isIntro): ${it.reason}")
                }
            }
        }
        if (cache.putIfAbsent(key, deferred) == null) {
            deferred.start()
        } else {
            // Lost the race — a deferred already exists; discard the one we just created.
            deferred.cancel()
        }
    }

    private fun evictExcept(keep: Set<CacheKey>) {
        val toRemove = cache.keys.filter { it !in keep }
        toRemove.forEach { key ->
            cache.remove(key)?.let { deferred ->
                if (deferred.isCompleted) {
                    (deferred.getCompleted() as? Generated.Done)?.let { runCatching { it.announcement.file.delete() } }
                } else {
                    deferred.cancel()
                }
            }
        }
    }

    private suspend fun generateAudio(title: String, artist: String?, isIntro: Boolean): Generated {
        val quality = networkChecker.check()
        if (quality != NetworkQualityChecker.Quality.GOOD) {
            Log.d(TAG, "Network quality is $quality — skipping cloud pre-generation")
            return Generated.Failed("network ${quality.name.lowercase()}")
        }

        val prefs = ApiKeyStore.getPrefs(context)
        if (prefs == null) {
            Log.d(TAG, "No encrypted prefs — skipping cloud pre-generation")
            return Generated.Failed("API keys unreadable")
        }

        val kiloKey = prefs.getString(ApiKeyStore.KEY_KILO, null)?.takeIf { it.isNotBlank() }
        val ttsKey = prefs.getString(ApiKeyStore.KEY_CLOUD_TTS, null)?.takeIf { it.isNotBlank() }
        val geminiKey = prefs.getString(ApiKeyStore.KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
        val backend = TtsBackend.fromStorage(prefs.getString(ApiKeyStore.KEY_TTS_BACKEND, null))

        if (ttsKey == null && geminiKey == null) {
            Log.d(TAG, "No cloud TTS key — will use on-device TTS")
            return Generated.Failed("no cloud TTS key")
        }

        val (text, lineSource) = fetchLine(title, artist, isIntro, geminiKey, kiloKey)
        Log.d(TAG, "Using text from $lineSource: $text")

        Log.d(TAG, "Synthesising with backend=$backend")
        val failures = mutableListOf<String>()
        val announcement = when (backend) {
            TtsBackend.STANDARD -> synthesizeStandard(text, ttsKey, failures)
            TtsBackend.CLOUD_GEMINI -> synthesizeCloudGemini(text, ttsKey, failures)
            TtsBackend.GEMINI_API -> synthesizeWithGeminiApi(text, geminiKey, ttsKey, failures)
        }
        return announcement?.let { Generated.Done(it.copy(voice = "${it.voice}, line by $lineSource")) }
            ?: Generated.Failed(failures.joinToString("; ").ifEmpty { "no voice produced audio" })
    }

    private fun warnMissingKey(backend: String, failures: MutableList<String>): Announcement? {
        Log.w(TAG, "Backend $backend selected but its API key is missing — falling back to on-device TTS")
        failures += "no key for $backend"
        return null
    }

    private suspend fun synthesizeStandard(text: String, ttsKey: String?, failures: MutableList<String>): Announcement? {
        if (ttsKey == null) return warnMissingKey("standard", failures)
        val voiceName = STANDARD_TTS_VOICES.random()
        val label = "Cloud TTS standard voice ($voiceName)"
        return fetchCloudTtsAudio(
            text,
            ttsKey,
            languageCode = STANDARD_TTS_LANGUAGE,
            voiceName = voiceName,
            modelName = null,
            onFailure = { failures += "$label: $it" },
        )?.let { Announcement(it, label) }
    }

    private suspend fun synthesizeCloudGemini(text: String, ttsKey: String?, failures: MutableList<String>): Announcement? {
        if (ttsKey == null) return warnMissingKey("cloud_gemini", failures)
        return fetchCloudGeminiTtsAudio(text, ttsKey) { failures += "$CLOUD_GEMINI_LABEL: $it" }
            ?.let { Announcement(it, CLOUD_GEMINI_LABEL) }
    }

    /**
     * Cloud TTS fallback chain used when the Gemini API backend cannot synthesise: try the
     * Gemini voice first, then the standard voice, and finally give up (on-device TTS).
     */
    private suspend fun synthesizeCloudFallback(text: String, ttsKey: String?, failures: MutableList<String>): Announcement? {
        if (ttsKey == null) {
            Log.w(TAG, "No Cloud TTS key for fallback — using on-device TTS")
            failures += "no Cloud TTS key for fallback"
            return null
        }
        return synthesizeCloudGemini(text, ttsKey, failures) ?: synthesizeStandard(text, ttsKey, failures)
    }

    /**
     * Option 2 (Gemini API) with graceful degradation: on resource exhaustion (HTTP 429) or
     * failure, fall back to the Cloud TTS Gemini voice, then the standard Cloud TTS voice, and
     * finally to on-device TTS (null). After a 429 the Gemini API is skipped for
     * [GEMINI_QUOTA_COOLDOWN_MS], so a used-up quota doesn't cost a failed request per track.
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun synthesizeWithGeminiApi(
        text: String,
        geminiKey: String?,
        ttsKey: String?,
        failures: MutableList<String> = mutableListOf(),
    ): Announcement? {
        if (geminiKey == null) {
            Log.w(TAG, "Gemini API backend selected but no Gemini key — trying Cloud TTS")
            failures += "no Gemini key"
            return synthesizeCloudFallback(text, ttsKey, failures)
        }
        val cooldownLeft = geminiCooldownUntil - clock()
        if (cooldownLeft > 0) {
            Log.d(TAG, "Gemini API paused for ${cooldownLeft / 1000}s after quota error — using Cloud TTS")
            failures += "Gemini API paused after quota error"
            return synthesizeCloudFallback(text, ttsKey, failures)
        }
        return when (val result = fetchGeminiApiTtsAudio(text, geminiKey)) {
            is SynthResult.Success -> Announcement(result.file, GEMINI_API_LABEL)
            SynthResult.ResourceExhausted -> {
                geminiCooldownUntil = clock() + GEMINI_QUOTA_COOLDOWN_MS
                Log.w(TAG, "Gemini API TTS quota exhausted (429) — pausing it for ${GEMINI_QUOTA_COOLDOWN_MS / 1000}s, falling back to Cloud TTS")
                failures += "Gemini API quota used up (HTTP 429)"
                synthesizeCloudFallback(text, ttsKey, failures)
            }
            SynthResult.Failed -> {
                Log.w(TAG, "Gemini API TTS failed — falling back to Cloud TTS")
                failures += "Gemini API failed"
                synthesizeCloudFallback(text, ttsKey, failures)
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun getStockPhrase(title: String, artist: String?, isIntro: Boolean): String {
        val artistName = artist ?: "Unknown"
        return if (isIntro) {
            listOf(
                "Up next: $title by $artistName",
                "Here's $title by $artistName",
                "$title is coming up by $artistName",
                "Now playing: $title by $artistName",
            ).random()
        } else {
            listOf(
                "That was $title by $artistName",
                "Thanks for listening to $title by $artistName",
                "$title is done by $artistName",
            ).random()
        }
    }

    /**
     * Writes the announcement line and says who wrote it: Gemini when there is a Gemini key
     * (a cheap text model whose quota is separate from the TTS model's), then Kilo, then a
     * stock phrase.
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchLine(
        title: String,
        artist: String?,
        isIntro: Boolean,
        geminiKey: String?,
        kiloKey: String?,
    ): Pair<String, String> {
        if (geminiKey != null && clock() >= geminiTextCooldownUntil) {
            fetchGeminiText(title, artist, isIntro, geminiKey)?.let { return it to "Gemini" }
        }
        Log.d(TAG, "Calling Kilo API for: $title")
        val kiloText = fetchKiloText(title, artist, isIntro, kiloKey)
        Log.d(TAG, "Kilo returned: $kiloText")
        if (kiloText != null) return kiloText to "Kilo"
        return getStockPhrase(title, artist, isIntro) to "stock phrase"
    }

    private fun linePrompt(title: String, artist: String?, isIntro: Boolean): String {
        val artistName = artist ?: "Unknown"
        return if (isIntro) {
            "Radio intro for \"$title\" by $artistName. Include both artist and song. Up to 16 words; vary the wording like a real radio DJ. Examples: \"Up next: $title by $artistName\", \"Here's $title from $artistName\". Just the text, no quotes."
        } else {
            "Radio outro for \"$title\" by $artistName. Include both artist and song. Up to 16 words; vary the wording like a real radio DJ. Examples: \"That was $title by $artistName\", \"Thanks for listening to $title\". Just the text, no quotes."
        }
    }

    /** Asks Gemini's cheapest text model for the line; null if it fails, so Kilo can try. */
    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchGeminiText(
        title: String,
        artist: String?,
        isIntro: Boolean,
        apiKey: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", linePrompt(title, artist, isIntro))))
                }))
                // Flash-Lite thinks at its minimal level by default. Gemini 3 rejects the old
                // thinkingBudget setting with HTTP 400, so no thinkingConfig is sent.
                put("generationConfig", JSONObject().put("maxOutputTokens", 100))
            }.toString()
            val request = Request.Builder()
                .url(TtsModels.GEMINI_TEXT_URL)
                .header("x-goog-api-key", apiKey)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val responseText = okHttpClient.newCall(request).awaitStringOrNull {
                // Whether quota, a bad key or a retired model, the next song would fail too.
                geminiTextCooldownUntil = clock() + GEMINI_QUOTA_COOLDOWN_MS
                val detail = runCatching { it.peekBody(300).string() }.getOrDefault("")
                Log.w(TAG, "Gemini text HTTP ${it.code}: falling back to Kilo for ${GEMINI_QUOTA_COOLDOWN_MS / 1000}s. $detail")
            } ?: return@withContext null
            val parts = JSONObject(responseText).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: return@withContext null
            val line = (0 until parts.length())
                .mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf { t -> t.isNotBlank() } }
                .joinToString(" ")
                .trim()
                .trim('"')
                .takeIf { it.isNotBlank() }
            if (line == null) Log.w(TAG, "Gemini text returned no line: falling back to Kilo")
            line
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Gemini text call failed: ${e.message}")
            null
        }
    }

    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchKiloText(
        title: String,
        artist: String?,
        isIntro: Boolean,
        apiKey: String?,
    ): String? = withContext(Dispatchers.IO) {
        val prompt = linePrompt(title, artist, isIntro)

        // Kilo keys are JWTs, whose three parts are joined by dots, so dots must survive.
        val sanitizedKey = apiKey?.replace(Regex("[^A-Za-z0-9+/=.\\-_]"), "")?.trim()
        val isAnon = sanitizedKey.isNullOrBlank()
        val authHeader = if (isAnon) "Bearer anonymous" else "Bearer $sanitizedKey"
        val model = if (isAnon) KILO_MODEL_ANON else KILO_MODEL_WITH_KEY

        try {
            val bodyString = JSONObject().apply {
                put("model", model)
                // Kilo's auto models reason first, using 100-150 tokens, and the reasoning
                // counts toward this limit. At 150 the line often came back empty.
                put("max_tokens", 500)
                put("reasoning", JSONObject().apply {
                    put("effort", "low")
                })
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                ))
            }.toString()

            val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.KILO_ENDPOINT}/chat/completions")
                .header("Authorization", authHeader)
                .post(requestBody)
                .build()

            val responseText = okHttpClient.newCall(request).awaitStringOrNull {
                Log.w(TAG, "Kilo HTTP ${it.code}: API request failed")
            }
            if (responseText.isNullOrBlank()) {
                Log.w(TAG, "Kilo response empty")
                return@withContext null
            }

            val responseJson = JSONObject(responseText)
            if (!responseJson.has("choices") || responseJson.getJSONArray("choices").length() == 0) {
                Log.w(TAG, "Kilo no choices")
                return@withContext null
            }
            val message = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = message.optString("content")?.takeIf { it != "null" && it.isNotBlank() }
            if (content == null) {
                val finish = responseJson.getJSONArray("choices").getJSONObject(0).optString("finish_reason")
                Log.w(TAG, "Kilo no content (finish_reason=$finish)")
                return@withContext null
            }
            content.trim()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Kilo API exception: ${e.message}", e)
            null
        }
    }

    /**
     * Synthesises [text] through the Cloud Text-to-Speech `text:synthesize` endpoint and caches
     * the resulting MP3. [modelName] selects a Gemini-TTS voice when non-null; otherwise the
     * standard (Neural2) voice named [voiceName] is used.
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchCloudTtsAudio(
        text: String,
        apiKey: String,
        languageCode: String,
        voiceName: String,
        modelName: String?,
        onFailure: (String) -> Unit = {},
    ): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyString = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("text", text)
                    })
                    put("voice", JSONObject().apply {
                        put("languageCode", languageCode)
                        if (modelName != null) put("modelName", modelName)
                        put("name", voiceName)
                    })
                    put("audioConfig", JSONObject().apply {
                        put("audioEncoding", "MP3")
                        put("speakingRate", 1.0)
                        put("pitch", 0.0)
                        put("volumeGainDb", 1.0)
                    })
                }.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(CLOUD_TTS_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .post(requestBody)
                    .build()

                var httpFailed = false
                val responseBody = okHttpClient.newCall(request).awaitStringOrNull {
                    httpFailed = true
                    Log.w(TAG, "Cloud TTS API returned HTTP ${it.code}")
                    onFailure("HTTP ${it.code}")
                }
                if (responseBody.isNullOrBlank()) {
                    // awaitStringOrNull gives null for both an HTTP error and an empty body.
                    if (!httpFailed) onFailure("empty response")
                    return@runCatching null
                }

                val audioBase64 = try {
                    JSONObject(responseBody).getString("audioContent")
                } catch (e: Exception) {
                    onFailure("no audio in response")
                    return@runCatching null
                }

                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                if (audioBytes.isEmpty()) {
                    Log.w(TAG, "Cloud TTS API returned empty audio content")
                    onFailure("empty audio")
                    return@runCatching null
                }
                writeAnnouncementFile(audioBytes, "mp3", audioBytes.size)
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "Cloud TTS API call failed: ${e.message}")
                onFailure("request failed")
                null
            }
        }

    /** Option 1: Cloud TTS using a Gemini-TTS voice (reuses the Cloud TTS key). */
    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchCloudGeminiTtsAudio(text: String, apiKey: String, onFailure: (String) -> Unit = {}): File? {
        Log.d(TAG, "Calling Cloud TTS Gemini voice (${TtsModels.CLOUD_TTS_GEMINI_MODEL})")
        return fetchCloudTtsAudio(
            text = text,
            apiKey = apiKey,
            languageCode = STANDARD_TTS_LANGUAGE,
            voiceName = TtsModels.GEMINI_VOICE,
            modelName = TtsModels.CLOUD_TTS_GEMINI_MODEL,
            onFailure = onFailure,
        )
    }

    /** Outcome of an Option 2 synthesis attempt, so callers can react to quota exhaustion. */
    internal sealed interface SynthResult {
        data class Success(val file: File) : SynthResult
        data object ResourceExhausted : SynthResult
        data object Failed : SynthResult
    }

    /**
     * Option 2: Gemini API `generateContent` TTS. Returns raw PCM (24 kHz, 16-bit LE) which is
     * wrapped in a WAV container so it can be played by the existing announcement player.
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchGeminiApiTtsAudio(text: String, apiKey: String): SynthResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(TtsModels.GEMINI_API_TTS_URL)
                    .header("x-goog-api-key", apiKey)
                    .post(TtsModels.geminiTtsRequestBody(text).toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                okHttpClient.newCall(request).await { response ->
                    when {
                        response.code == 429 -> SynthResult.ResourceExhausted
                        !response.isSuccessful -> {
                            Log.w(TAG, "Gemini API TTS returned HTTP ${response.code}")
                            SynthResult.Failed
                        }
                        else -> {
                            val responseBody = response.body?.string()
                            if (responseBody.isNullOrBlank()) return@await SynthResult.Failed
                            val inline = try {
                                findInlineAudio(JSONObject(responseBody))
                            } catch (e: Exception) {
                                null
                            }
                            if (inline == null) {
                                Log.w(TAG, "Gemini API TTS response missing inline audio data")
                                return@await SynthResult.Failed
                            }
                            val pcm = Base64.decode(inline.first, Base64.DEFAULT)
                            if (pcm.isEmpty()) {
                                Log.w(TAG, "Gemini API TTS returned empty audio data")
                                return@await SynthResult.Failed
                            }
                            SynthResult.Success(writeWavFile(pcm, inline.second, channels = 1))
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Gemini API TTS call failed: ${e.message}")
                SynthResult.Failed
            }
        }

    /**
     * Finds the first inline audio part in a Gemini `generateContent` response, without assuming
     * the audio is the first part. Returns the base64 payload and the sample rate parsed from the
     * part's `mimeType` (defaulting to 24 kHz when absent).
     */
    private fun findInlineAudio(responseJson: JSONObject): Pair<String, Int>? {
        val candidates = responseJson.optJSONArray("candidates") ?: return null
        for (i in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(i)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val inline = parts.optJSONObject(j)?.optJSONObject("inlineData") ?: continue
                val data = inline.optString("data")
                if (data.isNullOrEmpty()) continue
                return data to parseSampleRate(inline.optString("mimeType"))
            }
        }
        return null
    }

    private fun parseSampleRate(mimeType: String?): Int {
        val match = Regex("rate=(\\d+)").find(mimeType ?: "")
        return match?.groupValues?.get(1)?.toIntOrNull() ?: GEMINI_TTS_SAMPLE_RATE
    }

    private fun writeAnnouncementFile(bytes: ByteArray, extension: String, pcmSize: Int): File {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.$extension")
        file.createNewFile()
        file.writeBytes(bytes)
        Log.d(TAG, "Saved pre-generated announcement to ${file.name} (${bytes.size} bytes, pcm=$pcmSize)")
        return file
    }

    /**
     * Streams a 44-byte RIFF/WAVE header followed by raw 16-bit little-endian PCM straight to the
     * cache file, avoiding a second full-size copy of the audio buffer.
     */
    private fun writeWavFile(pcm: ByteArray, sampleRate: Int, channels: Int): File {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.wav")
        java.io.FileOutputStream(file).use { out ->
            out.write(wavHeader(pcm.size, sampleRate, channels))
            out.write(pcm)
        }
        Log.d(TAG, "Saved pre-generated announcement to ${file.name} (${44 + pcm.size} bytes, pcm=${pcm.size})")
        return file
    }

    /** Builds the 44-byte RIFF/WAVE header for 16-bit little-endian PCM. */
    private fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int): ByteArray {
        val bitsPerSample = 16
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                       // PCM subchunk size
        header.putShort(1)                      // audio format = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        return header.array()
    }
}
