package com.example.mymediaplayer.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    private suspend fun measureQuality(): Quality {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return Quality.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return Quality.NONE

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return Quality.NONE
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return Quality.POOR
        }

        // Measure round-trip latency with an asynchronous HEAD request
        val latencyMs = runCatching {
            val start = System.currentTimeMillis()
            val request = Request.Builder()
                .url("https://www.google.com")
                .head()
                .build()

            val diff = suspendCancellableCoroutine<Long> { continuation ->
                val call = okHttpClient.newCall(request)
                continuation.invokeOnCancellation {
                    call.cancel()
                }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // The test Interceptor injects elapsed header if it is set.
                        // We use it specifically for Robolectric deterministic timing
                        val mockLatency = response.header("X-Mock-Latency")?.toLongOrNull()
                        response.close()
                        if (continuation.isActive) {
                            if (mockLatency != null) {
                                continuation.resume(mockLatency)
                            } else {
                                val actualDiff = System.currentTimeMillis() - start
                                continuation.resume(actualDiff)
                            }
                        }
                    }
                })
            }

            diff
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            Log.d(TAG, "Network ping failed: ${e.message}")
            return Quality.POOR
        }

        Log.d(TAG, "Network latency: ${latencyMs}ms")
        return if (latencyMs < GOOD_LATENCY_THRESHOLD_MS) Quality.GOOD else Quality.POOR
    }

    companion object {
        private const val TAG = "NetworkQualityChecker"
        private const val CACHE_TTL_MS = 60_000L
        private const val PING_TIMEOUT_MS = 2_000
        private const val GOOD_LATENCY_THRESHOLD_MS = 200L

        // For testing purposes
        internal var testInterceptor: okhttp3.Interceptor? = null

        private val okHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(PING_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(PING_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .addInterceptor { chain -> testInterceptor?.intercept(chain) ?: chain.proceed(chain.request()) }
                .build()
        }
    }
}
