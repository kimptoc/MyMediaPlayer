package com.example.mymediaplayer.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal class NetworkQualityChecker(private val context: Context) {

    enum class Quality { GOOD, POOR, NONE }

    @Volatile private var cachedQuality: Quality = Quality.NONE
    @Volatile private var cacheTimestampMs: Long = 0L

    suspend fun check(): Quality {
        val now = System.currentTimeMillis()
        if (now - cacheTimestampMs < CACHE_TTL_MS) return cachedQuality
        val result = measureQuality()
        cachedQuality = result
        cacheTimestampMs = now
        return result
    }

    fun invalidate() {
        cacheTimestampMs = 0L
    }

    private suspend fun measureQuality(): Quality = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@withContext Quality.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return@withContext Quality.NONE

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return@withContext Quality.NONE
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return@withContext Quality.POOR
        }

        // Measure round-trip latency with a lightweight HEAD request
        val latencyMs = runCatching {
            val start = System.currentTimeMillis()
            val conn = URL("https://www.google.com").openConnection() as HttpURLConnection
            conn.connectTimeout = PING_TIMEOUT_MS
            conn.readTimeout = PING_TIMEOUT_MS
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = false
            conn.connect()
            val elapsed = System.currentTimeMillis() - start
            conn.disconnect()
            elapsed
        }.getOrElse { e ->
            Log.d(TAG, "Network ping failed: ${e.message}")
            return@withContext Quality.POOR
        }

        Log.d(TAG, "Network latency: ${latencyMs}ms")
        if (latencyMs < GOOD_LATENCY_THRESHOLD_MS) Quality.GOOD else Quality.POOR
    }

    companion object {
        private const val TAG = "NetworkQualityChecker"
        private const val CACHE_TTL_MS = 60_000L
        private const val PING_TIMEOUT_MS = 2_000
        private const val GOOD_LATENCY_THRESHOLD_MS = 200L
    }
}
