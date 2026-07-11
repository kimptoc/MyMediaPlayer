package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
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


    internal var testInterceptor: okhttp3.Interceptor? = null

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .apply { testInterceptor?.let { addInterceptor(it) } }
            .build()
    }

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
            val url = URL("${BuildConfig.KILO_ENDPOINT}/chat/completions")
            if (!"https".equals(url.protocol, ignoreCase = true)) {
                return@withContext ValidationResult.Error("Endpoint must use HTTPS")
            }

            val bodyStr = JSONObject().apply {
                put("model", "kilo/auto")
                put("max_tokens", 10)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hi")
                    }
                ))
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(bodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            suspendCancellableCoroutine<ValidationResult> { continuation ->
                val call = okHttpClient.newCall(request)
                continuation.invokeOnCancellation {
                    call.cancel()
                }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        if (continuation.isActive) {
                            continuation.resume(ValidationResult.Error("Connection failed: ${e.message}"))
                        }
                    }

override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
    response.use { response ->
        response.body!!.string() // Consume the body even if we don't use it
        if (continuation.isActive) {
            if (response.code != 200) {
                continuation.resume(ValidationResult.Error("HTTP ${response.code}: API request failed"))
            } else {
                continuation.resume(ValidationResult.Success)
            }
        }
    }
}
                        response.use {
                            if (continuation.isActive) {
                                if (it.code != 200) {
                                    continuation.resume(ValidationResult.Error("HTTP ${it.code}: API request failed"))
                                } else {
                                    continuation.resume(ValidationResult.Success)
                                }
                            }
                        }
                    }
                })
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            ValidationResult.Error("Connection failed: ${e.message}")
        }
    }

    private suspend fun validateTtsKey(apiKey: String): ValidationResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://texttospeech.googleapis.com/v1/text:synthesize")
            if (!"https".equals(url.protocol, ignoreCase = true)) {
                return@withContext ValidationResult.Error("Endpoint must use HTTPS")
            }

            val bodyStr = JSONObject().apply {
                put("input", JSONObject().put("text", "test"))
                put("voice", JSONObject().apply {
                    put("languageCode", "en-US")
                    put("name", "en-US-Neural2-F")
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                })
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("X-Goog-Api-Key", apiKey)
                .post(bodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            suspendCancellableCoroutine<ValidationResult> { continuation ->
                val call = okHttpClient.newCall(request)
                continuation.invokeOnCancellation {
                    call.cancel()
                }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        if (continuation.isActive) {
                            continuation.resume(ValidationResult.Error("Connection failed: ${e.message}"))
                        }
                    }

override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
    response.use { response ->
        response.body!!.string() // Consume the body to enable connection reuse
        if (continuation.isActive) {
            if (response.code != 200) {
                continuation.resume(ValidationResult.Error("HTTP ${response.code}: API request failed"))
            } else {
                continuation.resume(ValidationResult.Success)
            }
        }
    }
}
                        response.use {
                            if (continuation.isActive) {
                                if (it.code != 200) {
                                    continuation.resume(ValidationResult.Error("HTTP ${it.code}: API request failed"))
                                } else {
                                    continuation.resume(ValidationResult.Success)
                                }
                            }
                        }
                    }
                })
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            ValidationResult.Error("Connection failed: ${e.message}")
        }
    }

    /**
     * Returns an [EncryptedSharedPreferences] instance, or null if the Android Keystore is
     * unavailable (e.g. on an emulator without a secure element).  Callers should treat a
     * null result as "no keys configured" and fall back to on-device TTS.
     */
    fun getPrefs(context: Context): SharedPreferences? = runCatching {
        EncryptedPrefsManager.createOrGet(
            context,
            ENCRYPTED_PREFS_NAME
        )
    }.getOrElse { _ ->
        Log.e(TAG, "Failed to open encrypted prefs — API keys unavailable")
        null
    }
}
