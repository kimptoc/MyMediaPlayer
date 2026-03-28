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
 * [KEY_KILO] and [KEY_CLOUD_TTS] constants.
 */
object ApiKeyStore {

    const val KEY_KILO = "kilo_api_key"
    const val KEY_CLOUD_TTS = "cloud_tts_api_key"

    private const val TAG = "ApiKeyStore"
    private const val ENCRYPTED_PREFS_NAME = "mymediaplayer_api_keys"
    private const val KILO_ENDPOINT = "https://api.kilo.ai/api/gateway"

    sealed class ValidationResult {
        data object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    suspend fun validateKeys(context: Context): Pair<ValidationResult, ValidationResult> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context) ?: return@withContext Pair(
            ValidationResult.Error("Encrypted storage unavailable"),
            ValidationResult.Error("Encrypted storage unavailable")
        )

        val kiloKey = prefs.getString(KEY_KILO, null)?.takeIf { it.isNotBlank() }
        val ttsKey = prefs.getString(KEY_CLOUD_TTS, null)?.takeIf { it.isNotBlank() }

        val kiloResult = if (kiloKey != null) {
            validateKiloKey(kiloKey)
        } else {
            ValidationResult.Success
        }

        val ttsResult = if (ttsKey != null) {
            validateTtsKey(ttsKey)
        } else {
            ValidationResult.Success
        }

        Pair(kiloResult, ttsResult)
    }

    private suspend fun validateKiloKey(apiKey: String): ValidationResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("$KILO_ENDPOINT/chat/completions")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("content-type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("model", "anthropic/claude-sonnet-4-6")
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
                return@withContext ValidationResult.Error("HTTP ${conn.responseCode}: API request failed")
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
                return@withContext ValidationResult.Error("HTTP ${conn.responseCode}: API request failed")
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
    }.getOrElse { _ ->
        Log.e(TAG, "Failed to open encrypted prefs — API keys unavailable")
        null
    }
}
