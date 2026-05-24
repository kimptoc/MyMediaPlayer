package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility singleton for managing EncryptedSharedPreferences instances.
 */
object EncryptedPrefsManager {
    private val prefsInstances = ConcurrentHashMap<String, SharedPreferences>()

    /**
     * Creates or retrieves an EncryptedSharedPreferences instance for the given file name.
     */
    fun createOrGet(context: Context, fileName: String): SharedPreferences {
        // Look up first before doing any Keystore initialization to avoid failure
        // in tests where we are mimicking failure by overriding context.
        return prefsInstances.computeIfAbsent(fileName) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun clearCacheForTesting() {
        prefsInstances.clear()
    }
}
