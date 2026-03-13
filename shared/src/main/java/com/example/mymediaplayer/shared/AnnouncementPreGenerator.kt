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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-generates announcement audio for upcoming track intros and outros while the current
 * song is playing, so there is no delay at track boundaries.
 *
 * When network quality is good, uses the Claude API to write a natural radio-DJ line and
 * Google Cloud Text-to-Speech to synthesise it into an MP3. Falls back to Android TTS
 * (handled by the caller) when the network is unavailable or either API key is missing.
 *
 * API keys are stored via [ApiKeyStore] in an [androidx.security.crypto.EncryptedSharedPreferences]
 * file backed by the Android Keystore.
 */
internal class AnnouncementPreGenerator(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "AnnouncementPreGen"
        private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"
        private const val READY_TIMEOUT_MS = 5000L
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
        val claudeKey = prefs.getString(ApiKeyStore.KEY_CLAUDE, null)?.takeIf { it.isNotBlank() }
        if (claudeKey == null) {
            Log.d(TAG, "No Claude key — skipping cloud pre-generation")
            return null
        }
        val ttsKey = prefs.getString(ApiKeyStore.KEY_CLOUD_TTS, null)?.takeIf { it.isNotBlank() }
        if (ttsKey == null) {
            Log.d(TAG, "No TTS key — skipping cloud pre-generation")
            return null
        }

        Log.d(TAG, "Calling Claude API for: $title")
        val text = fetchClaudeText(title, artist, isIntro, claudeKey)
        if (text == null) {
            Log.d(TAG, "Claude API returned null")
            return null
        }
        Log.d(TAG, "Claude returned: $text")
        Log.d(TAG, "Calling Google TTS API")
        return fetchGoogleTtsAudio(text, ttsKey)
    }

    private suspend fun fetchClaudeText(
        title: String,
        artist: String?,
        isIntro: Boolean,
        apiKey: String,
    ): String? = withContext(Dispatchers.IO) {
        val who = if (artist != null) "$title by $artist" else title
        val prompt = if (isIntro) {
            "Write a very short, 3-5 word intro for $who. Like \"Up next: $title\" or just the song title. No extra words."
        } else {
            "Write a very short, 2-4 word outro for $who. Like \"Thanks for listening\" or just the song title. No extra words."
        }

        runCatching {
            val conn = URL("https://api.anthropic.com/v1/messages")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("content-type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("model", CLAUDE_MODEL)
                put("max_tokens", 80)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                ))
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode != 200) {
                Log.w(TAG, "Claude API returned HTTP ${conn.responseCode}")
                return@runCatching null
            }

            JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }.getOrElse { e ->
            Log.w(TAG, "Claude API call failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchGoogleTtsAudio(text: String, apiKey: String): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 10_000
                conn.requestMethod = "POST"
                conn.setRequestProperty("content-type", "application/json")
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("input", JSONObject().put("text", text))
                    put("voice", JSONObject().apply {
                        put("languageCode", "en-US")
                        put("name", "en-US-Neural2-F")
                    })
                    put("audioConfig", JSONObject().apply {
                        put("audioEncoding", "MP3")
                        put("speakingRate", 0.95)
                        put("pitch", 0.0)
                    })
                }.toString()

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                if (conn.responseCode != 200) {
                    Log.w(TAG, "Google TTS API returned HTTP ${conn.responseCode}")
                    return@runCatching null
                }

                val audioBase64 = JSONObject(conn.inputStream.bufferedReader().readText())
                    .getString("audioContent")
                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)

                val file = File.createTempFile("announcement_", ".mp3", context.cacheDir)
                file.writeBytes(audioBytes)
                Log.d(TAG, "Saved pre-generated announcement to ${file.name} (${audioBytes.size} bytes)")
                file
            }.getOrElse { e ->
                Log.w(TAG, "Google TTS API call failed: ${e.message}")
                null
            }
        }
}
