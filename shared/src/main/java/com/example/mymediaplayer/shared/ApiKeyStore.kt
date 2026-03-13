package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
