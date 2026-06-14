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
) {
    companion object {
        private const val TAG = "AnnouncementPreGen"
        private const val KILO_ENDPOINT = "https://api.kilo.ai/api/gateway"
        private const val KILO_MODEL_ANON = "kilo/auto-free"
        private const val READY_TIMEOUT_MS = 5000L

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

    private val cache = ConcurrentHashMap<CacheKey, Deferred<File?>>()
    private val networkChecker = NetworkQualityChecker(context)

    /**
     * Schedules background generation for:
     *  - the outro of [current] (needed when [current] finishes)
     *  - the intro of [next] (needed when [next] starts)
     *
     * Evicts any cached entries that are no longer needed to keep the cache small.
     */
    fun schedulePreGeneration(current: MediaFileInfo, next: MediaFileInfo?) {
        scheduleOne(current, isIntro = true)
        scheduleOne(current, isIntro = false)
        if (next != null) scheduleOne(next, isIntro = true)

        val keysToKeep = setOfNotNull(
            CacheKey(current.uriString, true),
            CacheKey(current.uriString, false),
            next?.let { CacheKey(it.uriString, true) },
        )
        evictExcept(keysToKeep)
    }

    /**
     * Returns the pre-generated audio [File] for [track], or null if it is not ready within
     * [READY_TIMEOUT_MS] milliseconds or generation failed.  The caller should fall back to
     * Android TTS when null is returned.
     */
    suspend fun getReadyAudio(track: MediaFileInfo, isIntro: Boolean): File? {
        val key = CacheKey(track.uriString, isIntro)
        val deferred = cache[key]
        if (deferred == null) {
            Log.d(TAG, "No cached job for ${track.cleanTitle} (intro=$isIntro) - no cache entry")
            return null
        }
        if (!deferred.isCompleted) {
            Log.d(TAG, "Job not yet complete for ${track.cleanTitle} (intro=$isIntro)")
        }
        val startTime = System.currentTimeMillis()
        return try {
            withTimeout(READY_TIMEOUT_MS) { deferred.await() }.also {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Pre-generated audio ready in ${elapsed}ms for ${track.cleanTitle}")
            }
        } catch (_: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Pre-generated audio timed out after ${elapsed}ms (timeout=${READY_TIMEOUT_MS}ms) for ${track.cleanTitle} — falling back to TTS")
            null
        }
    }

    /** Cancels all in-flight jobs and deletes any cached temp files. */
    fun cancelAll() {
        val entries = cache.entries.toList()
        cache.clear()
        entries.forEach { (_, deferred) ->
            if (deferred.isCompleted) {
                deferred.getCompleted()?.let { runCatching { it.delete() } }
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
            generateAudio(track.cleanTitle, track.artist?.takeIf { it.isNotBlank() }, isIntro)
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
                    deferred.getCompleted()?.let { runCatching { it.delete() } }
                } else {
                    deferred.cancel()
                }
            }
        }
    }

    private suspend fun generateAudio(title: String, artist: String?, isIntro: Boolean): File? {
        val quality = networkChecker.check()
        if (quality != NetworkQualityChecker.Quality.GOOD) {
            Log.d(TAG, "Network quality is $quality — skipping cloud pre-generation")
            return null
        }

        val prefs = ApiKeyStore.getPrefs(context)
        if (prefs == null) {
            Log.d(TAG, "No encrypted prefs — skipping cloud pre-generation")
            return null
        }

        val kiloKey = prefs.getString(ApiKeyStore.KEY_KILO, null)?.takeIf { it.isNotBlank() }
        val ttsKey = prefs.getString(ApiKeyStore.KEY_CLOUD_TTS, null)?.takeIf { it.isNotBlank() }

        if (ttsKey == null) {
            Log.d(TAG, "No TTS key — will use on-device TTS")
            return null
        }

        Log.d(TAG, "Calling Kilo API for: $title")
        val textFromApi = fetchKiloText(title, artist, isIntro, kiloKey)
        Log.d(TAG, "Kilo returned: $textFromApi")
        val text = textFromApi ?: run {
            val fallback = getStockPhrase(title, artist, isIntro)
            Log.d(TAG, "Using stock phrase: $fallback")
            fallback
        }
        Log.d(TAG, "Using text: $text")

        Log.d(TAG, "Calling Google TTS API")
        return fetchGoogleTtsAudio(text, ttsKey)
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

    @androidx.annotation.VisibleForTesting
    internal suspend fun fetchKiloText(
        title: String,
        artist: String?,
        isIntro: Boolean,
        apiKey: String?,
    ): String? = withContext(Dispatchers.IO) {
        val artistName = artist ?: "Unknown"
        val prompt = if (isIntro) {
            "Radio intro for \"$title\" by $artistName. Include both artist and song. Max 8 words total. Examples: \"Up next: $title by $artistName\", \"Here's $title from $artistName\". Just the text, no quotes."
        } else {
            "Radio outro for \"$title\" by $artistName. Include both artist and song. Max 8 words total. Examples: \"That was $title by $artistName\", \"Thanks for listening to $title\". Just the text, no quotes."
        }

        val sanitizedKey = apiKey?.replace("\r", "")?.replace("\n", "")?.trim()
        val isAnon = sanitizedKey.isNullOrBlank()
        val authHeader = if (isAnon) "Bearer anonymous" else "Bearer $sanitizedKey"
        val model = if (isAnon) "kilo/auto-free" else "anthropic/claude-sonnet-4-6"

        try {
            val bodyString = JSONObject().apply {
                put("model", model)
                put("max_tokens", 150)
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
                .url("$KILO_ENDPOINT/chat/completions")
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
                Log.w(TAG, "Kilo no content")
                return@withContext null
            }
            content.trim()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Kilo API exception: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchGoogleTtsAudio(text: String, apiKey: String): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyString = JSONObject().apply {
                    put("input", JSONObject().put("text", text))
                    put("voice", JSONObject().apply {
                        put("languageCode", "en-US")
                        val voices = listOf("en-US-Neural2-F", "en-US-Neural2-J", "en-US-Neural2-I")
                        put("name", voices.random())
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
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize")
                    .header("X-Goog-Api-Key", apiKey)
                    .post(requestBody)
                    .build()

                val responseBody = okHttpClient.newCall(request).awaitStringOrNull {
                    Log.w(TAG, "Google TTS API returned HTTP ${it.code}")
                }
                if (responseBody.isNullOrBlank()) {
                    return@runCatching null
                }

                val audioBase64 = try {
                    JSONObject(responseBody).getString("audioContent")
                } catch (e: Exception) {
                    return@runCatching null
                }

                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)

                val file = File(context.cacheDir, "${UUID.randomUUID()}.mp3")
                file.createNewFile()
                file.writeBytes(audioBytes)
                Log.d(TAG, "Saved pre-generated announcement to ${file.name} (${audioBytes.size} bytes)")
                file
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "Google TTS API call failed: ${e.message}")
                null
            }
        }
}
