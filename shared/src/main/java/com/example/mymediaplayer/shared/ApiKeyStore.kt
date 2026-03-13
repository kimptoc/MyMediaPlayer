package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Secure storage for API keys used by [AnnouncementPreGenerator].
 *
 * Keys are stored in an [EncryptedSharedPreferences] file backed by the Android Keystore,
 * so both preference keys and values are AES-256 encrypted at rest.
 *
 * Use [getPrefs] to obtain the [SharedPreferences] instance, then read/write via the
 * [KEY_CLAUDE] and [KEY_CLOUD_TTS] constants.
 */
object ApiKeyStore {

    const val KEY_CLAUDE = "claude_api_key"
    const val KEY_CLOUD_TTS = "cloud_tts_api_key"

    private const val TAG = "ApiKeyStore"
    private const val ENCRYPTED_PREFS_NAME = "mymediaplayer_api_keys"
    private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"

    sealed class ValidationResult {
        data object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    suspend fun validateKeys(context: Context): Pair<ValidationResult, ValidationResult> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context) ?: return@withContext Pair(
            ValidationResult.Error("Encrypted storage unavailable"),
            ValidationResult.Error("Encrypted storage unavailable")
        )

        val claudeKey = prefs.getString(KEY_CLAUDE, null)?.takeIf { it.isNotBlank() }
        val ttsKey = prefs.getString(KEY_CLOUD_TTS, null)?.takeIf { it.isNotBlank() }

        val claudeResult = if (claudeKey != null) {
            validateClaudeKey(claudeKey)
        } else {
            ValidationResult.Error("No Claude key configured")
        }

        val ttsResult = if (ttsKey != null) {
            validateTtsKey(ttsKey)
        } else {
            ValidationResult.Error("No Google TTS key configured")
        }

        Pair(claudeResult, ttsResult)
    }

    private suspend fun validateClaudeKey(apiKey: String): ValidationResult = withContext(Dispatchers.IO) {
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
                put("max_tokens", 10)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hi")
                    }
                ))
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                return@withContext ValidationResult.Error("HTTP ${conn.responseCode}: $errorBody")
            }

            ValidationResult.Success
        }.getOrElse { e ->
            ValidationResult.Error("Connection failed: ${e.message}")
        }
    }

    private suspend fun validateTtsKey(apiKey: String): ValidationResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("content-type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("input", JSONObject().put("text", "test"))
                put("voice", JSONObject().apply {
                    put("languageCode", "en-US")
                    put("name", "en-US-Neural2-F")
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                })
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                return@withContext ValidationResult.Error("HTTP ${conn.responseCode}: $errorBody")
            }

            ValidationResult.Success
        }.getOrElse { e ->
            ValidationResult.Error("Connection failed: ${e.message}")
        }
    }

    /**
     * Returns an [EncryptedSharedPreferences] instance, or null if the Android Keystore is
     * unavailable (e.g. on an emulator without a secure element).  Callers should treat a
     * null result as "no keys configured" and fall back to on-device TTS.
     */
    fun getPrefs(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { e ->
        Log.e(TAG, "Failed to open encrypted prefs — API keys unavailable", e)
        null
    }
}
