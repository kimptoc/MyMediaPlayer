package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility singleton for managing EncryptedSharedPreferences instances.
 */
object EncryptedPrefsManager {
    private const val TAG = "EncryptedPrefsManager"
    private val prefsInstances = ConcurrentHashMap<String, SharedPreferences?>()

    /**
     * Creates or retrieves an EncryptedSharedPreferences instance for the given file name.
     * Returns null if the Android Keystore is unavailable (e.g., on some Samsung devices
     * after OS updates or keystore corruption).
     */
    fun createOrGet(context: Context, fileName: String): SharedPreferences? {
        val cached = prefsInstances[fileName]
        if (cached != null) return cached
        // Check if we already tried and failed (null value cached)
        if (prefsInstances.containsKey(fileName)) return null
        return synchronized(prefsInstances) {
            val existing = prefsInstances[fileName]
            if (existing != null) return@synchronized existing
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { prefsInstances[fileName] = it }
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to create EncryptedSharedPreferences for $fileName", e)
                prefsInstances[fileName] = null
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create EncryptedSharedPreferences for $fileName", e)
                prefsInstances[fileName] = null
                null
            }
        }
    }

    fun clearCacheForTesting() {
        prefsInstances.clear()
    }
}
